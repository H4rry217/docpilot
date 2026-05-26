package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable command for replacing document content.
 */
@Getter
@Setter
public class UpdateDocumentContentCommand {

    /**
     * Full replacement Markdown source.
     */
    private String markdown = "";

    /**
     * Expected document version. Null means skip optimistic version check.
     */
    private Long expectedVersion;

}
