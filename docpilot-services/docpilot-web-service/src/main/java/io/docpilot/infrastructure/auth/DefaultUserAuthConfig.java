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
     * Optional server-side password pepper. Keep production values outside the database.
     */
    private String passwordPepper = "";

    /**
     * PBKDF2 work factor for new password hashes.
     */
    private int passwordIterations = 600000;

    private final Datasource datasource = new Datasource();

    public void setPasswordPepper(String passwordPepper) {
        this.passwordPepper = passwordPepper == null ? "" : passwordPepper;
    }

    @Getter
    @Setter
    public static class Datasource {

        private String url = "jdbc:mysql://127.0.0.1:3306/docpilot?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";

        private String username = "docpilot";

        private String password = "";

    }

}
