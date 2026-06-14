package io.docpilot.workspace.config;

import io.docpilot.common.id.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkspaceIdConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(@Value("${docpilot.id.datacenter-id:1}") long datacenterId,
                                                     @Value("${docpilot.id.machine-id:1}") long machineId) {
        return new SnowflakeIdGenerator(datacenterId, machineId);
    }

}
