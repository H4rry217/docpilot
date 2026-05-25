package io.docpilot.config;

import io.docpilot.filesystem.provider.S3FilesystemProviderConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "docpilot.filesystem")
@Getter
@Setter
public class FilesystemProperties {

    private String defaultProviderId;
    private Local local = new Local();
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class Local {

        private String providerId = "local";
        private String root;

    }

    @Getter
    @Setter
    public static class S3 {

        private String providerId = "s3";
        private String endpoint;
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
        private Duration presignedUrlTtl = Duration.ofMinutes(10);

        public S3FilesystemProviderConfig toProviderConfig() {
            S3FilesystemProviderConfig config = new S3FilesystemProviderConfig();
            config.setProviderId(providerId);
            config.setEndpoint(endpoint);
            config.setRegion(region);
            config.setBucket(bucket);
            config.setAccessKey(accessKey);
            config.setSecretKey(secretKey);
            config.setPathStyleAccess(pathStyleAccess);
            config.setPresignedUrlTtl(presignedUrlTtl);
            return config;
        }

    }

}
