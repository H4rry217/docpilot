package io.docpilot.common.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BaseEntityTest {

    @AfterEach
    void tearDown() {
        BaseEntityAuditor.setAuditorSupplier(Optional::empty);
    }

    @Test
    void markCreatedFillsAuditFields() {
        BaseEntityAuditor.setAuditorSupplier(() -> Optional.of(new BaseEntityAuditor.Auditor(7L, "Harry")));
        BaseEntity entity = new BaseEntity();
        LocalDateTime before = LocalDateTime.now();

        entity.markCreated();

        LocalDateTime after = LocalDateTime.now();
        assertThat(entity.getCreateTime()).isBetween(before, after);
        assertThat(entity.getUpdateTime()).isBetween(before, after);
        assertThat(entity.getCreatorId()).isEqualTo(7L);
        assertThat(entity.getUpdaterId()).isEqualTo(7L);
        assertThat(entity.getCreateBy()).isEqualTo("Harry");
        assertThat(entity.getUpdateBy()).isEqualTo("Harry");
        assertThat(entity.getIsDeleted()).isFalse();
    }

    @Test
    void markUpdatedDoesNotOverrideExistingCreateFields() {
        BaseEntityAuditor.setAuditorSupplier(() -> Optional.of(new BaseEntityAuditor.Auditor(7L, "Harry")));
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 30, 2, 15);
        BaseEntity entity = new BaseEntity();
        entity.setCreateTime(createdAt);
        entity.setCreatorId(3L);
        entity.setCreateBy("Original");
        LocalDateTime before = LocalDateTime.now();

        entity.markUpdated();

        LocalDateTime after = LocalDateTime.now();
        assertThat(entity.getCreateTime()).isEqualTo(createdAt);
        assertThat(entity.getCreatorId()).isEqualTo(3L);
        assertThat(entity.getCreateBy()).isEqualTo("Original");
        assertThat(entity.getUpdateTime()).isBetween(before, after);
        assertThat(entity.getUpdaterId()).isEqualTo(7L);
        assertThat(entity.getUpdateBy()).isEqualTo("Harry");
    }

}
