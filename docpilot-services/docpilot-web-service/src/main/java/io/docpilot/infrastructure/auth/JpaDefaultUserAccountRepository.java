package io.docpilot.infrastructure.auth;

import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.NotFoundException;
import io.docpilot.user.model.UserInformation;
import io.docpilot.user.repository.UserInformationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

public class JpaDefaultUserAccountRepository implements DefaultUserAccountRepository, UserInformationRepository {

    private final DefaultUserAccountJpaStore accountStore;

    public JpaDefaultUserAccountRepository(DefaultUserAccountJpaStore accountStore) {
        this.accountStore = accountStore;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DefaultUserAccount create(DefaultUserAccount account) {
        try {
            account.setEmail(normalizeEmail(account.getEmail()));
            return accountStore.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Email is already registered");
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

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

}
