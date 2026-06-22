package io.docpilot.config;

import io.docpilot.infrastructure.knowledge.RedissonKnowledgeIndexJobLock;
import io.docpilot.workspace.knowledge.queue.KnowledgeIndexJobLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "docpilot.knowledge.index", name = "mode", havingValue = "redis", matchIfMissing = true)
public class KnowledgeIndexRedisConfig {

    @Bean
    @ConditionalOnMissingBean(KnowledgeIndexJobLock.class)
    public KnowledgeIndexJobLock knowledgeIndexJobLock(RedissonClient redissonClient) {
        return new RedissonKnowledgeIndexJobLock(redissonClient);
    }

}
