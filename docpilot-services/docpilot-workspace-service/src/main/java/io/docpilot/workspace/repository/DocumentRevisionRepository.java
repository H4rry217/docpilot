package io.docpilot.workspace.repository;

import io.docpilot.workspace.model.entity.DocumentRevision;

import java.util.List;

public interface DocumentRevisionRepository {

    DocumentRevision save(DocumentRevision revision);

    List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit);

}

