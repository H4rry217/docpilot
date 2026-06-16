package io.docpilot.infrastructure.user;

import io.docpilot.user.model.UserSettingRecord;
import io.docpilot.user.repository.UserSettingRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DatabaseUserSettingRepository implements UserSettingRepository {

    private final UserSettingStore store;

    public DatabaseUserSettingRepository(UserSettingStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public List<UserSettingRecord> findByUserIdAndKeys(Long userId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return store.findByUserIdAndSettingKeyIn(userId, keys).stream()
                .map(setting -> new UserSettingRecord(
                        setting.getUserId(),
                        setting.getSettingKey(),
                        setting.getSettingValue()
                ))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAll(Long userId, Map<String, String> settingValueByKey) {
        if (settingValueByKey == null || settingValueByKey.isEmpty()) {
            return;
        }
        settingValueByKey.forEach((key, settingValue) -> {
            UserSettingEntity setting = store.findByUserIdAndSettingKey(userId, key)
                    .orElseGet(UserSettingEntity::new);
            setting.setUserId(userId);
            setting.setSettingKey(key);
            setting.setSettingValue(settingValue);
            store.save(setting);
        });
        store.flush();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByUserIdAndKeys(Long userId, Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        store.deleteByUserIdAndSettingKeyIn(userId, keys);
    }

}
