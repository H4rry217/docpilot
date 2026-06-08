package io.docpilot.workspace.repository;

import io.docpilot.workspace.model.entity.DocumentRevision;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for immutable document revisions.
 */
public interface DocumentRevisionRepository {

    /**
     * Persists a new document revision.
     *
     * @param revision revision snapshot to save.
     * @return persisted revision.
     */
    DocumentRevision save(DocumentRevision revision);

    /**
     * Finds a revision already written for a client mutation id on one document.
     *
     * @param documentId document aggregate id.
     * @param clientMutationId client-provided idempotency token.
     * @return matching revision when the mutation was saved before.
     */
    Optional<DocumentRevision> findByDocumentIdAndClientMutationId(Long documentId, String clientMutationId);

    /**
     * Lists newest revisions first for one document.
     *
     * @param documentId document aggregate id.
     * @param limit maximum number of revisions to return.
     * @return revisions ordered by descending version.
     */
    List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit);

}

