package io.docpilot.workspace.enums;

import io.docpilot.common.enums.BaseEnum;

public enum WorkspaceType implements BaseEnum<Integer, WorkspaceType> {

    /**
     * Personal workspace owned by one user.
     */
    PERSONAL(1),

    /**
     * Additional user-created workspace.
     */
    CUSTOM(2);

    private final Integer value;

    WorkspaceType(Integer value) {
        this.value = value;
    }

    @Override
    public Integer getValue() {
        return value;
    }
}

