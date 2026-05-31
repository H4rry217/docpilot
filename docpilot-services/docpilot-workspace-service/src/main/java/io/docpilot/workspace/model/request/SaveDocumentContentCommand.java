package io.docpilot.workspace.model.request;

import io.docpilot.block.model.BlockDocument;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDocumentContentCommand {

    /**
     * Document to save.
     */
    private Long documentId;

    /**
     * Client base version for optimistic locking.
     */
    private Long baseVersion;

    /**
     * New block document content.
     */
    private BlockDocument blockDocument;

    /**
     * Client mutation id for tracing duplicate saves.
     */
    private String clientMutationId;

}


