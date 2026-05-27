package io.docpilot.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Common database entity metadata.
 */
@Getter
@Setter
@MappedSuperclass
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "create_by")
    private String createBy;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @Column(name = "update_by")
    private String updateBy;

    @Column(name = "updater_id")
    private Long updaterId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @PrePersist
    protected void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
        if (isDeleted == null) {
            isDeleted = false;
        }
        BaseEntityAuditor.currentAuditor().ifPresent(auditor -> {
            if (creatorId == null) {
                creatorId = auditor.userId();
            }
            if (!hasText(createBy)) {
                createBy = auditorName(auditor);
            }
            updaterId = auditor.userId();
            updateBy = auditorName(auditor);
        });
    }

    @PreUpdate
    protected void beforeUpdate() {
        updateTime = LocalDateTime.now();
        if (isDeleted == null) {
            isDeleted = false;
        }
        BaseEntityAuditor.currentAuditor().ifPresent(auditor -> {
            updaterId = auditor.userId();
            updateBy = auditorName(auditor);
        });
    }

    private String auditorName(BaseEntityAuditor.Auditor auditor) {
        if (hasText(auditor.name())) {
            return auditor.name();
        }
        return auditor.userId() == null ? null : String.valueOf(auditor.userId());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
