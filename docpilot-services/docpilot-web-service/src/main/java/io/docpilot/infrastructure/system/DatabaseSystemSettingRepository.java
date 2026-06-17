package io.docpilot.infrastructure.system;

import io.docpilot.system.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Database-backed repository for system-owned settings.
 */
public class DatabaseSystemSettingRepository implements SystemSettingRepository {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSystemSettingRepository.class);

    private final SystemSettingStore store;

    public DatabaseSystemSettingRepository(SystemSettingStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Optional<String> findByKey(String key) {
        if (!StringUtils.hasText(key)) {
            return Optional.empty();
        }
        return store.findById(key).map(SystemSettingEntity::getSettingValue);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String findOrCreate(String key, Supplier<String> valueSupplier) {
        String effectiveKey = requireKey(key);
        Optional<String> existing = findByKey(effectiveKey);
        if (existing.isPresent()) {
            log.debug("system setting reused key={}", effectiveKey);
            return existing.get();
        }
        String value = valueSupplier.get();
        try {
            SystemSettingEntity setting = new SystemSettingEntity();
            setting.setSettingKey(effectiveKey);
            setting.setSettingValue(value);
            String savedValue = store.saveAndFlush(setting).getSettingValue();
            log.info("system setting created key={}", effectiveKey);
            return savedValue;
        } catch (DataIntegrityViolationException e) {
            // A concurrent process may have created the same setting after our read; use its value.
            log.info("system setting reused after concurrent initialization key={}", effectiveKey);
            return findByKey(effectiveKey)
                    .orElseThrow(() -> new IllegalStateException("Failed to read initialized setting: " + effectiveKey, e));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(String key, String value) {
        String effectiveKey = requireKey(key);
        Optional<SystemSettingEntity> existing = store.findById(effectiveKey);
        SystemSettingEntity setting = existing.orElseGet(SystemSettingEntity::new);
        setting.setSettingKey(effectiveKey);
        setting.setSettingValue(value);
        store.saveAndFlush(setting);
        log.info("system setting saved key={} existed={}", effectiveKey, existing.isPresent());
    }

    private String requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("System setting key is required");
        }
        return key;
    }

}
