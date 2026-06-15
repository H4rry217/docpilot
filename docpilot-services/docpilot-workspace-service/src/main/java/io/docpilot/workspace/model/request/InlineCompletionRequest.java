package io.docpilot.workspace.model.request;

import java.util.List;

/**
 * Request body for streaming inline completion.
 *
 * @param workspaceId current workspace id.
 * @param documentId current document id.
 * @param cursor current ProseMirror cursor range.
 * @param currentBlock block that contains the cursor.
 * @param headingPath headings from document root to the cursor.
 * @param nearbyBlocks nearby unsaved document blocks supplied by the frontend.
 * @param trigger client-side trigger, such as IDLE or MANUAL.
 * @param clientVersion frontend client version.
 * @param candidateCount requested number of completion candidates.
 */
public record InlineCompletionRequest(
        String workspaceId,
        String documentId,
        CursorContext cursor,
        BlockContext currentBlock,
        List<String> headingPath,
        List<BlockContext> nearbyBlocks,
        String trigger,
        String clientVersion,
        Integer candidateCount
) {

    public InlineCompletionRequest {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        nearbyBlocks = nearbyBlocks == null ? List.of() : List.copyOf(nearbyBlocks);
    }

    public InlineCompletionRequest(String workspaceId,
                                   String documentId,
                                   CursorContext cursor,
                                   BlockContext currentBlock,
                                   List<String> headingPath,
                                   List<BlockContext> nearbyBlocks,
                                   String trigger,
                                   String clientVersion) {
        this(workspaceId, documentId, cursor, currentBlock, headingPath, nearbyBlocks, trigger, clientVersion, null);
    }

    public record CursorContext(Integer from, Integer to) {
    }

    public record BlockContext(
            String id,
            String type,
            String text,
            String textBeforeCursor,
            String textAfterCursor
    ) {
    }

}
