package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * MongoDB implementation of the workspace document repository.
 */
@Repository
public class MongoWorkspaceDocumentRepository implements WorkspaceDocumentRepository {

    /**
     * Spring Mongo entry point used for document persistence.
     */
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public WorkspaceDocument save(WorkspaceDocument document) {
        return mongoTemplate.save(document);
    }

    @Override
    public Optional<WorkspaceDocument> findById(Long documentId) {
        return Optional.ofNullable(mongoTemplate.findById(documentId, WorkspaceDocument.class));
    }

}


