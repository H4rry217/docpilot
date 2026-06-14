package io.docpilot.filesystem;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Capabilities are enforced at the mount edge, even if the target filesystem can do more.
 */
public record MountOptions(
        /**
         * Operations exposed by this mount to the composite filesystem caller.
         */
        Set<FilesystemCapability> capabilities
) {

    public MountOptions {
        EnumSet<FilesystemCapability> copy = EnumSet.noneOf(FilesystemCapability.class);

        if (capabilities != null) {
            copy.addAll(capabilities);
        }

        capabilities = Collections.unmodifiableSet(copy);
    }

    public static MountOptions readOnly() {
        return new MountOptions(EnumSet.of(
                FilesystemCapability.LIST,
                FilesystemCapability.READ,
                FilesystemCapability.STAT,
                FilesystemCapability.SEARCH,
                // Retrieval is read-only from the caller perspective, so default read-only mounts expose it.
                FilesystemCapability.RETRIEVE,
                FilesystemCapability.READ_URL
        ));
    }

    public static MountOptions of(FilesystemCapability first, FilesystemCapability... rest) {
        EnumSet<FilesystemCapability> capabilities = EnumSet.of(first, rest);
        return new MountOptions(capabilities);
    }

    public boolean allows(FilesystemCapability capability) {
        return capabilities.contains(capability);
    }

}
