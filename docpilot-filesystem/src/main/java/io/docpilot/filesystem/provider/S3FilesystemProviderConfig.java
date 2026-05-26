package io.docpilot.filesystem.provider;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

@Getter
@Setter
public class S3FilesystemProviderConfig {

    private String providerId = "s3";
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess = true;
    private Duration presignedUrlTtl = Duration.ofMinutes(10);

    public boolean isComplete() {
        return hasText(endpoint) && hasText(bucket) && hasText(accessKey) && hasText(secretKey);
    }

    private boolean hasText(String value) {
        return StringUtils.isNotBlank(value);
    }

}
