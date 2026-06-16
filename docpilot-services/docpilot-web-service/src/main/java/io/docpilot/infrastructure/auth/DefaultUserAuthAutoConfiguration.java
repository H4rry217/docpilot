package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import io.docpilot.common.web.auth.AuthSubjectResolver;
import io.docpilot.infrastructure.user.DatabaseUserSettingRepository;
import io.docpilot.infrastructure.user.UserSettingStore;
import io.docpilot.infrastructure.auth.provider.AuthProvider;
import io.docpilot.infrastructure.auth.provider.AuthProviderProperties;
import io.docpilot.infrastructure.auth.provider.AuthProviderRegistry;
import io.docpilot.infrastructure.auth.provider.LocalPasswordAuthProvider;
import io.docpilot.infrastructure.auth.provider.ProviderAuthSubjectResolver;
import io.docpilot.user.repository.UserSettingRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

@Configuration
@EnableConfigurationProperties({DefaultUserAuthConfig.class, AuthProviderProperties.class})
public class DefaultUserAuthAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "docpilot.auth.default-user", name = "init-schema", havingValue = "true", matchIfMissing = true)
    public InitializingBean defaultUserAuthSchemaInitializer(DataSource dataSource,
                                                            ResourceLoader resourceLoader) {
        return () -> {
            Resource resource = resourceLoader.getResource("classpath:schema-mysql.sql");
            new ResourceDatabasePopulator(resource).execute(dataSource);
        };
    }

    @Bean
    @ConditionalOnMissingBean(DefaultUserAccountRepository.class)
    public JpaDefaultUserAccountRepository defaultUserAccountRepository(DefaultUserAccountJpaStore accountStore) {
        return new JpaDefaultUserAccountRepository(accountStore);
    }

    @Bean
    @ConditionalOnMissingBean(UserSettingRepository.class)
    public DatabaseUserSettingRepository userSettingRepository(UserSettingStore settingStore) {
        return new DatabaseUserSettingRepository(settingStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultUserAuthSettingsStore defaultUserAuthSettingsStore(DataSource dataSource,
                                                                     DefaultUserAuthConfig config) {
        return new DefaultUserAuthSettingsStore(dataSource, config.isInitSchema());
    }

    @Bean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = LocalPasswordAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public InitializingBean defaultUserJwtSecretInitializer(DocPilotJwtConfig jwtConfig,
                                                           DefaultUserAuthSettingsStore settingsStore) {
        return () -> jwtConfig.setSecret(settingsStore.jwtSecret(jwtConfig.getConfiguredSecret()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = LocalPasswordAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public PasswordHasher passwordHasher(DefaultUserAuthConfig config,
                                         DefaultUserAuthSettingsStore settingsStore) {
        return new PasswordHasher(settingsStore.passwordPepper(config.getPasswordPepper()), config.getPasswordIterations());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = LocalPasswordAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultJwtIssuer defaultJwtIssuer(DocPilotJwtConfig jwtConfig) {
        return new DefaultJwtIssuer(jwtConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = LocalPasswordAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultUserAuthService defaultUserAuthService(DefaultUserAccountRepository accountRepository,
                                                         PasswordHasher passwordHasher,
                                                         DefaultJwtIssuer jwtIssuer,
                                                         DefaultUserAuthConfig config,
                                                         AuthContextProvider authContextProvider) {
        return new DefaultUserAuthService(accountRepository, passwordHasher, jwtIssuer, config.isAllowRegistration(), authContextProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthIdentityService authIdentityService(DefaultUserAccountRepository accountRepository,
                                                   AuthUserIdentityJpaStore identityStore) {
        return new AuthIdentityService(accountRepository, identityStore);
    }

    @Bean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = LocalPasswordAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public LocalPasswordAuthProvider localPasswordAuthProvider(DefaultUserAuthService authService,
                                                              DefaultUserAuthConfig config,
                                                              DocPilotJwtConfig jwtConfig) {
        return new LocalPasswordAuthProvider(authService, config, jwtConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthProviderRegistry authProviderRegistry(AuthProviderProperties properties,
                                                     List<AuthProvider> providers) {
        return new AuthProviderRegistry(properties, providers);
    }

    @Bean
    @Primary
    public AuthSubjectResolver providerAuthSubjectResolver(AuthProviderRegistry providerRegistry,
                                                           AuthIdentityService identityService) {
        return new ProviderAuthSubjectResolver(providerRegistry, identityService);
    }

}
