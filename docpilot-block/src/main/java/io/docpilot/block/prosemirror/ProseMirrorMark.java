package io.docpilot.block.prosemirror;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable ProseMirror mark DTO.
 */
@Getter
@Setter
public class ProseMirrorMark {

    /**
     * ProseMirror mark type.
     */
    private String type;

    /**
     * Mark-specific attributes.
     */
    private Map<String, Object> attrs = new HashMap<>();

    public static ProseMirrorMark of(String type) {
        ProseMirrorMark mark = new ProseMirrorMark();
        mark.setType(type);
        return mark;
    }

    public static ProseMirrorMark of(String type, Map<String, Object> attrs) {
        ProseMirrorMark mark = of(type);
        mark.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
        return mark;
    }

}
