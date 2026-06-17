package io.docpilot.infrastructure.auth.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "docpilot.auth")
public class AuthProviderProperties {

    private String provider = DefaultUserAuthProvider.PROVIDER_ID;

}
