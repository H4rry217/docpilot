package io.docpilot.workspace.knowledge.store;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Search request for knowledge chunks.
 */
@Getter
@Setter
public class KnowledgeSearchQuery {

    /**
     * Workspace id used as the mandatory tenant filter.
     */
    private Long workspaceId;

    /**
     * Optional owner user id used to further constrain visible chunks.
     */
    private Long ownerUserId;

    /**
     * Hard document filter generated from retrieval scope.
     */
    private List<Long> scopeDocumentIds = new ArrayList<>();

    /**
     * Current document boost target; it must never expand the hard scope.
     */
    private Long preferredDocumentId;

    /**
     * Optional revision filter for historical snapshot lookup.
     */
    private Long revisionId;

    /**
     * Lexical query text used by BM25-style search.
     */
    private String queryText;

    /**
     * Query vector used by dense retrieval providers.
     */
    private float[] queryEmbedding;

    /**
     * Optional chunk type filter, such as BLOCK or SECTION_SUMMARY.
     */
    private Set<String> includeChunkTypes = new LinkedHashSet<>();

    /**
     * Maximum number of chunks requested from the store.
     */
    private int limit = 8;

}
