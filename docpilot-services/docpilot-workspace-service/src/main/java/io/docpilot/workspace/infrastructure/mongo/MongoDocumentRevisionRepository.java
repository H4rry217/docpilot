package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB implementation of the document revision persistence port.
 */
@Repository
public class MongoDocumentRevisionRepository implements DocumentRevisionRepository {

    /**
     * Spring Mongo entry point used for revision queries.
     */
    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public DocumentRevision save(DocumentRevision revision) {
        return mongoTemplate.save(revision);
    }

    @Override
    public Optional<DocumentRevision> findById(Long revisionId) {
        return Optional.ofNullable(mongoTemplate.findById(revisionId, DocumentRevision.class));
    }

    @Override
    public Optional<DocumentRevision> findByDocumentIdAndClientMutationId(Long documentId, String clientMutationId) {
        // The pair is the idempotency key; querying both fields prevents cross-document token collisions.
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.DOCUMENT_ID).is(documentId)
                .and(WorkspaceMongoConstant.CLIENT_MUTATION_ID).is(clientMutationId));
        return Optional.ofNullable(mongoTemplate.findOne(query, DocumentRevision.class));
    }

    @Override
    public List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.DOCUMENT_ID).is(documentId))
                .with(Sort.by(Sort.Direction.DESC, WorkspaceMongoConstant.VERSION))
                .limit(limit);
        return mongoTemplate.find(query, DocumentRevision.class);
    }

}


