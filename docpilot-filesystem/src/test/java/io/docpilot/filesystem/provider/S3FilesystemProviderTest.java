package io.docpilot.filesystem.provider;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3FilesystemProviderTest {

    @Test
    void writesAndReadsUsingBucketAndProviderPathAsObjectKey() {
        S3Client s3Client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hello".getBytes()));
        S3FilesystemProvider provider = new S3FilesystemProvider(config(), s3Client, presigner);

        provider.write("workspaces/ws1/project/a.md", "hello".getBytes());
        byte[] bytes = provider.read("workspaces/ws1/project/a.md");

        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));
        assertThat(putCaptor.getValue().key()).isEqualTo("workspaces/ws1/project/a.md");

        ArgumentCaptor<GetObjectRequest> getCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(getCaptor.capture());
        assertThat(getCaptor.getValue().key()).isEqualTo("workspaces/ws1/project/a.md");
        assertThat(new String(bytes)).isEqualTo("hello");
    }

    @Test
    void writesAndReadsRealS3ObjectWhenConfigured() {
        Optional<S3FilesystemProviderConfig> config = realS3Config();
        Assumptions.assumeTrue(config.isPresent(), "Set docpilot.test.s3.* system properties or DOCPILOT_TEST_S3_* env vars to run real S3 test");
        S3FilesystemProvider provider = new S3FilesystemProvider(config.get());
        String root = "docpilot-tests/s3-provider/" + UUID.randomUUID().toString().replace("-", "");
        String key = root + "/hello.txt";
        byte[] content = "hello real s3".getBytes(StandardCharsets.UTF_8);

        try {
            provider.write(key, content);

            assertThat(provider.exists(key)).isTrue();
            assertThat(provider.read(key)).isEqualTo(content);
            assertThat(provider.stat(key).size()).isEqualTo(content.length);
            assertThat(provider.list(root))
                    .extracting("path")
                    .contains(key);
        } finally {
            provider.delete(root);
        }
    }

    private S3FilesystemProviderConfig config() {
        S3FilesystemProviderConfig config = new S3FilesystemProviderConfig();
        config.setProviderId("s3");
        config.setEndpoint("http://localhost:9000");
        config.setBucket("docpilot-test");
        config.setAccessKey("access");
        config.setSecretKey("secret");
        config.setPathStyleAccess(true);
        config.setPresignedUrlTtl(Duration.ofMinutes(10));
        return config;
    }

    private Optional<S3FilesystemProviderConfig> realS3Config() {
        S3FilesystemProviderConfig config = new S3FilesystemProviderConfig();
        config.setProviderId(value("provider-id", "DOCPILOT_TEST_S3_PROVIDER_ID", "s3"));
        config.setEndpoint(value("endpoint", "DOCPILOT_TEST_S3_ENDPOINT", null));
        config.setRegion(value("region", "DOCPILOT_TEST_S3_REGION", null));
        config.setBucket(value("bucket", "DOCPILOT_TEST_S3_BUCKET", null));
        config.setAccessKey(value("access-key", "DOCPILOT_TEST_S3_ACCESS_KEY", null));
        config.setSecretKey(value("secret-key", "DOCPILOT_TEST_S3_SECRET_KEY", null));
        config.setPathStyleAccess(Boolean.parseBoolean(value("path-style-access", "DOCPILOT_TEST_S3_PATH_STYLE_ACCESS", "true")));
        config.setPresignedUrlTtl(Duration.ofMinutes(10));
        return config.isComplete() ? Optional.of(config) : Optional.empty();
    }

    private String value(String propertySuffix, String envName, String defaultValue) {
        String systemProperty = System.getProperty("docpilot.test.s3." + propertySuffix);
        if (StringUtils.isNotBlank(systemProperty)) {
            return systemProperty;
        }
        String envValue = System.getenv(envName);
        if (StringUtils.isNotBlank(envValue)) {
            return envValue;
        }
        return defaultValue;
    }

}
