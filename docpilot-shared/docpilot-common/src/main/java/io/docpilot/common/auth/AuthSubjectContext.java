package io.docpilot.common.auth;

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
        MDC.put("userId", subject.getUserId());
    }

    public static void clear() {
        CURRENT.remove();
        MDC.remove("userId");
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
