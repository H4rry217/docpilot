package io.docpilot.infrastructure.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "docpilot.user-settings.cache")
public class UserSettingCacheProperties {

    /**
     * Whether Redis should be used as a best-effort user settings cache.
     */
    private boolean enabled = true;

    /**
     * TTL for one user's settings snapshot.
     */
    private Duration ttl = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
