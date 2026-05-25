package io.docpilot.filesystem.model;

import java.time.Instant;

public record FileEntry(
        String path,
        String name,
        FileEntryType type,
        long size,
        Instant lastModified
) {

    public boolean directory() {
        return type == FileEntryType.DIRECTORY;
    }

}
