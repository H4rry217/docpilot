package io.docpilot.system.repository;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Storage boundary for system-owned settings.
 */
public interface SystemSettingRepository {

    Optional<String> findByKey(String key);

    String findOrCreate(String key, Supplier<String> valueSupplier);

    void save(String key, String value);

}
