package io.docpilot.workspace.application;

import java.util.function.Supplier;

public class NoopWorkspaceTransactionRunner implements WorkspaceTransactionRunner {

    @Override
    public <T> T run(Supplier<T> supplier) {
        return supplier.get();
    }

}
