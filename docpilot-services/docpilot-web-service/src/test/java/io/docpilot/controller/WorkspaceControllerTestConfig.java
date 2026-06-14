package io.docpilot.controller;

import io.docpilot.common.id.SnowflakeIdGenerator;
import io.docpilot.workspace.application.NoopWorkspaceTransactionRunner;
import io.docpilot.workspace.application.WorkspaceTransactionRunner;
import io.docpilot.workspace.model.entity.DocumentRevision;
import io.docpilot.workspace.model.entity.Workspace;
import io.docpilot.workspace.model.entity.WorkspaceDocument;
import io.docpilot.workspace.model.entity.WorkspaceNode;
import io.docpilot.workspace.enums.WorkspaceType;
import io.docpilot.workspace.repository.DocumentRevisionRepository;
import io.docpilot.workspace.repository.WorkspaceDocumentRepository;
import io.docpilot.workspace.repository.WorkspaceNodeRepository;
import io.docpilot.workspace.repository.WorkspaceRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@TestConfiguration
class WorkspaceControllerTestConfig {

    @Bean
    @Primary
    WorkspaceTransactionRunner testWorkspaceTransactionRunner() {
        return new NoopWorkspaceTransactionRunner();
    }

    @Bean
    @Primary
    SnowflakeIdGenerator testSnowflakeIdGenerator() {
        return new SnowflakeIdGenerator(1, 1);
    }

    @Bean
    @Primary
    WorkspaceRepository testWorkspaceRepository() {
        return new InMemoryWorkspaceRepository();
    }

    @Bean
    @Primary
    WorkspaceNodeRepository testWorkspaceNodeRepository() {
        return new InMemoryWorkspaceNodeRepository();
    }

    @Bean
    @Primary
    WorkspaceDocumentRepository testWorkspaceDocumentRepository() {
        return new InMemoryWorkspaceDocumentRepository();
    }

    @Bean
    @Primary
    DocumentRevisionRepository testDocumentRevisionRepository() {
        return new InMemoryDocumentRevisionRepository();
    }

    private static class InMemoryWorkspaceRepository implements WorkspaceRepository {

        private final Map<Long, Workspace> workspaces = new LinkedHashMap<>();

        @Override
        public Workspace save(Workspace workspace) {
            workspaces.put(workspace.getId(), workspace);
            return workspace;
        }

        @Override
        public Optional<Workspace> findById(Long workspaceId) {
            return Optional.ofNullable(workspaces.get(workspaceId));
        }

        @Override
        public Optional<Workspace> findActivePersonalByOwnerUserId(Long ownerUserId) {
            return workspaces.values().stream()
                    .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                    .filter(workspace -> WorkspaceType.PERSONAL == workspace.getType())
                    .filter(workspace -> !Boolean.TRUE.equals(workspace.getIsDeleted()))
                    .findFirst();
        }

        @Override
        public List<Workspace> findActiveByOwnerUserId(Long ownerUserId) {
            return workspaces.values().stream()
                    .filter(workspace -> ownerUserId.equals(workspace.getOwnerUserId()))
                    .filter(workspace -> !Boolean.TRUE.equals(workspace.getIsDeleted()))
                    .toList();
        }
    }

    private static class InMemoryWorkspaceNodeRepository implements WorkspaceNodeRepository {

        private final Map<Long, WorkspaceNode> nodes = new LinkedHashMap<>();

        @Override
        public WorkspaceNode save(WorkspaceNode node) {
            nodes.put(node.getId(), node);
            return node;
        }

        @Override
        public List<WorkspaceNode> saveAll(Collection<WorkspaceNode> nodes) {
            nodes.forEach(this::save);
            return new ArrayList<>(nodes);
        }

        @Override
        public Optional<WorkspaceNode> findById(Long nodeId) {
            return Optional.ofNullable(nodes.get(nodeId));
        }

        @Override
        public List<WorkspaceNode> findActiveByWorkspaceId(Long workspaceId) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .toList();
        }

        @Override
        public Optional<WorkspaceNode> findActiveByWorkspaceIdAndParentNodeIdAndName(
                Long workspaceId,
                Long parentNodeId,
                String name
        ) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> parentNodeId.equals(node.getParentNodeId()))
                    .filter(node -> name.equals(node.getName()))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .findFirst();
        }

        @Override
        public List<WorkspaceNode> findActiveByWorkspaceIdAndAncestor(Long workspaceId, Long ancestorNodeId) {
            return nodes.values().stream()
                    .filter(node -> workspaceId.equals(node.getWorkspaceId()))
                    .filter(node -> node.getAncestors().contains(ancestorNodeId))
                    .filter(node -> !Boolean.TRUE.equals(node.getIsDeleted()))
                    .toList();
        }
    }

    private static class InMemoryWorkspaceDocumentRepository implements WorkspaceDocumentRepository {

        private final Map<Long, WorkspaceDocument> documents = new LinkedHashMap<>();

        @Override
        public WorkspaceDocument save(WorkspaceDocument document) {
            documents.put(document.getId(), document);
            return document;
        }

        @Override
        public Optional<WorkspaceDocument> findById(Long documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }
    }

    private static class InMemoryDocumentRevisionRepository implements DocumentRevisionRepository {

        private final Map<Long, DocumentRevision> revisions = new LinkedHashMap<>();

        @Override
        public DocumentRevision save(DocumentRevision revision) {
            revisions.put(revision.getId(), revision);
            return revision;
        }

        @Override
        public Optional<DocumentRevision> findById(Long revisionId) {
            return Optional.ofNullable(revisions.get(revisionId));
        }

        @Override
        public Optional<DocumentRevision> findByDocumentIdAndClientMutationId(Long documentId, String clientMutationId) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.getDocumentId()))
                    .filter(revision -> clientMutationId.equals(revision.getClientMutationId()))
                    .findFirst();
        }

        @Override
        public List<DocumentRevision> findByDocumentIdOrderByVersionDesc(Long documentId, int limit) {
            return revisions.values().stream()
                    .filter(revision -> documentId.equals(revision.getDocumentId()))
                    .sorted(Comparator.comparing(DocumentRevision::getVersion).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }
}

