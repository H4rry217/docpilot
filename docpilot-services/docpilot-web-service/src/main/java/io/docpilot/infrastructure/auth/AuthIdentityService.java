package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.BadRequestException;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.auth.AuthPrincipal;
import io.docpilot.auth.AuthProvider;
import io.docpilot.auth.AuthUserProvisioning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate transactionTemplate;

    public AuthIdentityService(DefaultUserAccountRepository accountRepository,
                               AuthUserIdentityJpaStore identityStore,
                               TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.identityStore = identityStore;
        this.transactionTemplate = transactionTemplate;
    }

    public AuthSubject resolve(AuthProvider provider, AuthPrincipal principal) {
        AuthPrincipal normalized = validatePrincipal(provider, principal);
        AuthUserProvisioning provisioning = userProvisioning(provider, normalized);
        Optional<Long> resolvedUserId = provider.resolveUserId(normalized);
        if (resolvedUserId.isPresent()) {
            log.debug("auth principal resolved directly providerId={} userId={}",
                    normalized.providerId(), resolvedUserId.get());
            return toSubject(resolvedUserId.get(), normalized.displayName(), normalized.roles());
        }

        try {
            // Database unique constraints make first-login races safe across JVMs; conflicts are reread below.
            return resolveInTransaction(normalized, provisioning);
        } catch (ConflictException e) {
            return resolveAfterCreationConflict(normalized, provisioning, e);
        } catch (DataIntegrityViolationException e) {
            return resolveAfterCreationConflict(normalized, provisioning, new ConflictException("User is already registered"));
        }
    }

    private Optional<AuthSubject> resolveExistingIdentity(AuthPrincipal normalized) {
        AuthSubject subject = transactionTemplate.execute(status -> identityStore
                .findByProviderIdAndSubject(normalized.providerId(), normalized.subject())
                .map(identity -> toSubject(identity.getUserId(), displayName(identity, normalized), normalized.roles()))
                .orElse(null));
        return Optional.ofNullable(subject);
    }

    private AuthSubject resolveAfterCreationConflict(AuthPrincipal normalized,
                                                     AuthUserProvisioning provisioning,
                                                     ConflictException conflict) {
        Optional<AuthSubject> existingSubject = resolveExistingIdentity(normalized);
        if (existingSubject.isPresent()) {
            log.info("auth identity resolved after creation conflict providerId={} subjectHash={}",
                    normalized.providerId(), subjectHash(normalized));
            return existingSubject.get();
        }
        if (StringUtils.hasText(provisioning.email()) || provisioning.userId() != null) {
            throw conflict;
        }
        // Placeholder email creation can race before the identity row exists, so retry once then reread identity.
        log.info("auth identity creation conflicted, retrying placeholder binding providerId={} subjectHash={}",
                normalized.providerId(), subjectHash(normalized));
        return retryResolveInTransaction(normalized, provisioning);
    }

    private AuthSubject resolveInTransaction(AuthPrincipal normalized, AuthUserProvisioning provisioning) {
        AuthSubject subject = transactionTemplate.execute(status -> {
            Optional<AuthUserIdentity> existingIdentity = identityStore
                    .findByProviderIdAndSubject(normalized.providerId(), normalized.subject());
            AuthUserIdentity identity = existingIdentity.orElseGet(() -> createIdentity(normalized, provisioning));
            if (existingIdentity.isPresent()) {
                log.debug("auth identity resolved providerId={} subjectHash={} userId={}",
                        normalized.providerId(), subjectHash(normalized), identity.getUserId());
            }
            return toSubject(identity.getUserId(), displayName(identity, normalized), normalized.roles());
        });
        return Optional.ofNullable(subject)
                .orElseThrow(() -> new IllegalStateException("Resolved auth subject is required"));
    }

    private AuthSubject retryResolveInTransaction(AuthPrincipal normalized, AuthUserProvisioning provisioning) {
        try {
            return resolveInTransaction(normalized, provisioning);
        } catch (ConflictException e) {
            return resolveExistingIdentityAfterRetry(normalized, e);
        } catch (DataIntegrityViolationException e) {
            return resolveExistingIdentityAfterRetry(normalized, new ConflictException("User is already registered"));
        }
    }

    private AuthSubject resolveExistingIdentityAfterRetry(AuthPrincipal normalized, ConflictException conflict) {
        Optional<AuthSubject> existingSubject = resolveExistingIdentity(normalized);
        if (existingSubject.isPresent()) {
            log.info("auth identity resolved after retry conflict providerId={} subjectHash={}",
                    normalized.providerId(), subjectHash(normalized));
            return existingSubject.get();
        }
        throw conflict;
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

    private AuthUserIdentity createIdentity(AuthPrincipal principal, AuthUserProvisioning provisioning) {
        String email = email(principal.providerId(), principal.subject(), provisioning.email());
        String displayName = displayName(principal.subject(), provisioning.displayName(), email);
        Long provisionedUserId = provisioning.userId();
        boolean hasProviderEmail = StringUtils.hasText(provisioning.email());
        DefaultUserAccount savedAccount = hasProviderEmail
                ? createAccountWithProviderEmail(principal, provisionedUserId, email, displayName)
                : createOrReusePlaceholderAccount(principal, provisionedUserId, email, displayName);

        AuthUserIdentity identity = new AuthUserIdentity();
        identity.setProviderId(principal.providerId());
        identity.setSubject(principal.subject());
        identity.setUserId(savedAccount.getUserId());
        identity.setEmail(provisioning.email());
        identity.setDisplayName(provisioning.displayName());
        AuthUserIdentity savedIdentity = identityStore.saveAndFlush(identity);
        log.info("auth identity created providerId={} subjectHash={} userId={} emailProvided={}",
                principal.providerId(), subjectHash(principal), savedAccount.getUserId(), StringUtils.hasText(provisioning.email()));
        return savedIdentity;
    }

    private DefaultUserAccount createAccountWithProviderEmail(AuthPrincipal principal,
                                                              Long provisionedUserId,
                                                              String email,
                                                              String displayName) {
        if (accountRepository.findByEmail(email).isPresent()) {
            // Avoid silently attaching a provider subject to an existing password account.
            log.warn("auth identity creation rejected reason=email-exists providerId={} subjectHash={}",
                    principal.providerId(), subjectHash(principal));
            throw new ConflictException("Email is already registered");
        }
        return createProviderManagedAccount(principal, provisionedUserId, email, displayName);
    }

    private DefaultUserAccount createOrReusePlaceholderAccount(AuthPrincipal principal,
                                                               Long provisionedUserId,
                                                               String email,
                                                               String displayName) {
        Optional<DefaultUserAccount> existingAccount = accountRepository.findByEmail(email);
        if (existingAccount.isPresent()) {
            DefaultUserAccount account = existingAccount.get();
            if (!PROVIDER_MANAGED_PASSWORD_PLACEHOLDER.equals(account.getPasswordHash())) {
                log.warn("auth identity creation rejected reason=placeholder-email-owned providerId={} subjectHash={}",
                        principal.providerId(), subjectHash(principal));
                throw new ConflictException("Email is already registered");
            }
            if (provisionedUserId != null && !provisionedUserId.equals(account.getUserId())) {
                log.warn("auth identity creation rejected reason=placeholder-user-id-mismatch providerId={} subjectHash={}",
                        principal.providerId(), subjectHash(principal));
                throw new ConflictException("User id is already registered");
            }
            log.info("auth identity reused placeholder account providerId={} subjectHash={} userId={}",
                    principal.providerId(), subjectHash(principal), account.getUserId());
            return account;
        }
        return createProviderManagedAccount(principal, provisionedUserId, email, displayName);
    }

    private DefaultUserAccount createProviderManagedAccount(AuthPrincipal principal,
                                                            Long provisionedUserId,
                                                            String email,
                                                            String displayName) {
        if (provisionedUserId != null && accountRepository.findAccountByUserId(provisionedUserId).isPresent()) {
            log.warn("auth identity creation rejected reason=user-id-exists providerId={} subjectHash={} userId={}",
                    principal.providerId(), subjectHash(principal), provisionedUserId);
            throw new ConflictException("User id is already registered");
        }
        DefaultUserAccount account = new DefaultUserAccount();
        account.setUserId(provisionedUserId);
        account.setEmail(email);
        account.setDisplayName(displayName);
        account.setPasswordHash(PROVIDER_MANAGED_PASSWORD_PLACEHOLDER);
        return accountRepository.create(account);
    }

    private AuthSubject toSubject(Long userId, String displayName, java.util.Set<String> roles) {
        AuthSubject subject = new AuthSubject();
        subject.setUserId(userId);
        subject.setDisplayName(displayName);
        subject.setPlatformRoles(roles);
        return subject;
    }

    private AuthUserProvisioning userProvisioning(AuthProvider provider, AuthPrincipal principal) {
        AuthUserProvisioning provisioning = provider.userProvisioning(principal);
        return provisioning == null ? AuthUserProvisioning.from(principal) : provisioning;
    }

    private String email(String providerId, String subject, String provisionedEmail) {
        if (StringUtils.hasText(provisionedEmail)) {
            String normalized = provisionedEmail.strip().toLowerCase(Locale.ROOT);
            if (normalized.length() > MAX_EMAIL_LENGTH || !normalized.contains("@")) {
                throw new BadRequestException("Provider user email is invalid");
            }
            return normalized;
        }
        return placeholderEmail(providerId, subject);
    }

    private String placeholderEmail(String providerId, String subject) {
        // docpilot_user.email is required; some providers only expose an opaque subject.
        String hash = sha256(providerId + ":" + subject).substring(0, 32);
        return hash + "@provider.docpilot.invalid";
    }

    private String displayName(String subject, String provisionedDisplayName, String email) {
        String displayName = StringUtils.hasText(provisionedDisplayName)
                ? provisionedDisplayName.strip()
                : email.substring(0, email.indexOf('@'));
        if (!StringUtils.hasText(displayName)) {
            displayName = subject;
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
