package io.docpilot.workspace.model.response;

import io.docpilot.block.prosemirror.ProseMirrorNode;

public record DocumentDetailResponse(DocumentResponse document, ProseMirrorNode prosemirror) {
}
