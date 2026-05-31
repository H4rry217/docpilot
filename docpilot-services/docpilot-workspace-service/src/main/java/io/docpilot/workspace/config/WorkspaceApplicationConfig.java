package io.docpilot.workspace.config;

import io.docpilot.block.processing.MarkdownBlockParser;
import io.docpilot.block.processing.MarkdownBlockRenderer;
import io.docpilot.common.auth.AuthContextProvider;
import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.infrastructure.mongo.MongoWorkspaceTransactionRunner;
import io.docpilot.workspace.application.DocumentApplicationService;
import io.docpilot.workspace.application.WorkspaceApplicationService;
import io.docpilot.workspace.application.WorkspaceTransactionRunner;
import io.docpilot.workspace.processing.WorkspaceIdCodec;
import io.docpilot.workspace.processing.WorkspaceNodeName;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;

import java.time.Clock;

@Configuration
public class WorkspaceApplicationConfig {

    @Bean
    @ConditionalOnMissingBean
    public WorkspaceTransactionRunner workspaceTransactionRunner(MongoDatabaseFactory mongoDatabaseFactory) {
        MongoWorkspaceTransactionRunner transactionRunner = new MongoWorkspaceTransactionRunner();
        transactionRunner.setMongoDatabaseFactory(mongoDatabaseFactory);
        return transactionRunner;
    }

    @Bean
    public WorkspaceApplicationService workspaceApplicationService(WorkspaceRepository workspaceRepository,
                                                                   WorkspaceNodeRepository workspaceNodeRepository,
                                                                   WorkspaceDocumentRepository documentRepository,
                                                                   AuthContextProvider authContextProvider,
                                                                   SnowflakeIdGenerator idGenerator,
                                                                   WorkspaceIdCodec idCodec,
                                                                   WorkspaceNodeName workspaceNodeName,
                                                                   WorkspaceTransactionRunner transactionRunner) {
        WorkspaceApplicationService service = new WorkspaceApplicationService();
        service.setWorkspaceRepository(workspaceRepository);
        service.setNodeRepository(workspaceNodeRepository);
        service.setDocumentRepository(documentRepository);
        service.setAuthContextProvider(authContextProvider);
        service.setIdGenerator(idGenerator);
        service.setIdCodec(idCodec);
        service.setWorkspaceNodeName(workspaceNodeName);
        service.setTransactionRunner(transactionRunner);
        service.setClock(Clock.systemDefaultZone());
        return service;
    }

    @Bean
    public DocumentApplicationService documentApplicationService(WorkspaceApplicationService workspaceApplicationService,
                                                                 WorkspaceNodeRepository workspaceNodeRepository,
                                                                 WorkspaceDocumentRepository documentRepository,
                                                                 DocumentRevisionRepository revisionRepository,
                                                                 AuthContextProvider authContextProvider,
                                                                 SnowflakeIdGenerator idGenerator,
                                                                 WorkspaceIdCodec idCodec,
                                                                 WorkspaceNodeName workspaceNodeName,
                                                                 WorkspaceTransactionRunner transactionRunner) {
        DocumentApplicationService service = new DocumentApplicationService();
        service.setWorkspaceService(workspaceApplicationService);
        service.setNodeRepository(workspaceNodeRepository);
        service.setDocumentRepository(documentRepository);
        service.setRevisionRepository(revisionRepository);
        service.setAuthContextProvider(authContextProvider);
        service.setIdGenerator(idGenerator);
        service.setIdCodec(idCodec);
        service.setWorkspaceNodeName(workspaceNodeName);
        service.setTransactionRunner(transactionRunner);
        service.setMarkdownBlockParser(new MarkdownBlockParser());
        service.setMarkdownBlockRenderer(new MarkdownBlockRenderer());
        service.setClock(Clock.systemDefaultZone());
        return service;
    }

}
