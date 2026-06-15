package io.docpilot.infrastructure.user;

import io.docpilot.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Persisted row for one saved user setting.
 *
 * <p>One user can save at most one value per whitelisted setting key.</p>
 */
@Getter
@Setter
@Entity
@Table(
        name = "docpilot_user_setting",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_docpilot_user_setting_user_key",
                columnNames = {"user_id", "setting_key"}
        )
)
public class UserSettingEntity extends BaseEntity {

    /**
     * Owner user id.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Whitelisted setting key.
     */
    @Column(name = "setting_key", nullable = false, length = 120)
    private String settingKey;

    /**
     * Raw setting value interpreted by {@code UserSettingKeys}; examples: {@code en-US}, {@code true}, {@code 64}.
     */
    @Column(name = "setting_value", nullable = false, length = 4096)
    private String settingValue;

}
