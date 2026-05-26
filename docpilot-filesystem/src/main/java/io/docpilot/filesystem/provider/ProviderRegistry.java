package io.docpilot.filesystem.provider;

import java.util.Optional;

public interface ProviderRegistry {

    void register(FilesystemProvider provider);

    Optional<FilesystemProvider> findById(String providerId);

    default FilesystemProvider requireById(String providerId) {
        return findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Filesystem provider not found: " + providerId));
    }

}
