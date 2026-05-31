package io.docpilot.workspace.repository;

import io.docpilot.workspace.model.entity.WorkspaceDocument;

import java.util.Optional;

public interface WorkspaceDocumentRepository {

    WorkspaceDocument save(WorkspaceDocument document);

    Optional<WorkspaceDocument> findById(Long documentId);

}

