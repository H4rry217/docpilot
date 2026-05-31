package io.docpilot.workspace.model.request;

import io.docpilot.block.model.BlockDocument;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateDocumentCommand {

    /**
     * Workspace where the document node will be created.
     */
    private Long workspaceId;

    /**
     * Parent folder node id.
     */
    private Long parentNodeId;

    /**
     * Document title.
     */
    private String title;

    /**
     * Node name shown in the workspace tree.
     */
    private String nodeName;

    /**
     * Initial block document content.
     */
    private BlockDocument blockDocument;

    /**
     * Optional initial markdown content.
     */
    private String markdown;

}


