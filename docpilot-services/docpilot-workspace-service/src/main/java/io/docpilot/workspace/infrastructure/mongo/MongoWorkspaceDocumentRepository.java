package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import jakarta.annotation.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MongoWorkspaceDocumentRepository implements WorkspaceDocumentRepository {

    @Resource
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


