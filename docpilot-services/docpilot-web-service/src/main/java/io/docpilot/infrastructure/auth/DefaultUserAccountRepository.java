package io.docpilot.infrastructure.auth;

import java.util.Optional;

public interface DefaultUserAccountRepository {

    DefaultUserAccount create(DefaultUserAccount account);

    Optional<DefaultUserAccount> findByEmail(String email);

    Optional<DefaultUserAccount> findAccountByUserId(Long userId);

    boolean hasAnyAccount();

    DefaultUserAccount updatePasswordHash(Long userId, String passwordHash);

    DefaultUserAccount updateDisplayName(Long userId, String displayName);

}
