package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class MongoWorkspaceNodeRepository implements WorkspaceNodeRepository {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public WorkspaceNode save(WorkspaceNode node) {
        return mongoTemplate.save(node);
    }

    @Override
    public List<WorkspaceNode> saveAll(Collection<WorkspaceNode> nodes) {
        nodes.forEach(this::save);
        return List.copyOf(nodes);
    }

    @Override
    public Optional<WorkspaceNode> findById(Long nodeId) {
        return Optional.ofNullable(mongoTemplate.findById(nodeId, WorkspaceNode.class));
    }

    @Override
    public List<WorkspaceNode> findActiveByWorkspaceId(Long workspaceId) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.WORKSPACE_ID).is(workspaceId)
                        .and(WorkspaceMongoConstant.IS_DELETED).is(false))
                .with(Sort.by(
                        Sort.Direction.ASC,
                        WorkspaceMongoConstant.PARENT_NODE_ID,
                        WorkspaceMongoConstant.NODE_TYPE,
                        WorkspaceMongoConstant.NAME,
                        WorkspaceMongoConstant.CREATE_TIME
                ));
        return mongoTemplate.find(query, WorkspaceNode.class);
    }

    @Override
    public Optional<WorkspaceNode> findActiveByWorkspaceIdAndParentNodeIdAndName(
            Long workspaceId,
            Long parentNodeId,
            String name) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.WORKSPACE_ID).is(workspaceId)
                .and(WorkspaceMongoConstant.PARENT_NODE_ID).is(parentNodeId)
                .and(WorkspaceMongoConstant.NAME).is(name)
                .and(WorkspaceMongoConstant.IS_DELETED).is(false));
        return Optional.ofNullable(mongoTemplate.findOne(query, WorkspaceNode.class));
    }

    @Override
    public List<WorkspaceNode> findActiveByWorkspaceIdAndAncestor(Long workspaceId, Long ancestorNodeId) {
        Query query = Query.query(Criteria.where(WorkspaceMongoConstant.WORKSPACE_ID).is(workspaceId)
                .and(WorkspaceMongoConstant.ANCESTORS).is(ancestorNodeId)
                .and(WorkspaceMongoConstant.IS_DELETED).is(false))
                .with(Sort.by(Sort.Direction.ASC, WorkspaceMongoConstant.NAME));
        return mongoTemplate.find(query, WorkspaceNode.class);
    }

}


