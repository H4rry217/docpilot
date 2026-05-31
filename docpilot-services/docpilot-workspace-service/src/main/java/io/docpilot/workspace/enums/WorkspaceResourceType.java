package io.docpilot.workspace.enums;

import io.docpilot.common.enums.BaseEnum;

public enum WorkspaceResourceType implements BaseEnum<Integer, WorkspaceResourceType> {

    /**
     * Editable document resource.
     */
    DOCUMENT(1),

    /**
     * Image resource.
     */
    IMAGE(2),

    /**
     * Generic file resource.
     */
    FILE(3),

    /**
     * External link resource.
     */
    LINK(4);

    private final Integer value;

    WorkspaceResourceType(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}


