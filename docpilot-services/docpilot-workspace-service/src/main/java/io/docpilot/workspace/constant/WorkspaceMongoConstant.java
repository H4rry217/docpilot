package io.docpilot.workspace.constant;

/**
 * Mongo collection and field names used by the workspace module.
 */
public final class WorkspaceMongoConstant {

    private WorkspaceMongoConstant() {
    }

    /**
     * Workspace collection.
     */
    public static final String WORKSPACE_COLLECTION = "dp_workspace";

    /**
     * Workspace node collection.
     */
    public static final String WORKSPACE_NODE_COLLECTION = "dp_workspace_node";

    /**
     * Document collection.
     */
    public static final String DOCUMENT_COLLECTION = "dp_document";

    /**
     * Document revision collection.
     */
    public static final String DOCUMENT_REVISION_COLLECTION = "dp_document_revision";

    /**
     * Base entity created time field.
     */
    public static final String CREATE_TIME = "createTime";

    /**
     * Base entity soft delete field.
     */
    public static final String IS_DELETED = "isDeleted";

    /**
     * Workspace owner user id field.
     */
    public static final String OWNER_USER_ID = "ownerUserId";

    /**
     * Workspace type field.
     */
    public static final String TYPE = "type";

    /**
     * Workspace id field on child entities.
     */
    public static final String WORKSPACE_ID = "workspaceId";

    /**
     * Parent node id field.
     */
    public static final String PARENT_NODE_ID = "parentNodeId";

    /**
     * Ancestor node id list field.
     */
    public static final String ANCESTORS = "ancestors";

    /**
     * Node type field.
     */
    public static final String NODE_TYPE = "nodeType";

    /**
     * Sibling display name field.
     */
    public static final String NAME = "name";

    /**
     * Document origin workspace id field.
     */
    public static final String ORIGIN_WORKSPACE_ID = "originWorkspaceId";

    /**
     * Document id field.
     */
    public static final String DOCUMENT_ID = "documentId";

    /**
     * Document revision version field.
     */
    public static final String VERSION = "version";

}
