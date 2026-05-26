package io.docpilot.common.web.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "docpilot.auth.jwt")
public class DocPilotJwtProperties {

    /**
     * HMAC secret for the default HS256 bearer-token resolver.
     */
    private String secret = "docpilot-dev-secret";

    /**
     * Optional issuer claim to enforce.
     */
    private String issuer;

    /**
     * Allowed clock skew in seconds for exp/nbf checks.
     */
    private long clockSkewSeconds = 60;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

}
