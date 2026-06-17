package io.docpilot.infrastructure.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted row for one system-owned setting.
 */
@Getter
@Setter
@Entity
@Table(name = "docpilot_setting")
public class SystemSettingEntity {

    @Id
    @Column(name = "setting_key", nullable = false, length = 120)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 2048)
    private String settingValue;

}
