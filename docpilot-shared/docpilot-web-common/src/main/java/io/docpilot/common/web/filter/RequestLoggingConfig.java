package io.docpilot.common.web.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.web.logging.http")
public class RequestLoggingConfig {

    /**
     * Enables HTTP request/response logging.
     */
    private boolean enabled = true;

    /**
     * Logs query parameters and request body by default.
     */
    private boolean requestPayloadEnabled = true;

    /**
     * Logs response body by default.
     */
    private boolean responsePayloadEnabled = true;

    /**
     * Ant-style paths that bypass HTTP request/response logging entirely.
     */
    private Set<String> excludedPaths = new LinkedHashSet<>();

    /**
     * Maximum payload characters printed to logs. Larger payloads are truncated.
     */
    private int maxPayloadLength = 4096;

    /**
     * Ant-style paths whose request/response payloads are not printed.
     */
    private Set<String> payloadExcludedPaths = new LinkedHashSet<>();

}
