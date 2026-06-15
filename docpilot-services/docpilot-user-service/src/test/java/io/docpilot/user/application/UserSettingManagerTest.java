package io.docpilot.user.application;

import io.docpilot.common.exception.BadRequestException;
import io.docpilot.user.model.UserSettingKeys;
import io.docpilot.user.model.UserSettingRecord;
import io.docpilot.user.model.UserSettingSource;
import io.docpilot.user.model.response.UserSettingResponse;
import io.docpilot.user.repository.UserSettingCache;
import io.docpilot.user.repository.UserSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingManagerTest {

    @Test
    void getSettingsMergesDefaultsAndSavedValues() {
        UserSettingManager manager = new UserSettingManager(new InMemoryUserSettingRepository());

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_LOCALE)).settings(), UserSettingKeys.APP_LOCALE))
                .satisfies(setting -> {
                    assertThat(setting.value()).isEqualTo("zh-CN");
                    assertThat(setting.source()).isEqualTo(UserSettingSource.DEFAULT);
                });

        manager.saveSettings(1L, Map.of(UserSettingKeys.APP_LOCALE, "en-US"));

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_LOCALE)).settings(), UserSettingKeys.APP_LOCALE))
                .satisfies(setting -> {
                    assertThat(setting.value()).isEqualTo("en-US");
                    assertThat(setting.source()).isEqualTo(UserSettingSource.USER);
                });
    }

    @Test
    void removeSettingsFallsBackToDefaults() {
        UserSettingManager manager = new UserSettingManager(new InMemoryUserSettingRepository());
        manager.saveSettings(1L, Map.of(UserSettingKeys.APP_DEVELOPER_MODE, true));

        manager.removeSettings(1L, List.of(UserSettingKeys.APP_DEVELOPER_MODE));

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_DEVELOPER_MODE)).settings(),
                UserSettingKeys.APP_DEVELOPER_MODE))
                .satisfies(setting -> {
                    assertThat(setting.value()).isEqualTo(false);
                    assertThat(setting.source()).isEqualTo(UserSettingSource.DEFAULT);
                });
    }

    @Test
    void rejectUnknownKeysAndInvalidValues() {
        UserSettingManager manager = new UserSettingManager(new InMemoryUserSettingRepository());

        assertThatThrownBy(() -> manager.saveSettings(1L, Map.of("unknown", true)))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> manager.saveSettings(1L, Map.of(UserSettingKeys.APP_DEVELOPER_MODE, "maybe")))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> manager.saveSettings(1L, Map.of(UserSettingKeys.INLINE_COMPLETION_IDLE_DELAY_MS, 100)))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> manager.saveSettings(1L, Map.of(UserSettingKeys.INLINE_COMPLETION_CANDIDATE_COUNT, 6)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tokenValuesClampToServerMaximum() {
        UserSettingManager manager = new UserSettingManager(new InMemoryUserSettingRepository());

        manager.saveSettings(1L, Map.of(UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT, 9999));

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT)).settings(),
                UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SHORT).value())
                .isEqualTo(128);
    }

    @Test
    void settingValuesUsePlainStringStorageAndParseCommonStringForms() {
        InMemoryUserSettingRepository repository = new InMemoryUserSettingRepository();
        UserSettingManager manager = new UserSettingManager(repository);

        manager.saveSettings(1L, Map.of(
                UserSettingKeys.APP_LOCALE, "en-US",
                UserSettingKeys.APP_DEVELOPER_MODE, "1",
                UserSettingKeys.INLINE_COMPLETION_IDLE_DELAY_MS, "750",
                UserSettingKeys.INLINE_COMPLETION_CANDIDATE_COUNT, "4"
        ));

        assertThat(repository.valuesByUserId.get(1L))
                .containsEntry(UserSettingKeys.APP_LOCALE, "en-US")
                .containsEntry(UserSettingKeys.APP_DEVELOPER_MODE, "true")
                .containsEntry(UserSettingKeys.INLINE_COMPLETION_IDLE_DELAY_MS, "750")
                .containsEntry(UserSettingKeys.INLINE_COMPLETION_CANDIDATE_COUNT, "4");
        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_DEVELOPER_MODE)).settings(),
                UserSettingKeys.APP_DEVELOPER_MODE).value())
                .isEqualTo(true);

        repository.saveAll(1L, Map.of(
                UserSettingKeys.APP_LOCALE, "\"en-US\"",
                UserSettingKeys.APP_DEVELOPER_MODE, "0",
                UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE, "96"
        ));

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_LOCALE)).settings(),
                UserSettingKeys.APP_LOCALE).value())
                .isEqualTo("en-US");
        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_DEVELOPER_MODE)).settings(),
                UserSettingKeys.APP_DEVELOPER_MODE).value())
                .isEqualTo(false);
        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE)).settings(),
                UserSettingKeys.INLINE_COMPLETION_MAX_OUTPUT_TOKENS_SENTENCE).value())
                .isEqualTo(96);
    }

    @Test
    void savedSettingsAreIsolatedByUserId() {
        UserSettingManager manager = new UserSettingManager(new InMemoryUserSettingRepository());
        manager.saveSettings(1L, Map.of(UserSettingKeys.APP_LOCALE, "en-US"));

        assertThat(setting(manager.getSettings(2L, List.of(UserSettingKeys.APP_LOCALE)).settings(),
                UserSettingKeys.APP_LOCALE))
                .satisfies(setting -> {
                    assertThat(setting.value()).isEqualTo("zh-CN");
                    assertThat(setting.source()).isEqualTo(UserSettingSource.DEFAULT);
                });
    }

    @Test
    void readsFromCacheWhenAvailable() {
        InMemoryUserSettingRepository repository = new InMemoryUserSettingRepository();
        InMemoryUserSettingCache cache = new InMemoryUserSettingCache();
        cache.save(1L, Map.of(UserSettingKeys.APP_LOCALE, "en-US"));
        UserSettingManager manager = new UserSettingManager(repository, cache);

        assertThat(setting(manager.getSettings(1L, List.of(UserSettingKeys.APP_LOCALE)).settings(),
                UserSettingKeys.APP_LOCALE))
                .satisfies(setting -> {
                    assertThat(setting.value()).isEqualTo("en-US");
                    assertThat(setting.source()).isEqualTo(UserSettingSource.USER);
                });
        assertThat(repository.findCount).isZero();
    }

    @Test
    void populatesCacheOnMissAndInvalidatesAfterWrite() {
        InMemoryUserSettingRepository repository = new InMemoryUserSettingRepository();
        InMemoryUserSettingCache cache = new InMemoryUserSettingCache();
        UserSettingManager manager = new UserSettingManager(repository, cache);
        repository.saveAll(1L, Map.of(UserSettingKeys.APP_LOCALE, "en-US"));

        manager.getSettings(1L, List.of(UserSettingKeys.APP_LOCALE));

        assertThat(repository.findCount).isEqualTo(1);
        assertThat(cache.valuesByUserId.get(1L)).containsEntry(UserSettingKeys.APP_LOCALE, "en-US");

        manager.saveSettings(1L, Map.of(UserSettingKeys.APP_DEVELOPER_MODE, true));

        assertThat(cache.evictCount).isEqualTo(1);
        assertThat(cache.valuesByUserId.get(1L)).containsEntry(UserSettingKeys.APP_DEVELOPER_MODE, "true");

        manager.removeSettings(1L, List.of(UserSettingKeys.APP_DEVELOPER_MODE));

        assertThat(cache.evictCount).isEqualTo(2);
    }

    private static UserSettingResponse setting(List<UserSettingResponse> settings, String key) {
        return settings.stream()
                .filter(setting -> key.equals(setting.key()))
                .findFirst()
                .orElseThrow();
    }

    private static class InMemoryUserSettingRepository implements UserSettingRepository {

        private final Map<Long, Map<String, String>> valuesByUserId = new LinkedHashMap<>();

        private int findCount;

        @Override
        public List<UserSettingRecord> findByUserIdAndKeys(Long userId, Collection<String> keys) {
            findCount++;
            Map<String, String> values = valuesByUserId.getOrDefault(userId, Map.of());
            return keys.stream()
                    .filter(values::containsKey)
                    .map(key -> new UserSettingRecord(userId, key, values.get(key)))
                    .toList();
        }

        @Override
        public void saveAll(Long userId, Map<String, String> settingValueByKey) {
            valuesByUserId.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()).putAll(settingValueByKey);
        }

        @Override
        public void removeByUserIdAndKeys(Long userId, Collection<String> keys) {
            Map<String, String> values = valuesByUserId.get(userId);
            if (values != null) {
                keys.forEach(values::remove);
            }
        }

    }

    private static class InMemoryUserSettingCache implements UserSettingCache {

        private final Map<Long, Map<String, String>> valuesByUserId = new LinkedHashMap<>();

        private int evictCount;

        @Override
        public Optional<Map<String, String>> findByUserId(Long userId) {
            Map<String, String> values = valuesByUserId.get(userId);
            return values == null ? Optional.empty() : Optional.of(new LinkedHashMap<>(values));
        }

        @Override
        public void save(Long userId, Map<String, String> settingValueByKey) {
            valuesByUserId.put(userId, new LinkedHashMap<>(settingValueByKey));
        }

        @Override
        public void evict(Long userId) {
            evictCount++;
            valuesByUserId.remove(userId);
        }

    }

}

