package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MongoDocumentRevisionRepository implements DocumentRevisionRepository {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public DocumentRevision save(DocumentRevision revision) {
        return mongoTemplate.save(revision);
    }

    @Override
    public List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.DOCUMENT_ID).is(documentId))
                .with(Sort.by(Sort.Direction.DESC, WorkspaceMongoConstant.VERSION))
                .limit(limit);
        return mongoTemplate.find(query, DocumentRevision.class);
    }

}


