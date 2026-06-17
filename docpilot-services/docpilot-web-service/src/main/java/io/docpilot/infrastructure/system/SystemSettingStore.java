package io.docpilot.infrastructure.system;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSettingStore extends JpaRepository<SystemSettingEntity, String> {
}
