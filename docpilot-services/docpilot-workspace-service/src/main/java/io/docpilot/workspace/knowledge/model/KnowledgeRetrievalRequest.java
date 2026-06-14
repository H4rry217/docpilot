package io.docpilot.workspace.knowledge.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Retrieval request used by inline completion and future RAG features.
 */
@Getter
@Setter
public class KnowledgeRetrievalRequest {

    /**
     * Workspace scope resolved by the caller or filesystem adapter.
     */
    private Long workspaceId;

    /**
     * Optional owner user filter for permission and tenant isolation.
     */
    private Long ownerUserId;

    /**
     * Caller-visible path used as a retrieval hint or scope source.
     */
    private String path;

    /**
     * Hard document scope; providers must not return documents outside this set when non-empty.
     */
    private List<Long> scopeDocumentIds = new ArrayList<>();

    /**
     * Soft ranking preference for the current document inside the hard scope.
     */
    private Long preferredDocumentId;

    /**
     * Optional revision filter for snapshot-specific retrieval.
     */
    private Long revisionId;

    /**
     * User query text sent to the retrieval provider.
     */
    private String queryText;

    /**
     * Optional chunk type filter, such as BLOCK or SECTION_SUMMARY.
     */
    private Set<String> includeChunkTypes = new LinkedHashSet<>();

    /**
     * Maximum number of ranked hits requested from the provider.
     */
    private int limit = 8;

}
