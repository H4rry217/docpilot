package io.docpilot.block.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable root object for one DocPilot document snapshot.
 */
@Getter
@Setter
public class BlockDocument {

    /**
     * Current schema id for the expanded DocPilot block contract.
     */
    public static final String CURRENT_SCHEMA_VERSION = "docpilot-block/2";

    /**
     * Block schema version used by downstream clients.
     */
    private String schemaVersion = CURRENT_SCHEMA_VERSION;

    /**
     * Top-level document blocks in reading order.
     */
    private List<BlockNode> blocks = new ArrayList<>();

    /**
     * Document-level extension metadata.
     */
    private Map<String, Object> metadata = new HashMap<>();

    public static BlockDocument of(List<BlockNode> blocks) {
        BlockDocument document = new BlockDocument();
        document.setBlocks(blocks == null ? new ArrayList<>() : new ArrayList<>(blocks));
        return document;
    }

}
