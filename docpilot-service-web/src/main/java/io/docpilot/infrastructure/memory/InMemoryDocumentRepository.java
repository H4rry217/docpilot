package io.docpilot.infrastructure.memory;

import io.docpilot.document.model.DocPilotDocument;
import io.docpilot.document.repository.DocumentRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryDocumentRepository implements DocumentRepository {

    private final ConcurrentMap<String, DocPilotDocument> documents = new ConcurrentHashMap<>();

    @Override
    public DocPilotDocument save(DocPilotDocument document) {
        documents.put(document.getDocumentId(), document);
        return document;
    }

    @Override
    public Optional<DocPilotDocument> findById(String documentId) {
        return Optional.ofNullable(documents.get(documentId));
    }

}
