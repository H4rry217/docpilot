package io.docpilot.infrastructure.auth.provider;

import io.docpilot.auth.AuthProvider;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.infrastructure.auth.AuthIdentityService;
import io.docpilot.infrastructure.auth.AuthUserIdentityJpaStore;
import io.docpilot.infrastructure.auth.DefaultUserAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Common web authentication bridge for every configured AuthProvider.
 */
@Configuration
@EnableConfigurationProperties(AuthProviderProperties.class)
public class AuthProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthProviderRegistry authProviderRegistry(AuthProviderProperties properties,
                                                     List<AuthProvider> providers) {
        return new AuthProviderRegistry(properties, providers);
    }

    @Bean
    @Primary
    public AuthSubjectResolver providerAuthSubjectResolver(AuthProviderRegistry providerRegistry,
                                                           AuthIdentityService identityService,
                                                           ServletAuthRequestAdapter requestAdapter) {
        return new ProviderAuthSubjectResolver(providerRegistry, identityService, requestAdapter);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthIdentityService authIdentityService(DefaultUserAccountRepository accountRepository,
                                                   AuthUserIdentityJpaStore identityStore) {
        return new AuthIdentityService(accountRepository, identityStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ServletAuthRequestAdapter servletAuthRequestAdapter() {
        return new ServletAuthRequestAdapter();
    }

}
