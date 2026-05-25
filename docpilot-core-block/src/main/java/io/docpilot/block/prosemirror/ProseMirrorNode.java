package io.docpilot.block.prosemirror;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable ProseMirror node DTO used as the backend JSON contract.
 */
@Getter
@Setter
public class ProseMirrorNode {

    /**
     * ProseMirror node type.
     */
    private String type;

    /**
     * Node attributes.
     */
    private Map<String, Object> attrs = new HashMap<>();

    /**
     * Child nodes.
     */
    private List<ProseMirrorNode> content = new ArrayList<>();

    /**
     * Text payload for text nodes.
     */
    private String text;

    /**
     * Marks applied to text nodes.
     */
    private List<ProseMirrorMark> marks = new ArrayList<>();

    public static ProseMirrorNode node(String type, Map<String, Object> attrs, List<ProseMirrorNode> content) {
        ProseMirrorNode node = new ProseMirrorNode();
        node.setType(type);
        node.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
        node.setContent(content == null ? new ArrayList<>() : new ArrayList<>(content));
        return node;
    }

    public static ProseMirrorNode leaf(String type, Map<String, Object> attrs) {
        return node(type, attrs, List.of());
    }

    public static ProseMirrorNode text(String text, List<ProseMirrorMark> marks) {
        ProseMirrorNode node = new ProseMirrorNode();
        node.setType("text");
        node.setText(text);
        node.setMarks(marks == null ? new ArrayList<>() : new ArrayList<>(marks));
        return node;
    }

}
