package io.docpilot.workspace.application;

import io.docpilot.common.auth.AuthSubject;
import io.docpilot.workspace.event.DefaultWorkspaceInitializedEvent;
import io.docpilot.workspace.event.DocumentContentChangedEvent;
import io.docpilot.workspace.event.DocumentCreatedEvent;
import io.docpilot.workspace.event.DocumentDeletedEvent;
import io.docpilot.workspace.event.WorkspaceCreatedEvent;
import io.docpilot.workspace.event.WorkspaceDeletedEvent;
import io.docpilot.workspace.event.WorkspaceNodeCreatedEvent;
import io.docpilot.workspace.event.WorkspaceNodeDeletedEvent;
import io.docpilot.workspace.event.WorkspaceNodeMovedEvent;
import io.docpilot.workspace.event.WorkspaceNodeRenamedEvent;
import io.docpilot.workspace.event.WorkspaceRenamedEvent;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public final class WorkspaceDomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDomainEventPublisher.class);

    private final ApplicationEventPublisher publisher;

    public WorkspaceDomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void defaultWorkspaceInitialized(AuthSubject subject, Workspace workspace) {
        publish("default workspace initialized", new DefaultWorkspaceInitializedEvent(
                subject.getUserId(),
                subject.getDisplayName(),
                workspace.getId(),
                workspace.getRootNodeId()
        ), user(subject), workspace(workspace));
    }

    void workspaceCreated(AuthSubject subject, Workspace workspace) {
        publish("workspace created", new WorkspaceCreatedEvent(
                subject.getUserId(),
                workspace.getId(),
                workspace.getRootNodeId(),
                workspace.getType(),
                workspace.getName()
        ), user(subject), workspace(workspace));
    }

    void workspaceRenamed(AuthSubject subject, Workspace workspace, String oldName, String newName) {
        publish("workspace renamed", new WorkspaceRenamedEvent(
                subject.getUserId(),
                workspace.getId(),
                workspace.getRootNodeId(),
                oldName,
                newName
        ), user(subject), workspace(workspace));
    }

    void workspaceDeleted(AuthSubject subject, Workspace workspace) {
        publish("workspace deleted", new WorkspaceDeletedEvent(
                subject.getUserId(),
                workspace.getId(),
                workspace.getRootNodeId()
        ), user(subject), workspace(workspace));
    }

    void workspaceNodeCreated(AuthSubject subject, WorkspaceNode node) {
        publish("workspace node created", new WorkspaceNodeCreatedEvent(
                subject.getUserId(),
                node.getWorkspaceId(),
                node.getId(),
                node.getParentNodeId(),
                node.getNodeType(),
                node.getResourceType(),
                node.getDocumentId(),
                node.getName()
        ), user(subject), workspace(node.getWorkspaceId()), node(node));
    }

    void workspaceNodeRenamed(AuthSubject subject, WorkspaceNode node, String oldName, String newName) {
        publish("workspace node renamed", new WorkspaceNodeRenamedEvent(
                subject.getUserId(),
                node.getWorkspaceId(),
                node.getId(),
                node.getDocumentId(),
                oldName,
                newName
        ), user(subject), workspace(node.getWorkspaceId()), node(node));
    }

    void workspaceNodeMoved(AuthSubject subject,
                            WorkspaceNode node,
                            Long oldParentNodeId,
                            List<Long> oldAncestors) {
        publish("workspace node moved", new WorkspaceNodeMovedEvent(
                subject.getUserId(),
                node.getWorkspaceId(),
                node.getId(),
                node.getDocumentId(),
                oldParentNodeId,
                node.getParentNodeId(),
                oldAncestors,
                node.getAncestors()
        ), user(subject), workspace(node.getWorkspaceId()), node(node));
    }

    void workspaceNodeDeleted(AuthSubject subject, Long workspaceId, List<Long> nodeIds, List<Long> documentIds) {
        publish("workspace node deleted", new WorkspaceNodeDeletedEvent(
                subject.getUserId(),
                workspaceId,
                nodeIds,
                documentIds
        ), user(subject), workspace(workspaceId), field("nodes", size(nodeIds)), field("documents", size(documentIds)));
    }

    void documentCreated(AuthSubject subject,
                         Long workspaceId,
                         Long nodeId,
                         Long documentId,
                         Long revisionId,
                         Long version,
                         String title) {
        publish("document created", new DocumentCreatedEvent(
                subject.getUserId(),
                workspaceId,
                nodeId,
                documentId,
                revisionId,
                version,
                title
        ), user(subject), workspace(workspaceId), field("documentId", documentId), field("revisionId", revisionId));
    }

    void documentContentChanged(AuthSubject subject,
                                Long workspaceId,
                                Long documentId,
                                Long revisionId,
                                Long version,
                                Long baseVersion,
                                String clientMutationId) {
        publish("document content changed", new DocumentContentChangedEvent(
                subject.getUserId(),
                workspaceId,
                documentId,
                revisionId,
                version,
                baseVersion,
                clientMutationId
        ), user(subject), workspace(workspaceId), field("documentId", documentId), field("revisionId", revisionId));
    }

    void documentDeleted(AuthSubject subject, Long workspaceId, Long documentId, Long nodeId) {
        publish("document deleted", new DocumentDeletedEvent(
                subject.getUserId(),
                workspaceId,
                documentId,
                nodeId
        ), user(subject), workspace(workspaceId), field("documentId", documentId));
    }

    private void publish(String eventName, Object event, LogField... fields) {
        String details = details(fields);
        if (publisher == null) {
            log.warn("{} event skipped reason=no_event_publisher {}", eventName, details);
            return;
        }
        publisher.publishEvent(event);
        log.info("{} event published {}", eventName, details);
    }

    private static LogField user(AuthSubject subject) {
        return field("userId", subject.getUserId());
    }

    private static LogField workspace(Workspace workspace) {
        return workspace(workspace.getId());
    }

    private static LogField workspace(Long workspaceId) {
        return field("workspaceId", workspaceId);
    }

    private static LogField node(WorkspaceNode node) {
        return field("nodeId", node.getId());
    }

    private static LogField field(String name, Object value) {
        return new LogField(name, value);
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static String details(LogField... fields) {
        StringJoiner joiner = new StringJoiner(" ");
        for (LogField field : fields) {
            joiner.add(field.name() + "=" + field.value());
        }
        return joiner.toString();
    }

    private record LogField(String name, Object value) {
    }

}
