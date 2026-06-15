package io.docpilot.infrastructure.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserSettingStore extends JpaRepository<UserSettingEntity, Long> {

    Optional<UserSettingEntity> findByUserIdAndSettingKey(Long userId, String settingKey);

    List<UserSettingEntity> findByUserIdAndSettingKeyIn(Long userId, Collection<String> settingKeys);

    void deleteByUserIdAndSettingKeyIn(Long userId, Collection<String> settingKeys);
}
