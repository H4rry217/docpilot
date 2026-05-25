package io.docpilot.document.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Mutable command for creating a document.
 */
@Getter
@Setter
public class CreateDocumentCommand {

    /**
     * Initial document title.
     */
    private String title;

    /**
     * Initial Markdown source.
     */
    private String markdown = "";

    /**
     * Initial visibility policy.
     */
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;

}
