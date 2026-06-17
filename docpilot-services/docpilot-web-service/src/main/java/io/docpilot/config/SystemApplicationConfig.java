package io.docpilot.config;

import io.docpilot.infrastructure.system.DatabaseSystemSettingRepository;
import io.docpilot.infrastructure.system.SystemSettingStore;
import io.docpilot.system.repository.SystemSettingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemApplicationConfig {

    @Bean
    @ConditionalOnMissingBean(SystemSettingRepository.class)
    public SystemSettingRepository databaseSystemSettingRepository(SystemSettingStore store) {
        return new DatabaseSystemSettingRepository(store);
    }

}
