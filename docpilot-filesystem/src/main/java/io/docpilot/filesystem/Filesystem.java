package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Path-first filesystem boundary used by agents and higher-level services.
 *
 * <p>V1 is read-oriented. Mutating methods are present so writable filesystems can
 * opt in later, but the default behavior is to reject them explicitly.</p>
 */
public interface Filesystem {

    List<FileEntry> list(String path);

    byte[] read(String path);

    default String readText(String path) {
        return new String(read(path), StandardCharsets.UTF_8);
    }

    /**
     * Writable implementations should override this method.
     */
    default void write(String path, byte[] content) {
        throw new UnsupportedFilesystemOperationException("Filesystem does not support write");
    }

    default void writeText(String path, String content) {
        write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    default void delete(String path) {
        throw new UnsupportedFilesystemOperationException("Filesystem does not support delete");
    }

    boolean exists(String path);

    FileEntry stat(String path);

    default void copy(String sourcePath, String targetPath) {
        throw new UnsupportedFilesystemOperationException("Filesystem does not support copy");
    }

    default void move(String sourcePath, String targetPath) {
        throw new UnsupportedFilesystemOperationException("Filesystem does not support move");
    }

    List<FileEntry> glob(String pathPattern);

    List<GrepMatch> grep(String path, String text);

    default GrepResult grep(String path, String text, GrepOptions options) {
        return GrepResult.complete(grep(path, text));
    }

    default Optional<String> readUrl(String path) {
        return Optional.empty();
    }

}
