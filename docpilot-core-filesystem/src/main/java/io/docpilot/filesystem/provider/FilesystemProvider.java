package io.docpilot.filesystem.provider;

import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.GrepMatch;

import java.util.List;
import java.util.Optional;

public interface FilesystemProvider {

    String providerId();

    List<FileEntry> list(String path);

    byte[] read(String path);

    void write(String path, byte[] content);

    void delete(String path);

    boolean exists(String path);

    FileEntry stat(String path);

    default void copy(String sourcePath, String targetPath) {
        write(targetPath, read(sourcePath));
    }

    default void move(String sourcePath, String targetPath) {
        copy(sourcePath, targetPath);
        delete(sourcePath);
    }

    List<FileEntry> glob(String pathPattern);

    List<GrepMatch> grep(String path, String text);

    default Optional<String> readUrl(String path) {
        return Optional.empty();
    }

}
