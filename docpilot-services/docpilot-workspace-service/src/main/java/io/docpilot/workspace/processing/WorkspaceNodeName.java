package io.docpilot.workspace.processing;

import org.springframework.stereotype.Component;

@Component
public class WorkspaceNodeName {

    public String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        String normalized = name.strip();
        if (normalized.equals(".") || normalized.equals("..") || normalized.contains("/") || normalized.contains("\\")) {
            throw new IllegalArgumentException("name contains illegal path characters");
        }
        return normalized;
    }

}
