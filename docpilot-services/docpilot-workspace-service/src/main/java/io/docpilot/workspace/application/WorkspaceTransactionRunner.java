package io.docpilot.workspace.application;

import java.util.function.Supplier;

public interface WorkspaceTransactionRunner {

    <T> T run(Supplier<T> supplier);

}
