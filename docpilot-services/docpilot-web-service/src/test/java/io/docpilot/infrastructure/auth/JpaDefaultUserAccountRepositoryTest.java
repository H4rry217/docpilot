package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.auth.AuthSubjectContext;
import io.docpilot.common.domain.BaseEntityAuditor;
import io.docpilot.user.model.UserInformation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:docpilot_auth_jpa;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaDefaultUserAccountRepository.class)
@Sql("classpath:schema-mysql.sql")
class JpaDefaultUserAccountRepositoryTest {

    @Autowired
    private JpaDefaultUserAccountRepository repository;

    @Test
    void storesAccountsAndExposesUserInformation() {
        BaseEntityAuditor.setAuditorSupplier(() -> AuthSubjectContext.currentSubject()
                .map(subject -> new BaseEntityAuditor.Auditor(subject.getUserId(), subject.getDisplayName())));

        AuthSubject creator = subject(10L, "Creator");
        DefaultUserAccount account = AuthSubjectContext.callAs(creator, () -> {
            DefaultUserAccount newAccount = new DefaultUserAccount();
            newAccount.setEmail("alice@example.com");
            newAccount.setDisplayName("Alice");
            newAccount.setPasswordHash("hash");
            return repository.create(newAccount);
        });
        Long userId = account.getUserId();

        assertThat(repository.findByEmail(" Alice@Example.COM ")).hasValueSatisfying(saved -> {
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getPasswordHash()).isEqualTo("hash");
            assertThat(saved.getCreatorId()).isEqualTo(10L);
            assertThat(saved.getCreateBy()).isEqualTo("Creator");
            assertThat(saved.getUpdaterId()).isEqualTo(10L);
            assertThat(saved.getUpdateBy()).isEqualTo("Creator");
        });
        assertThat(repository.findByUserId(userId)).hasValueSatisfying(user -> {
            assertThat(user.getEmail()).isEqualTo("alice@example.com");
            assertThat(user.getDisplayName()).isEqualTo("Alice");
        });

        AuthSubject updater = subject(20L, "Updater");
        AuthSubjectContext.runAs(updater, () -> {
            UserInformation update = new UserInformation();
            update.setUserId(userId);
            update.setDisplayName("Alice Updated");
            update.setEmail("alice.updated@example.com");
            repository.save(update);
        });

        assertThat(repository.findByEmail("alice.updated@example.com")).hasValueSatisfying(updated -> {
            assertThat(updated.getCreatorId()).isEqualTo(10L);
            assertThat(updated.getCreateBy()).isEqualTo("Creator");
            assertThat(updated.getUpdaterId()).isEqualTo(20L);
            assertThat(updated.getUpdateBy()).isEqualTo("Updater");
        });
        assertThat(repository.findByUserId(userId).orElseThrow().getDisplayName()).isEqualTo("Alice Updated");
    }

    @Test
    void createPreservesPresetUserId() {
        DefaultUserAccount newAccount = new DefaultUserAccount();
        newAccount.setUserId(870001L);
        newAccount.setEmail("preset@example.com");
        newAccount.setDisplayName("Preset");
        newAccount.setPasswordHash("hash");

        DefaultUserAccount account = repository.create(newAccount);

        assertThat(account.getUserId()).isEqualTo(870001L);
        assertThat(repository.findByUserId(870001L)).hasValueSatisfying(user -> {
            assertThat(user.getEmail()).isEqualTo("preset@example.com");
            assertThat(user.getDisplayName()).isEqualTo("Preset");
        });
    }

    private AuthSubject subject(Long userId, String displayName) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        return subject;
    }

}
