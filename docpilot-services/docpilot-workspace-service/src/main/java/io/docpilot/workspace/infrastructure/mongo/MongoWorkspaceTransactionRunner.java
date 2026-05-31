package io.docpilot.workspace.infrastructure.mongo;

import io.docpilot.workspace.application.WorkspaceTransactionRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

public class MongoWorkspaceTransactionRunner implements WorkspaceTransactionRunner {

    private TransactionTemplate transactionTemplate;

    public void setMongoDatabaseFactory(MongoDatabaseFactory mongoDatabaseFactory) {
        this.transactionTemplate = new TransactionTemplate(new MongoTransactionManager(mongoDatabaseFactory));
    }

    @Override
    public <T> T run(Supplier<T> supplier) {
        return transactionTemplate.execute(status -> supplier.get());
    }

}
