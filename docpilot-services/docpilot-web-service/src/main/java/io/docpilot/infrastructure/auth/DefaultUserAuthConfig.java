package io.docpilot.infrastructure.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.auth.default-user")
public class DefaultUserAuthConfig {

    /**
     * Enables DocPilot's built-in, replaceable user auth implementation.
     */
    private boolean enabled = true;

    /**
     * Runs the bundled schema file on startup.
     */
    private boolean initSchema = true;

    /**
     * Allows public account registration through the built-in auth implementation.
     */
    private boolean allowRegistration = true;

    /**
     * Optional server-side password pepper. Keep production values outside the database.
     */
    private String passwordPepper = "";

    /**
     * PBKDF2 work factor for new password hashes.
     */
    private int passwordIterations = 600000;

    public void setPasswordPepper(String passwordPepper) {
        this.passwordPepper = passwordPepper == null ? "" : passwordPepper;
    }

}
