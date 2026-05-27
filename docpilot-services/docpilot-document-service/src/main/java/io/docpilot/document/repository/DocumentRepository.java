package io.docpilot.document.repository;

import io.docpilot.document.model.DocPilotDocument;

import java.util.Optional;

/**
 * Storage boundary for documents.
 */
public interface DocumentRepository {

    /**
     * Saves a document aggregate.
     */
    DocPilotDocument save(DocPilotDocument document);

    /**
     * Finds a document by id.
     */
    Optional<DocPilotDocument> findById(String documentId);

}
