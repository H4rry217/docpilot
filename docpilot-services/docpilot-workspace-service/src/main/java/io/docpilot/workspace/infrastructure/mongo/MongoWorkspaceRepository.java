package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.repository.WorkspaceRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MongoWorkspaceRepository implements WorkspaceRepository {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public Workspace save(Workspace workspace) {
        return mongoTemplate.save(workspace);
    }

    @Override
    public Optional<Workspace> findById(Long workspaceId) {
        return Optional.ofNullable(mongoTemplate.findById(workspaceId, Workspace.class));
    }

    @Override
    public Optional<Workspace> findActivePersonalByOwnerUserId(Long ownerUserId) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.OWNER_USER_ID).is(ownerUserId)
                .and(WorkspaceMongoConstant.TYPE).is(WorkspaceType.PERSONAL.getValue())
                .and(WorkspaceMongoConstant.IS_DELETED).is(false));
        return Optional.ofNullable(mongoTemplate.findOne(query, Workspace.class));
    }

    @Override
    public List<Workspace> findActiveByOwnerUserId(Long ownerUserId) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.OWNER_USER_ID).is(ownerUserId)
                        .and(WorkspaceMongoConstant.IS_DELETED).is(false))
                .with(Sort.by(Sort.Direction.ASC, WorkspaceMongoConstant.CREATE_TIME));
        return mongoTemplate.find(query, Workspace.class);
    }

}


