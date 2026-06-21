package io.docpilot.infrastructure.auth;

import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.repository.UserInformationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

public class JpaDefaultUserAccountRepository implements DefaultUserAccountRepository, UserInformationRepository {

    private final DefaultUserAccountJpaStore accountStore;
    private final EntityManager entityManager;

    public JpaDefaultUserAccountRepository(DefaultUserAccountJpaStore accountStore,
                                           EntityManager entityManager) {
        this.accountStore = accountStore;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefaultUserAccount create(DefaultUserAccount account) {
        try {
            account.setEmail(normalizeEmail(account.getEmail()));
            if (account.getUserId() != null) {
                return insertWithPresetUserId(account);
            }
            return accountStore.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email is already registered");
        } catch (PersistenceException e) {
            throw new ConflictException("User is already registered");
        }
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Optional<DefaultUserAccount> findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return accountStore.findByEmail(normalizeEmail(email));
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Optional<DefaultUserAccount> findAccountByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return accountStore.findById(userId);
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public boolean hasAnyAccount() {
        return accountStore.count() > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefaultUserAccount updatePasswordHash(Long userId, String passwordHash) {
        DefaultUserAccount account = accountStore.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        account.setPasswordHash(passwordHash);
        return accountStore.saveAndFlush(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefaultUserAccount updateDisplayName(Long userId, String displayName) {
        DefaultUserAccount account = accountStore.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        account.setDisplayName(displayName);
        return accountStore.saveAndFlush(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInformation save(UserInformation userInformation) {
        if (userInformation == null || userInformation.getUserId() == null) {
            throw new IllegalArgumentException("User id is required");
        }
        DefaultUserAccount account = accountStore.findById(userInformation.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (StringUtils.hasText(userInformation.getEmail())) {
            account.setEmail(normalizeEmail(userInformation.getEmail()));
        }
        if (userInformation.getDisplayName() != null) {
            account.setDisplayName(userInformation.getDisplayName().strip());
        }
        try {
            return toUserInformation(accountStore.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email is already registered");
        }
    }

    @Override
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public Optional<UserInformation> findByUserId(Long userId) {
        return findAccountByUserId(userId).map(this::toUserInformation);
    }

    private UserInformation toUserInformation(DefaultUserAccount account) {
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(account.getUserId());
        userInformation.setDisplayName(account.getDisplayName());
        userInformation.setEmail(account.getEmail());
        return userInformation;
    }

    private DefaultUserAccount insertWithPresetUserId(DefaultUserAccount account) {
        account.markCreated();
        entityManager.createNativeQuery("""
                        INSERT INTO docpilot_user (
                            id,
                            email,
                            display_name,
                            password_hash,
                            create_time,
                            create_by,
                            creator_id,
                            update_time,
                            update_by,
                            updater_id,
                            is_deleted
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, account.getUserId())
                .setParameter(2, account.getEmail())
                .setParameter(3, account.getDisplayName())
                .setParameter(4, account.getPasswordHash())
                .setParameter(5, account.getCreateTime())
                .setParameter(6, account.getCreateBy())
                .setParameter(7, account.getCreatorId())
                .setParameter(8, account.getUpdateTime())
                .setParameter(9, account.getUpdateBy())
                .setParameter(10, account.getUpdaterId())
                .setParameter(11, account.getIsDeleted())
                .executeUpdate();
        entityManager.flush();
        return accountStore.findById(account.getUserId())
                .orElseThrow(() -> new IllegalStateException("Created user was not found"));
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

}
