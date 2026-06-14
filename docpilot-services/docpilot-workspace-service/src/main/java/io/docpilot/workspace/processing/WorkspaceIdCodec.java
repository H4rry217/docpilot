package io.docpilot.workspace.processing;

import org.springframework.stereotype.Component;

@Component
public class WorkspaceIdCodec {

    public long parseRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be a positive long id");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a positive long id", e);
        }
    }

    public Long parseOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequired(value, fieldName);
    }

    public String format(Long value) {
        return value == null ? null : Long.toString(value);
    }

}
