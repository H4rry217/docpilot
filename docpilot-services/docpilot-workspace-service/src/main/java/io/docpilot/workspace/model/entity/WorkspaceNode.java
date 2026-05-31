package io.docpilot.workspace.model.entity;

import io.docpilot.common.domain.BaseEntity;
import io.docpilot.workspace.constant.WorkspaceMongoConstant;
import io.docpilot.workspace.enums.WorkspaceNodeType;
import io.docpilot.workspace.enums.WorkspaceResourceType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Document(collection = WorkspaceMongoConstant.WORKSPACE_NODE_COLLECTION)
public class WorkspaceNode extends BaseEntity {

    /**
     * Parent id used by the root node.
     */
    public static final long ROOT_PARENT_NODE_ID = 0L;

    /**
     * Workspace that owns this node.
     */
    private Long workspaceId;

    /**
     * Direct parent node id.
     */
    private Long parentNodeId;

    /**
     * Ancestor node ids from root to parent.
     */
    private List<Long> ancestors = new ArrayList<>();

    /**
     * Node category, such as folder or resource.
     */
    private WorkspaceNodeType nodeType;

    /**
     * Resource type when nodeType is RESOURCE.
     */
    private WorkspaceResourceType resourceType;

    /**
     * Linked document id for document resources.
     */
    private Long documentId;

    /**
     * External storage metadata for non-document resources.
     */
    private Map<String, Object> storage;

    /**
     * Display name in the parent folder.
     */
    private String name;

    /**
     * MIME type for resource nodes.
     */
    private String mimeType;

    /**
     * Resource size in bytes.
     */
    private Long size;

    /**
     * Content checksum.
     */
    private String checksum;

    /**
     * User-defined node metadata.
     */
    private Map<String, Object> metadata = new HashMap<>();

    public boolean isFolder() {
        return nodeType == WorkspaceNodeType.FOLDER;
    }

    public boolean isDocumentResource() {
        return nodeType == WorkspaceNodeType.RESOURCE && resourceType == WorkspaceResourceType.DOCUMENT && documentId != null;
    }

}
