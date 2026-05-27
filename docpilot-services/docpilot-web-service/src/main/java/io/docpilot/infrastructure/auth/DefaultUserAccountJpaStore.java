package io.docpilot.infrastructure.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DefaultUserAccountJpaStore extends JpaRepository<DefaultUserAccount, Long> {

    Optional<DefaultUserAccount> findByEmail(String email);

}
