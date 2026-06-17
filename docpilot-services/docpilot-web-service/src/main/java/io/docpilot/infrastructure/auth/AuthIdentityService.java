package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.BadRequestException;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps provider principals to the DocPilot user table.
 */
public class AuthIdentityService {

    private static final Logger log = LoggerFactory.getLogger(AuthIdentityService.class);

    private static final String PROVIDER_MANAGED_PASSWORD_PLACEHOLDER = "provider:disabled";
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_DISPLAY_NAME_LENGTH = 80;

    private final DefaultUserAccountRepository accountRepository;
    private final AuthUserIdentityJpaStore identityStore;

    public AuthIdentityService(DefaultUserAccountRepository accountRepository,
                               AuthUserIdentityJpaStore identityStore) {
        this.accountRepository = accountRepository;
        this.identityStore = identityStore;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthSubject resolve(AuthProvider provider, AuthPrincipal principal) {
        AuthPrincipal normalized = validatePrincipal(provider, principal);
        Optional<Long> resolvedUserId = provider.resolveUserId(normalized);
        if (resolvedUserId.isPresent()) {
            log.debug("auth principal resolved directly providerId={} userId={}",
                    normalized.providerId(), resolvedUserId.get());
            return toSubject(resolvedUserId.get(), normalized.displayName(), normalized.roles());
        }

        Optional<AuthUserIdentity> existingIdentity = identityStore
                .findByProviderIdAndSubject(normalized.providerId(), normalized.subject());
        AuthUserIdentity identity = existingIdentity.orElseGet(() -> createIdentity(normalized));
        if (existingIdentity.isPresent()) {
            log.debug("auth identity resolved providerId={} subjectHash={} userId={}",
                    normalized.providerId(), subjectHash(normalized), identity.getUserId());
        }
        return toSubject(identity.getUserId(), displayName(identity, normalized), normalized.roles());
    }

    private AuthPrincipal validatePrincipal(AuthProvider provider, AuthPrincipal principal) {
        if (provider == null) {
            throw new UnauthorizedException("Authentication provider is missing");
        }
        if (principal == null) {
            throw new UnauthorizedException("Authentication principal is missing");
        }
        if (!StringUtils.hasText(principal.providerId())) {
            throw new UnauthorizedException("Authentication provider id is missing");
        }
        if (!provider.providerId().equals(principal.providerId())) {
            throw new UnauthorizedException("Authentication provider mismatch");
        }
        if (!StringUtils.hasText(principal.subject())) {
            throw new UnauthorizedException("Authentication subject is missing");
        }
        return principal;
    }

    private AuthUserIdentity createIdentity(AuthPrincipal principal) {
        String email = email(principal);
        if (StringUtils.hasText(principal.email()) && accountRepository.findByEmail(email).isPresent()) {
            // Avoid silently attaching a provider subject to an existing password account.
            log.warn("auth identity creation rejected reason=email-exists providerId={} subjectHash={}",
                    principal.providerId(), subjectHash(principal));
            throw new ConflictException("Email is already registered");
        }

        DefaultUserAccount account = new DefaultUserAccount();
        account.setEmail(email);
        account.setDisplayName(displayName(principal));
        account.setPasswordHash(PROVIDER_MANAGED_PASSWORD_PLACEHOLDER);
        DefaultUserAccount savedAccount = accountRepository.create(account);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setProviderId(principal.providerId());
        identity.setSubject(principal.subject());
        identity.setUserId(savedAccount.getUserId());
        identity.setEmail(principal.email());
        identity.setDisplayName(principal.displayName());
        AuthUserIdentity savedIdentity = identityStore.saveAndFlush(identity);
        log.info("auth identity created providerId={} subjectHash={} userId={} emailProvided={}",
                principal.providerId(), subjectHash(principal), savedAccount.getUserId(), StringUtils.hasText(principal.email()));
        return savedIdentity;
    }

    private AuthSubject toSubject(Long userId, String displayName, java.util.Set<String> roles) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        subject.setPlatformRoles(roles);
        return subject;
    }

    private String email(AuthPrincipal principal) {
        if (StringUtils.hasText(principal.email())) {
            String normalized = principal.email().strip().toLowerCase(Locale.ROOT);
            if (normalized.length() > MAX_EMAIL_LENGTH || !normalized.contains("@")) {
                throw new BadRequestException("Provider user email is invalid");
            }
            return normalized;
        }
        return placeholderEmail(principal.providerId(), principal.subject());
    }

    private String placeholderEmail(String providerId, String subject) {
        // docpilot_user.email is required; some providers only expose an opaque subject.
        String hash = sha256(providerId + ":" + subject).substring(0, 32);
        return hash + "@provider.docpilot.invalid";
    }

    private String displayName(AuthPrincipal principal) {
        String displayName = StringUtils.hasText(principal.displayName())
                ? principal.displayName().strip()
                : email(principal).substring(0, email(principal).indexOf('@'));
        if (!StringUtils.hasText(displayName)) {
            displayName = principal.subject();
        }
        return truncate(displayName, MAX_DISPLAY_NAME_LENGTH);
    }

    private String displayName(AuthUserIdentity identity, AuthPrincipal principal) {
        if (StringUtils.hasText(principal.displayName())) {
            return truncate(principal.displayName().strip(), MAX_DISPLAY_NAME_LENGTH);
        }
        if (StringUtils.hasText(identity.getDisplayName())) {
            return identity.getDisplayName();
        }
        return String.valueOf(identity.getUserId());
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash auth subject", e);
        }
    }

    private String subjectHash(AuthPrincipal principal) {
        return sha256(principal.providerId() + ":" + principal.subject()).substring(0, 16);
    }

}
