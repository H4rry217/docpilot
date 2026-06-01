package io.docpilot.config;

import io.docpilot.filesystem.provider.DefaultProviderRegistry;
import io.docpilot.filesystem.provider.LocalFilesystemProvider;
import io.docpilot.filesystem.provider.ProviderRegistry;
import io.docpilot.filesystem.provider.S3FilesystemProvider;
import io.docpilot.filesystem.provider.S3FilesystemProviderConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Path;

@Configuration
@EnableConfigurationProperties(FilesystemConfig.class)
public class FilesystemApplicationConfig {

    @Bean
    public LocalFilesystemProvider localFilesystemProvider(FilesystemConfig config) {
        String root = config.getLocal().getRoot();
        Path localRoot = StringUtils.isBlank(root)
                ? Path.of(System.getProperty("java.io.tmpdir"), "docpilot-filesystem")
                : Path.of(root);
        return new LocalFilesystemProvider(config.getLocal().getProviderId(), localRoot);
    }

    @Bean
    public ProviderRegistry providerRegistry(LocalFilesystemProvider localFilesystemProvider,
                                             FilesystemConfig config) {
        DefaultProviderRegistry registry = new DefaultProviderRegistry();
        registry.register(localFilesystemProvider);
        S3FilesystemProviderConfig s3Config = config.getS3().toProviderConfig();
        if (s3Config.isComplete()) {
            registry.register(new S3FilesystemProvider(s3Config));
        }
        return registry;
    }

}
