package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.web.auth.DocPilotJwtConfig;
import org.springframework.beans.factory.InitializingBean;
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
@ConditionalOnProperty(prefix = "docpilot.auth.default-user", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    public DefaultUserAuthSettingsStore defaultUserAuthSettingsStore(DataSource dataSource,
                                                                     DefaultUserAuthConfig config) {
        return new DefaultUserAuthSettingsStore(dataSource, config.isInitSchema());
    }

    @Bean
    public InitializingBean defaultUserJwtSecretInitializer(DocPilotJwtConfig jwtConfig,
                                                           DefaultUserAuthSettingsStore settingsStore) {
        return () -> jwtConfig.setSecret(settingsStore.jwtSecret(jwtConfig.getConfiguredSecret()));
    }

    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher(DefaultUserAuthConfig config,
                                         DefaultUserAuthSettingsStore settingsStore) {
        return new PasswordHasher(settingsStore.passwordPepper(config.getPasswordPepper()), config.getPasswordIterations());
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultJwtIssuer defaultJwtIssuer(DocPilotJwtConfig jwtConfig) {
        return new DefaultJwtIssuer(jwtConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultUserAuthService defaultUserAuthService(DefaultUserAccountRepository accountRepository,
                                                         PasswordHasher passwordHasher,
                                                         DefaultJwtIssuer jwtIssuer,
                                                         DefaultUserAuthConfig config,
                                                         AuthContextProvider authContextProvider) {
        return new DefaultUserAuthService(accountRepository, passwordHasher, jwtIssuer, config.isAllowRegistration(), authContextProvider);
    }

}
