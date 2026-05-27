package io.docpilot.document.repository;

import io.docpilot.document.model.DocumentShare;

import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for document file shares.
 */
public interface DocumentShareRepository {

    /**
     * Saves a document share.
     */
    DocumentShare save(DocumentShare share);

    /**
     * Finds a share by id.
     */
    Optional<DocumentShare> findById(String shareId);

    /**
     * Lists active or historical shares received by a target user.
     */
    List<DocumentShare> findByTargetUserId(Long targetUserId);

    /**
     * Finds a share for a specific target user and document.
     */
    Optional<DocumentShare> findByDocumentIdAndTargetUserId(String documentId, Long targetUserId);

}
