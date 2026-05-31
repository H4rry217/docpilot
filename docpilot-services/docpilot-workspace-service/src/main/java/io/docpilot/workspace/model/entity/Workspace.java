package io.docpilot.workspace.model.entity;

import io.docpilot.common.domain.BaseEntity;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.enums.WorkspaceType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Document(collection = WorkspaceMongoConstant.WORKSPACE_COLLECTION)
public class Workspace extends BaseEntity {

    /**
     * Display name of the workspace.
     */
    private String name;

    /**
     * Workspace category, such as personal workspace.
     */
    private WorkspaceType type;

    /**
     * Owner user id.
     */
    private Long ownerUserId;

    /**
     * Root folder node id.
     */
    private Long rootNodeId;

    /**
     * User-defined workspace settings.
     */
    private Map<String, Object> settings = new HashMap<>();

}
