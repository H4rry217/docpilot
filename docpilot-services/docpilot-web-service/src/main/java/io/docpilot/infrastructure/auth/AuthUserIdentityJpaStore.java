package io.docpilot.infrastructure.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserIdentityJpaStore extends JpaRepository<AuthUserIdentity, Long> {

    Optional<AuthUserIdentity> findByProviderIdAndSubject(String providerId, String subject);

}
