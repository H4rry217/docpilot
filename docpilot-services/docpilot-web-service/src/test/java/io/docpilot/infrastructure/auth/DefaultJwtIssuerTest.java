package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.web.auth.BearerJwtAuthSubjectResolver;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultJwtIssuerTest {

    @Test
    void issuedTokenCanBeResolvedByDefaultBearerResolver() {
        DocPilotJwtConfig config = new DocPilotJwtConfig();
        config.setSecret("test-secret");
        config.setIssuer("docpilot-test");

        DefaultUserAccount account = new DefaultUserAccount();
        account.setUserId(1L);
        account.setDisplayName("Alice");

        String token = new DefaultJwtIssuer(config).issue(account);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        AuthSubject subject = new BearerJwtAuthSubjectResolver(config).resolve(request).orElseThrow();

        assertThat(subject.getUserId()).isEqualTo(1L);
        assertThat(subject.getDisplayName()).isEqualTo("Alice");
    }

    @Test
    void defaultSecretIsGeneratedAndStableForConfigInstance() {
        DocPilotJwtConfig config = new DocPilotJwtConfig();

        String secret = config.getSecret();

        assertThat(secret).isNotBlank();
        assertThat(config.getSecret()).isEqualTo(secret);
        assertThat(secret).isNotEqualTo("docpilot-dev-secret");
    }

}
