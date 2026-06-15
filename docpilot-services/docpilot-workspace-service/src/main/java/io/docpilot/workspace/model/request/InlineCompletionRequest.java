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
 */
public record InlineCompletionRequest(
        String workspaceId,
        String documentId,
        CursorContext cursor,
        BlockContext currentBlock,
        List<String> headingPath,
        List<BlockContext> nearbyBlocks,
        String trigger,
        String clientVersion
) {

    public InlineCompletionRequest {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        nearbyBlocks = nearbyBlocks == null ? List.of() : List.copyOf(nearbyBlocks);
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
