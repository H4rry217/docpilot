package io.docpilot.infrastructure.auth;

import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.auth.AuthSubject;
import io.docpilot.common.exception.BadRequestException;
import io.docpilot.common.exception.ConflictException;
import io.docpilot.common.exception.ForbiddenException;
import io.docpilot.common.exception.UnauthorizedException;
import io.docpilot.infrastructure.auth.provider.AuthSession;
import io.docpilot.user.model.UserInformation;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class DefaultUserAuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final int MAX_DISPLAY_NAME_LENGTH = 80;

    private final DefaultUserAccountRepository accountRepository;
    private final PasswordHasher passwordHasher;
    private final DefaultJwtIssuer jwtIssuer;
    private final boolean allowRegistration;
    private final AuthContextProvider authContextProvider;

    public DefaultUserAuthService(DefaultUserAccountRepository accountRepository,
                                  PasswordHasher passwordHasher,
                                  DefaultJwtIssuer jwtIssuer,
                                  boolean allowRegistration,
                                  AuthContextProvider authContextProvider) {
        this.accountRepository = accountRepository;
        this.passwordHasher = passwordHasher;
        this.jwtIssuer = jwtIssuer;
        this.allowRegistration = allowRegistration;
        this.authContextProvider = authContextProvider;
    }

    public UserInformation register(String email, String password, String displayName) {
        if (!allowRegistration) {
            throw new ForbiddenException("Registration is disabled");
        }
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedEmail);
        if (accountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ConflictException("Email is already registered");
        }

        DefaultUserAccount account = new DefaultUserAccount();
        account.setEmail(normalizedEmail);
        account.setDisplayName(normalizedDisplayName);
        account.setPasswordHash(passwordHasher.hash(password));
        DefaultUserAccount saved = accountRepository.create(account);
        return toUserInformation(saved);
    }

    public AuthSession login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        DefaultUserAccount account = accountRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordHasher.verify(password, account.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return new AuthSession(jwtIssuer.issue(account), toUserInformation(account));
    }

    public UserInformation currentUser() {
        return toUserInformation(currentAccount());
    }

    public void changePassword(String currentPassword, String newPassword) {
        validatePassword(newPassword);
        DefaultUserAccount account = currentAccount();
        if (!passwordHasher.verify(currentPassword, account.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        accountRepository.updatePasswordHash(account.getUserId(), passwordHasher.hash(newPassword));
    }

    public UserInformation changeDisplayName(String displayName) {
        DefaultUserAccount account = currentAccount();
        DefaultUserAccount updated = accountRepository.updateDisplayName(
                account.getUserId(),
                normalizeDisplayName(displayName, account.getEmail())
        );
        return toUserInformation(updated);
    }

    private DefaultUserAccount currentAccount() {
        Long userId = authContextProvider.currentSubject()
                .map(AuthSubject::getUserId)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        return accountRepository.findAccountByUserId(userId)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user no longer exists"));
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BadRequestException("Email is required");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new BadRequestException("Email is invalid");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BadRequestException("Password length must be between 8 and 128 characters");
        }
    }

    private String normalizeDisplayName(String displayName, String email) {
        String normalized = Optional.ofNullable(displayName)
                .filter(StringUtils::hasText)
                .map(String::strip)
                .orElseGet(() -> email.substring(0, email.indexOf('@')));
        if (!StringUtils.hasText(normalized) || normalized.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new BadRequestException("Display name length must be between 1 and 80 characters");
        }
        return normalized;
    }

    private UserInformation toUserInformation(DefaultUserAccount account) {
        UserInformation userInformation = new UserInformation();
        userInformation.setUserId(account.getUserId());
        userInformation.setDisplayName(account.getDisplayName());
        userInformation.setEmail(account.getEmail());
        return userInformation;
    }

}
