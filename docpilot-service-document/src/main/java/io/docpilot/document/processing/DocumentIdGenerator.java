package io.docpilot.document.processing;

import java.util.UUID;

/**
 * Generates public document identifiers.
 */
public class DocumentIdGenerator {

    /**
     * Creates a UUID-based id without hyphen separators.
     */
    public String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
