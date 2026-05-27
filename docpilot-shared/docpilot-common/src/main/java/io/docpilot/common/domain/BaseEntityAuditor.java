package io.docpilot.common.domain;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Process-wide bridge used by JPA entity callbacks to read the current auditor.
 */
public final class BaseEntityAuditor {

    private static volatile Supplier<Optional<Auditor>> auditorSupplier = Optional::empty;

    public static Optional<Auditor> currentAuditor() {
        return auditorSupplier.get();
    }

    public static void setAuditorSupplier(Supplier<Optional<Auditor>> supplier) {
        auditorSupplier = supplier == null ? Optional::empty : supplier;
    }

    public record Auditor(Long userId, String name) {
    }

}
