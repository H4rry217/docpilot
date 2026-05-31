package io.docpilot.workspace.model.response;

import io.docpilot.block.model.BlockDocument;

public record DocumentContentResponse(
        String blockSchemaVersion,
        BlockDocument blockDocument,
        String markdownText,
        String checksum) {
}
