package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import io.docpilot.common.web.auth.JwtTokenVerifier;
import io.docpilot.infrastructure.auth.provider.DefaultUserAuthProvider;
import io.docpilot.system.repository.SystemSettingRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DefaultUserAuthConfig.class)
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
    @ConditionalOnMissingBean
    public DefaultUserAuthSettings defaultUserAuthSettings(
            SystemSettingRepository systemSettingRepository,
            DefaultUserAccountRepository accountRepository,
            @Qualifier("defaultUserAuthSchemaInitializer") ObjectProvider<InitializingBean> schemaInitializer) {
        // Ensure schema-mysql.sql runs before generated default-auth settings are read.
        schemaInitializer.getIfAvailable();
        return new DefaultUserAuthSettings(systemSettingRepository, accountRepository);
    }

    @Bean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = DefaultUserAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public InitializingBean defaultUserJwtSecretInitializer(DocPilotJwtConfig jwtConfig,
                                                           DefaultUserAuthSettings authSettings) {
        return () -> jwtConfig.setSecret(authSettings.jwtSecret(jwtConfig.getConfiguredSecret()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = DefaultUserAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultUserPasswordHasher defaultUserPasswordHasher(DefaultUserAuthConfig config,
                                                               DefaultUserAuthSettings authSettings) {
        return new DefaultUserPasswordHasher(authSettings.passwordPepper(config.getPasswordPepper()), config.getPasswordIterations());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = DefaultUserAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultUserJwtIssuer defaultUserJwtIssuer(DocPilotJwtConfig jwtConfig) {
        return new DefaultUserJwtIssuer(jwtConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = DefaultUserAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultUserAuthService defaultUserAuthService(DefaultUserAccountRepository accountRepository,
                                                         DefaultUserPasswordHasher passwordHasher,
                                                         DefaultUserJwtIssuer jwtIssuer,
                                                         DefaultUserAuthConfig config,
                                                         AuthContextProvider authContextProvider) {
        return new DefaultUserAuthService(accountRepository, passwordHasher, jwtIssuer, config.isAllowRegistration(), authContextProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "docpilot.auth", name = "provider", havingValue = DefaultUserAuthProvider.PROVIDER_ID, matchIfMissing = true)
    public DefaultUserAuthProvider defaultUserAuthProvider(DefaultUserAuthService authService,
                                                          DefaultUserAuthConfig config,
                                                          JwtTokenVerifier jwtTokenVerifier) {
        return new DefaultUserAuthProvider(authService, config, jwtTokenVerifier);
    }

}
