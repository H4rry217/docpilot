package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.provider.S3FilesystemProvider;
import io.docpilot.filesystem.provider.S3FilesystemProviderConfig;
import io.docpilot.workspace.filesystem.WorkspaceFilesystem;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class S3WorkspaceCompositeFilesystemLiveTest {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceNodeRepository nodeRepository;

    @Autowired
    private WorkspaceDocumentRepository documentRepository;

    @Test
    void composesRealS3AndConfiguredWorkspaceWhenConfigured() {
        Optional<LiveConfig> liveConfig = liveConfig();
        Assumptions.assumeTrue(liveConfig.isPresent(), liveConfigSkipReason());

        LiveConfig config = liveConfig.get();
        S3FilesystemProvider s3Provider = new S3FilesystemProvider(config.s3Config());
        String s3Root = config.s3Root() + "/" + UUID.randomUUID().toString().replace("-", "");
        String s3ObjectPath = s3Root + "/hello.txt";
        String s3Content = "hello from real s3 and workspace";

        try {
            // Seed S3 directly; the composite mount below is intentionally read-only.
            s3Provider.write(s3ObjectPath, s3Content.getBytes(StandardCharsets.UTF_8));

            Filesystem workspaceFilesystem = workspaceRoot(config.workspaceId());
            Filesystem s3Filesystem = new ProviderFilesystem(s3Provider);

            CompositeFilesystem filesystem = new CompositeFilesystem()
                    .mount("/project", workspaceFilesystem)
                    .mount("/project/s3", s3Filesystem, "/" + s3Root, MountOptions.readOnly());

            assertThat(filesystem.list("/project"))
                    .extracting(FileEntry::path)
                    .contains("/project/workspace", "/project/s3");

            assertThat(filesystem.readText("/project/s3/hello.txt")).isEqualTo(s3Content);
            assertThat(filesystem.glob("/project/s3/*.txt"))
                    .extracting(FileEntry::path)
                    .containsExactly("/project/s3/hello.txt");
            assertThat(filesystem.grep("/project/s3", "real s3"))
                    .extracting("path")
                    .containsExactly("/project/s3/hello.txt");
            assertThatThrownBy(() -> filesystem.writeText("/project/s3/blocked.txt", "no"))
                    .isInstanceOf(UnsupportedFilesystemOperationException.class);

            String workspaceRoot = "/project/workspace/" + config.workspaceId();
            assertThat(filesystem.stat(workspaceRoot).directory()).isTrue();
            assertThat(filesystem.list("/project/workspace"))
                    .extracting(FileEntry::path)
                    .contains(workspaceRoot);

            String documentPath = configuredOrFirstDocumentPath(filesystem, workspaceRoot, config.documentPath());
            String markdown = filesystem.readText(documentPath);
            assertThat(markdown).isNotNull();

            config.grepText().ifPresent(text -> assertThat(filesystem.grep(workspaceRoot, text))
                    .extracting("path")
                    .contains(documentPath));
        } finally {
            s3Provider.delete(s3Root);
        }
    }

    private Filesystem workspaceRoot(Long workspaceId) {
        // WorkspaceFilesystem is the tree for one concrete workspace id.
        Filesystem workspace = new WorkspaceFilesystem(
                workspaceId,
                workspaceRepository,
                nodeRepository,
                documentRepository
        );

        // The /workspace/{workspaceId} namespace is a mount concern, not a workspace concern.
        return new CompositeFilesystem()
                .mount("/workspace/" + workspaceId, workspace);
    }

    private String configuredOrFirstDocumentPath(Filesystem filesystem, String workspaceRoot, Optional<String> configuredPath) {
        if (configuredPath.isPresent()) {
            return normalizeConfiguredDocumentPath(workspaceRoot, configuredPath.get());
        }

        Optional<FileEntry> firstDocument = firstDocument(filesystem, workspaceRoot, 0);
        Assumptions.assumeTrue(firstDocument.isPresent(), "Configured workspace must contain at least one document node");

        return firstDocument.get().path();
    }

    private String normalizeConfiguredDocumentPath(String workspaceRoot, String configuredPath) {
        if (configuredPath.startsWith("/project/")) {
            return configuredPath;
        }

        if (configuredPath.startsWith("/workspace/")) {
            return "/project" + configuredPath;
        }

        if (configuredPath.startsWith("/")) {
            return workspaceRoot + configuredPath;
        }

        return workspaceRoot + "/" + configuredPath;
    }

    private Optional<FileEntry> firstDocument(Filesystem filesystem, String path, int depth) {
        if (depth > 8) {
            return Optional.empty();
        }

        List<FileEntry> entries = filesystem.list(path);
        for (FileEntry entry : entries) {
            if (entry.type() == FileEntryType.FILE) {
                return Optional.of(entry);
            }

            Optional<FileEntry> child = firstDocument(filesystem, entry.path(), depth + 1);
            if (child.isPresent()) {
                return child;
            }
        }

        return Optional.empty();
    }

    private Optional<LiveConfig> liveConfig() {
        Optional<S3FilesystemProviderConfig> s3Config = realS3Config();
        String workspaceId = value("workspace.id", "DOCPILOT_TEST_WORKSPACE_ID", null);

        if (s3Config.isEmpty() || StringUtils.isBlank(workspaceId)) {
            return Optional.empty();
        }

        String s3Root = value("s3.root", "DOCPILOT_TEST_S3_ROOT", "docpilot-tests/s3-workspace-composite");
        String documentPath = value("workspace.document-path", "DOCPILOT_TEST_WORKSPACE_DOCUMENT_PATH", null);
        String grepText = value("workspace.grep-text", "DOCPILOT_TEST_WORKSPACE_GREP_TEXT", null);

        return Optional.of(new LiveConfig(
                s3Config.get(),
                s3Root,
                Long.parseLong(workspaceId),
                Optional.ofNullable(StringUtils.trimToNull(documentPath)),
                Optional.ofNullable(StringUtils.trimToNull(grepText))
        ));
    }

    private String liveConfigSkipReason() {
        List<String> missing = new ArrayList<>();

        requireConfig(missing, "docpilot.test.s3.endpoint", "DOCPILOT_TEST_S3_ENDPOINT");
        requireConfig(missing, "docpilot.test.s3.bucket", "DOCPILOT_TEST_S3_BUCKET");
        requireConfig(missing, "docpilot.test.s3.access-key", "DOCPILOT_TEST_S3_ACCESS_KEY");
        requireConfig(missing, "docpilot.test.s3.secret-key", "DOCPILOT_TEST_S3_SECRET_KEY");
        requireConfig(missing, "docpilot.test.workspace.id", "DOCPILOT_TEST_WORKSPACE_ID");

        if (missing.isEmpty()) {
            return "Live config was not accepted; check S3 endpoint, bucket, credentials, and workspace id.";
        }

        return "Missing live test config: " + String.join(", ", missing);
    }

    private void requireConfig(List<String> missing, String propertyName, String envName) {
        String systemProperty = System.getProperty(propertyName);
        String envValue = System.getenv(envName);

        if (StringUtils.isBlank(systemProperty) && StringUtils.isBlank(envValue)) {
            missing.add(propertyName + " or " + envName);
        }
    }

    private Optional<S3FilesystemProviderConfig> realS3Config() {
        S3FilesystemProviderConfig config = new S3FilesystemProviderConfig();
        config.setProviderId(value("s3.provider-id", "DOCPILOT_TEST_S3_PROVIDER_ID", "s3"));
        config.setEndpoint(value("s3.endpoint", "DOCPILOT_TEST_S3_ENDPOINT", null));
        config.setRegion(value("s3.region", "DOCPILOT_TEST_S3_REGION", null));
        config.setBucket(value("s3.bucket", "DOCPILOT_TEST_S3_BUCKET", null));
        config.setAccessKey(value("s3.access-key", "DOCPILOT_TEST_S3_ACCESS_KEY", null));
        config.setSecretKey(value("s3.secret-key", "DOCPILOT_TEST_S3_SECRET_KEY", null));
        config.setPathStyleAccess(Boolean.parseBoolean(value("s3.path-style-access", "DOCPILOT_TEST_S3_PATH_STYLE_ACCESS", "true")));
        config.setPresignedUrlTtl(Duration.ofMinutes(10));

        return config.isComplete() ? Optional.of(config) : Optional.empty();
    }

    private String value(String propertySuffix, String envName, String defaultValue) {
        String systemProperty = System.getProperty("docpilot.test." + propertySuffix);
        if (StringUtils.isNotBlank(systemProperty)) {
            return systemProperty;
        }

        String envValue = System.getenv(envName);
        if (StringUtils.isNotBlank(envValue)) {
            return envValue;
        }

        return defaultValue;
    }

    private record LiveConfig(
            S3FilesystemProviderConfig s3Config,
            String s3Root,
            Long workspaceId,
            Optional<String> documentPath,
            Optional<String> grepText
    ) {
    }

}
