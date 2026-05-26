package io.docpilot.block.processing;

import java.util.UUID;

/**
 * Generates public block identifiers used by the DocPilot document model.
 */
public class BlockIdGenerator {

    /**
     * Creates a UUID-based identifier without hyphen separators.
     */
    public String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
