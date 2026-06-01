package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositeFilesystemTest {

    @Test
    void resolvesUsingLongestMountPrefix() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", new MemoryFilesystem(Map.of("/tmp/a.txt", "base")))
                .mount("/project/tmp", new MemoryFilesystem(Map.of("/a.txt", "specific")));

        assertThat(filesystem.readText("/project/tmp/a.txt")).isEqualTo("specific");
    }

    @Test
    void listsSyntheticDirectoriesFromMountAncestors() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project/workspace/ws1", new MemoryFilesystem(Map.of("/a.md", "one")))
                .mount("/project/workspace/ws2", new MemoryFilesystem(Map.of("/b.md", "two")));

        assertThat(filesystem.list("/project/workspace"))
                .extracting(FileEntry::path)
                .containsExactly("/project/workspace/ws1", "/project/workspace/ws2");
        assertThat(filesystem.stat("/project/workspace").directory()).isTrue();
        assertThat(filesystem.exists("/project/workspace")).isTrue();
    }

    @Test
    void rejectsUnsupportedWritesWhenMountIsReadOnly() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", new MemoryFilesystem(Map.of("/a.txt", "hello")));

        assertThatThrownBy(() -> filesystem.writeText("/project/a.txt", "no"))
                .isInstanceOf(UnsupportedFilesystemOperationException.class);
    }

    @Test
    void rejectsMissingMountsAndMountCycles() {
        CompositeFilesystem filesystem = new CompositeFilesystem();
        assertThatThrownBy(() -> filesystem.readText("/missing/a.txt"))
                .isInstanceOf(FileNotFoundException.class);

        CompositeFilesystem child = new CompositeFilesystem();
        filesystem.mount("/project", child);
        assertThatThrownBy(() -> child.mount("/loop", filesystem))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void globWalksFilesAcrossNestedAndSyntheticMounts() {
        Filesystem workspaceRoots = new CompositeFilesystem()
                .mount("/workspace/ws1", new MemoryFilesystem(Map.of("/docs/a.md", "one")))
                .mount("/workspace/ws2", new MemoryFilesystem(Map.of("/notes/b.txt", "two")));

        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", workspaceRoots)
                .mount("/project/s3", new MemoryFilesystem(Map.of("/hello.txt", "s3")));

        assertThat(filesystem.glob("/project/**/*"))
                .extracting(FileEntry::path)
                .containsExactly(
                        "/project/s3/hello.txt",
                        "/project/workspace/ws1/docs/a.md",
                        "/project/workspace/ws2/notes/b.txt"
                );
        assertThat(filesystem.glob("/project/**/*.md"))
                .extracting(FileEntry::path)
                .containsExactly("/project/workspace/ws1/docs/a.md");
    }

    private static class MemoryFilesystem implements Filesystem {

        private final Map<String, String> files = new HashMap<>();

        MemoryFilesystem(Map<String, String> files) {
            files.forEach((path, content) -> this.files.put(FilesystemPath.normalizeVirtualPath(path), content));
        }

        @Override
        public List<FileEntry> list(String path) {
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
            Map<String, FileEntry> entries = new LinkedHashMap<>();

            for (String filePath : files.keySet()) {
                if (parentOf(filePath).equals(normalizedPath)) {
                    entries.put(filePath, fileEntry(filePath));
                    continue;
                }

                if (FilesystemPath.isStrictAncestor(normalizedPath, filePath)) {
                    String relativePath = FilesystemPath.relativeVirtualPath(normalizedPath, filePath);
                    String childName = relativePath.split("/", 2)[0];
                    String childPath = FilesystemPath.joinVirtualPath(normalizedPath, childName);
                    entries.putIfAbsent(childPath, directoryEntry(childPath));
                }
            }

            return entries.values().stream()
                    .sorted(Comparator.comparing(FileEntry::path))
                    .toList();
        }

        @Override
        public byte[] read(String path) {
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
            String content = files.get(normalizedPath);
            if (content == null) {
                throw new FileNotFoundException("Path not found: " + normalizedPath);
            }
            return content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean exists(String path) {
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
            return files.containsKey(normalizedPath)
                    || files.keySet().stream().anyMatch(filePath -> FilesystemPath.isStrictAncestor(normalizedPath, filePath));
        }

        @Override
        public FileEntry stat(String path) {
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);

            if (files.containsKey(normalizedPath)) {
                return fileEntry(normalizedPath);
            }

            if (files.keySet().stream().anyMatch(filePath -> FilesystemPath.isStrictAncestor(normalizedPath, filePath))) {
                return directoryEntry(normalizedPath);
            }

            throw new FileNotFoundException("Path not found: " + normalizedPath);
        }

        @Override
        public List<FileEntry> glob(String pathPattern) {
            return files.keySet().stream()
                    .filter(path -> io.docpilot.filesystem.path.GlobMatcher.matches(pathPattern, path))
                    .map(this::stat)
                    .toList();
        }

        @Override
        public List<GrepMatch> grep(String path, String text) {
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
            return files.entrySet().stream()
                    .filter(entry -> FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                    .filter(entry -> entry.getValue().contains(text))
                    .map(entry -> new GrepMatch(entry.getKey(), 1L, entry.getValue()))
                    .toList();
        }

        private String parentOf(String path) {
            int index = path.lastIndexOf('/');
            return index <= 0 ? "/" : path.substring(0, index);
        }

        private FileEntry fileEntry(String path) {
            return new FileEntry(path, FilesystemPath.nameOf(path), FileEntryType.FILE,
                    files.get(path).getBytes(StandardCharsets.UTF_8).length, null);
        }

        private FileEntry directoryEntry(String path) {
            return new FileEntry(path, FilesystemPath.nameOf(path), FileEntryType.DIRECTORY, 0L, null);
        }
    }

}
