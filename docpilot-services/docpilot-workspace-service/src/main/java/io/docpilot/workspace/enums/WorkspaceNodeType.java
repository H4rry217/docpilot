package io.docpilot.workspace.enums;

import io.docpilot.common.enums.BaseEnum;

public enum WorkspaceNodeType implements BaseEnum<Integer, WorkspaceNodeType> {

    /**
     * Directory-like node.
     */
    FOLDER(1),

    /**
     * Node that points to a resource.
     */
    RESOURCE(2);

    private final Integer value;

    WorkspaceNodeType(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}


