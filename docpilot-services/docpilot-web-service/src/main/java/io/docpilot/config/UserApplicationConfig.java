package io.docpilot.config;

import io.docpilot.user.application.UserInformationManager;
import io.docpilot.user.application.UserSettingManager;
import io.docpilot.infrastructure.user.RedisUserSettingCache;
import io.docpilot.infrastructure.user.UserSettingCacheProperties;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.model.UserSettingRecord;
import io.docpilot.user.repository.UserInformationRepository;
import io.docpilot.user.repository.UserSettingCache;
import io.docpilot.user.repository.UserSettingRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;

@Configuration
@EnableConfigurationProperties(UserSettingCacheProperties.class)
public class UserApplicationConfig {

    public static final Long DEV_USER_ID = 1L;
    public static final String DEV_DISPLAY_NAME = "HarryZ";

    @Bean
    @ConditionalOnMissingBean(UserInformationRepository.class)
    @ConditionalOnProperty(prefix = "docpilot.auth.default-user", name = "enabled", havingValue = "false")
    public UserInformationRepository userInformationRepository() {
        InMemoryUserInformationRepository repository = new InMemoryUserInformationRepository();
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(DEV_USER_ID);
        userInformation.setDisplayName(DEV_DISPLAY_NAME);
        userInformation.setEmail("dev-user@docpilot.local");
        repository.save(userInformation);
        return repository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(UserInformationManager.class)
    public UserInformationManager userInformationProvider(UserInformationRepository userInformationRepository) {
        return new UserInformationManager(userInformationRepository);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(UserSettingManager.class)
    public UserSettingManager userSettingManager(UserSettingRepository userSettingRepository,
                                                 ObjectProvider<UserSettingCache> userSettingCache) {
        return new UserSettingManager(userSettingRepository, userSettingCache.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(UserSettingRepository.class)
    @ConditionalOnProperty(prefix = "docpilot.auth.default-user", name = "enabled", havingValue = "false")
    public UserSettingRepository userSettingRepository() {
        return new InMemoryUserSettingRepository();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(UserSettingCache.class)
    @ConditionalOnProperty(prefix = "docpilot.user-settings.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    public UserSettingCache userSettingCache(StringRedisTemplate redisTemplate,
                                             UserSettingCacheProperties properties) {
        return new RedisUserSettingCache(redisTemplate, properties.getTtl());
    }

    private static final class InMemoryUserInformationRepository implements UserInformationRepository {

        private final Map<Long, UserInformation> userInformationById = new ConcurrentHashMap<>();

        @Override
        public UserInformation save(UserInformation userInformation) {
            userInformationById.put(userInformation.getUserId(), userInformation);
            return userInformation;
        }

        @Override
        public Optional<UserInformation> findByUserId(Long userId) {
            return Optional.ofNullable(userInformationById.get(userId));
        }

    }

    private static final class InMemoryUserSettingRepository implements UserSettingRepository {

        private final Map<Long, Map<String, String>> userSettingsByUserId = new ConcurrentHashMap<>();

        @Override
        public List<UserSettingRecord> findByUserIdAndKeys(Long userId, Collection<String> keys) {
            if (userId == null || keys == null || keys.isEmpty()) {
                return List.of();
            }
            Map<String, String> settings = userSettingsByUserId.getOrDefault(userId, Map.of());
            return keys.stream()
                    .filter(settings::containsKey)
                    .map(key -> new UserSettingRecord(userId, key, settings.get(key)))
                    .toList();
        }

        @Override
        public void saveAll(Long userId, Map<String, String> settingValueByKey) {
            if (userId == null || settingValueByKey == null || settingValueByKey.isEmpty()) {
                return;
            }
            userSettingsByUserId.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                    .putAll(new LinkedHashMap<>(settingValueByKey));
        }

        @Override
        public void removeByUserIdAndKeys(Long userId, Collection<String> keys) {
            if (userId == null || keys == null || keys.isEmpty()) {
                return;
            }
            Map<String, String> settings = userSettingsByUserId.get(userId);
            if (settings != null) {
                keys.forEach(settings::remove);
            }
        }

    }

}
