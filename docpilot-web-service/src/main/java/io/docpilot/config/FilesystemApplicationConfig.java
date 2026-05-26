package io.docpilot.config;

import io.docpilot.document.model.Workspace;
import io.docpilot.filesystem.FilesystemDefaultPaths;
import io.docpilot.filesystem.FilesystemService;
import io.docpilot.filesystem.PathMappingService;
import io.docpilot.filesystem.model.PathMapping;
import io.docpilot.filesystem.provider.DefaultProviderRegistry;
import io.docpilot.filesystem.provider.FilesystemProvider;
import io.docpilot.filesystem.provider.LocalFilesystemProvider;
import io.docpilot.filesystem.provider.ProviderRegistry;
import io.docpilot.filesystem.provider.S3FilesystemProvider;
import io.docpilot.filesystem.provider.S3FilesystemProviderConfig;
import io.docpilot.filesystem.store.InMemoryPathMappingStore;
import io.docpilot.filesystem.store.PathMappingStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;
import java.util.function.Consumer;

@Configuration
@EnableConfigurationProperties(FilesystemProperties.class)
public class FilesystemApplicationConfig {

    @Bean
    public PathMappingStore pathMappingStore() {
        return new InMemoryPathMappingStore();
    }

    @Bean
    public PathMappingService pathMappingService(PathMappingStore pathMappingStore) {
        return new PathMappingService(pathMappingStore);
    }

    @Bean
    public LocalFilesystemProvider localFilesystemProvider(FilesystemProperties properties) {
        String root = properties.getLocal().getRoot();
        Path localRoot = StringUtils.isBlank(root)
                ? Path.of(System.getProperty("java.io.tmpdir"), "docpilot-filesystem")
                : Path.of(root);
        return new LocalFilesystemProvider(properties.getLocal().getProviderId(), localRoot);
    }

    @Bean
    public ProviderRegistry providerRegistry(LocalFilesystemProvider localFilesystemProvider,
                                             FilesystemProperties properties) {
        DefaultProviderRegistry registry = new DefaultProviderRegistry();
        registry.register(localFilesystemProvider);
        S3FilesystemProviderConfig s3Config = properties.getS3().toProviderConfig();
        if (s3Config.isComplete()) {
            registry.register(new S3FilesystemProvider(s3Config));
        }
        return registry;
    }

    @Bean
    public FilesystemService filesystemService(PathMappingService pathMappingService,
                                               ProviderRegistry providerRegistry) {
        return new FilesystemService(pathMappingService, providerRegistry);
    }

    @Bean
    public Consumer<Workspace> workspacePathMappingInitializer(PathMappingService pathMappingService,
                                                               ProviderRegistry providerRegistry,
                                                               FilesystemProperties properties) {
        return workspace -> {
            String providerId = defaultProviderId(providerRegistry, properties);
            PathMapping mapping = new PathMapping();
            mapping.setWorkspaceId(workspace.getWorkspaceId());
            mapping.setVirtualPath(FilesystemDefaultPaths.PROJECT_VIRTUAL_PATH);
            mapping.setProviderId(providerId);
            mapping.setProviderRoot(FilesystemDefaultPaths.workspaceProjectProviderRoot(workspace.getWorkspaceId()));
            mapping.setReadonly(false);
            mapping.setEnabled(true);
            pathMappingService.create(mapping);
        };
    }

    private String defaultProviderId(ProviderRegistry providerRegistry, FilesystemProperties properties) {
        String configured = properties.getDefaultProviderId();
        if (StringUtils.isNotBlank(configured) && providerRegistry.findById(configured).isPresent()) {
            return configured;
        }
        String s3ProviderId = properties.getS3().getProviderId();
        if (providerRegistry.findById(s3ProviderId).isPresent()) {
            return s3ProviderId;
        }
        return properties.getLocal().getProviderId();
    }

}
