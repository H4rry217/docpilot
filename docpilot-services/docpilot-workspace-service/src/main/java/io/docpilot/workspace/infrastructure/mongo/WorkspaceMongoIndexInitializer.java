package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;

@Configuration
@ConditionalOnProperty(prefix = "docpilot.workspace.mongo", name = "init-indexes", havingValue = "true", matchIfMissing = true)
public class WorkspaceMongoIndexInitializer {

    @Bean
    public ApplicationRunner workspaceMongoIndexes(MongoTemplate mongoTemplate) {
        return args -> {
            var workspaceIndexes = mongoTemplate.indexOps(Workspace.class);
            workspaceIndexes.getIndexInfo().stream()
                    .filter(index -> "ownerUserId_1_type_1".equals(index.getName()))
                    .findFirst()
                    .ifPresent(index -> workspaceIndexes.dropIndex(index.getName()));

            workspaceIndexes
                    .ensureIndex(new Index()
                            .named("active_personal_workspace_per_owner")
                            .on(WorkspaceMongoConstant.OWNER_USER_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.TYPE, Sort.Direction.ASC)
                            .unique()
                            .partial(PartialIndexFilter.of(Criteria.where(WorkspaceMongoConstant.IS_DELETED).is(false)
                                    .and(WorkspaceMongoConstant.TYPE).is(WorkspaceType.PERSONAL.getValue()))));

            mongoTemplate.indexOps(WorkspaceNode.class)
                    .ensureIndex(new Index()
                            .on(WorkspaceMongoConstant.WORKSPACE_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.PARENT_NODE_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.NAME, Sort.Direction.ASC)
                            .unique()
                            .partial(PartialIndexFilter.of(Criteria.where(WorkspaceMongoConstant.IS_DELETED).is(false))));
            mongoTemplate.indexOps(WorkspaceNode.class)
                    .ensureIndex(new Index()
                            .on(WorkspaceMongoConstant.WORKSPACE_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.ANCESTORS, Sort.Direction.ASC));

            mongoTemplate.indexOps(WorkspaceDocument.class)
                    .ensureIndex(new Index()
                            .on(WorkspaceMongoConstant.OWNER_USER_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.IS_DELETED, Sort.Direction.ASC));
            mongoTemplate.indexOps(WorkspaceDocument.class)
                    .ensureIndex(new Index()
                            .on(WorkspaceMongoConstant.ORIGIN_WORKSPACE_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.IS_DELETED, Sort.Direction.ASC));

            mongoTemplate.indexOps(DocumentRevision.class)
                    .ensureIndex(new Index()
                            .on(WorkspaceMongoConstant.DOCUMENT_ID, Sort.Direction.ASC)
                            .on(WorkspaceMongoConstant.VERSION, Sort.Direction.ASC)
                            .unique());
        };
    }

}
