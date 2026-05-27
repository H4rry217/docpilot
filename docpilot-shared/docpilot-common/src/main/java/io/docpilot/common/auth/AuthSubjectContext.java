package io.docpilot.common.auth;

import io.docpilot.common.context.RequestConstants;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Request or task scoped authenticated subject storage.
 */
public final class AuthSubjectContext {

    private static final ThreadLocal<AuthSubject> CURRENT = new ThreadLocal<>();

    public static Optional<AuthSubject> currentSubject() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static void set(AuthSubject subject) {
        if (subject == null) {
            clear();
            return;
        }
        CURRENT.set(subject);
        String userId = String.valueOf(subject.getUserId());
        MDC.put(RequestConstants.KEY_USER_ID, userId);
        MDC.put(RequestConstants.KEY_USER, userId);
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove(RequestConstants.KEY_USER_ID);
        MDC.remove(RequestConstants.KEY_USER);
    }

    public static void runAs(AuthSubject subject, Runnable runnable) {
        callAs(subject, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T callAs(AuthSubject subject, Supplier<T> supplier) {
        AuthSubject previous = CURRENT.get();
        set(subject);
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                clear();
            } else {
                set(previous);
            }
        }
    }

}
