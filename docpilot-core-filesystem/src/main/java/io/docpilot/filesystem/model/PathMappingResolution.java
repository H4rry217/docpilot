package io.docpilot.filesystem.model;

public record PathMappingResolution(
        PathMapping mapping,
        String virtualPath,
        String relativePath,
        String providerPath
) {
}
