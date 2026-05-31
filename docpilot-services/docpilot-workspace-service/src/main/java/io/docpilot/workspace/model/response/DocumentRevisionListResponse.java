package io.docpilot.workspace.model.response;

import java.util.List;

public record DocumentRevisionListResponse(List<DocumentRevisionResponse> revisions) {
}
