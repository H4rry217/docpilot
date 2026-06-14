package io.docpilot.workspace.knowledge.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Heading section content used for summary generation.
 */
@Getter
@Setter
public class KnowledgeSectionDraft {

    /**
     * Heading block id that anchors the section summary back to the source document.
     */
    private String headingBlockId;

    /**
     * Heading labels from document root to the section being summarized.
     */
    private List<String> headingPath = new ArrayList<>();

    /**
     * Stable order for summary chunks generated from the same heading section.
     */
    private Integer chunkIndex = 0;

    /**
     * Rendered text collected from the section and selected child sections.
     */
    private String content;

    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

}
