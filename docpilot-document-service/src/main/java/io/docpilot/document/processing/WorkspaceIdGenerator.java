package io.docpilot.document.processing;

import java.util.UUID;

/**
 * Generates public workspace identifiers.
 */
public class WorkspaceIdGenerator {

    /**
     * Creates a UUID-based id without hyphen separators.
     */
    public String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
