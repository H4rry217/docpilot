package io.docpilot.infrastructure.auth;

import io.docpilot.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Binding between a provider subject and a DocPilot user.
 */
@Getter
@Setter
@Entity
@Table(
        name = "docpilot_user_identity",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_docpilot_user_identity_provider_subject",
                columnNames = {"provider_id", "subject"}
        ),
        indexes = @Index(name = "idx_docpilot_user_identity_user_id", columnList = "user_id")
)
public class AuthUserIdentity extends BaseEntity {

    @Column(name = "provider_id", nullable = false, length = 120)
    private String providerId;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", length = 80)
    private String displayName;

}
