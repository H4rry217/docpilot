package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.retrieval.FilesystemRetrieval;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalHit;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalOptions;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalRequest;
import io.docpilot.filesystem.retrieval.FilesystemRetrievalResult;
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

    @Test
    void grepWalksFilesAcrossNestedAndSyntheticMounts() {
        Filesystem workspaceRoots = new CompositeFilesystem()
                .mount("/workspace/ws1", new MemoryFilesystem(Map.of("/docs/a.md", "one needle")))
                .mount("/workspace/ws2", new MemoryFilesystem(Map.of("/notes/b.md", "two needle")));

        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", workspaceRoots);

        GrepResult result = filesystem.grep("/project/workspace", "needle", GrepOptions.unlimited());

        assertThat(result.matches())
                .extracting(GrepMatch::path)
                .containsExactly("/project/workspace/ws1/docs/a.md", "/project/workspace/ws2/notes/b.md");
        assertThat(result.truncated()).isFalse();
        assertThat(result.searchedMounts()).isEqualTo(2);
        assertThat(result.searchedFiles()).isEqualTo(2);
    }

    @Test
    void grepIncludesSiblingMountsUnderResolvedCompositePath() {
        Filesystem workspaceRoots = new CompositeFilesystem()
                .mount("/workspace/ws1", new MemoryFilesystem(Map.of("/docs/a.md", "workspace needle")));

        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", workspaceRoots)
                .mount("/project/s3", new MemoryFilesystem(Map.of("/hello.txt", "s3 needle")));

        assertThat(filesystem.grep("/project", "needle"))
                .extracting(GrepMatch::path)
                .containsExactlyInAnyOrder(
                        "/project/workspace/ws1/docs/a.md",
                        "/project/s3/hello.txt"
                );
    }

    @Test
    void grepFiltersParentMountMatchesHiddenByMoreSpecificMounts() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", new MemoryFilesystem(Map.of(
                        "/a.txt", "base needle",
                        "/s3/hidden.txt", "hidden needle"
                )))
                .mount("/project/s3", new MemoryFilesystem(Map.of("/visible.txt", "s3 needle")));

        assertThat(filesystem.grep("/project", "needle"))
                .extracting(GrepMatch::path)
                .containsExactlyInAnyOrder("/project/a.txt", "/project/s3/visible.txt");
    }

    @Test
    void grepReportsTruncationWhenFileLimitIsReached() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", new MemoryFilesystem(Map.of(
                        "/a.md", "one needle",
                        "/b.md", "two needle"
                )));

        GrepResult result = filesystem.grep("/project", "needle", new GrepOptions(1, null));

        assertThat(result.matches())
                .extracting(GrepMatch::path)
                .containsExactly("/project/a.md");
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo(GrepResult.TRUNCATED_BY_MAX_FILES);
        assertThat(result.searchedFiles()).isEqualTo(1);
    }

    @Test
    void grepReportsTruncationWhenMatchLimitIsReached() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", new MemoryFilesystem(Map.of(
                        "/a.md", "one needle",
                        "/b.md", "two needle"
                )));

        GrepResult result = filesystem.grep("/project", "needle", new GrepOptions(null, 1));

        assertThat(result.matches())
                .extracting(GrepMatch::path)
                .containsExactly("/project/a.md");
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo(GrepResult.TRUNCATED_BY_MAX_MATCHES);
    }

    @Test
    void grepRejectsSyntheticSearchWhenDescendantMountDoesNotAllowSearch() {
        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project/workspace/ws1",
                        new MemoryFilesystem(Map.of("/a.md", "needle")),
                        "/",
                        MountOptions.of(FilesystemCapability.LIST, FilesystemCapability.STAT));

        assertThatThrownBy(() -> filesystem.grep("/project/workspace", "needle"))
                .isInstanceOf(UnsupportedFilesystemOperationException.class);
    }

    @Test
    void retrieveWalksAcrossSyntheticMountsAndMapsPaths() {
        Filesystem workspaceRoots = new CompositeFilesystem()
                .mount("/workspace/ws1", new MemoryFilesystem(Map.of("/docs/a.md", "one needle")))
                .mount("/workspace/ws2", new MemoryFilesystem(Map.of("/notes/b.md", "two needle")));

        CompositeFilesystem filesystem = new CompositeFilesystem()
                .mount("/project", workspaceRoots);

        FilesystemRetrievalResult result = filesystem.retrieve(new FilesystemRetrievalRequest(
                "/project/workspace",
                "needle",
                new FilesystemRetrievalOptions(1, 100)
        ));

        assertThat(result.hits())
                .extracting(FilesystemRetrievalHit::path)
                .containsExactly("/project/workspace/ws2/notes/b.md");
        assertThat(result.truncated()).isTrue();
        assertThat(result.truncationReason()).isEqualTo(FilesystemRetrievalResult.TRUNCATED_BY_TOP_K);
        assertThat(result.searchedMounts()).isEqualTo(2);
    }

    private static class MemoryFilesystem implements Filesystem, FilesystemRetrieval {

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
            return grep(path, text, GrepOptions.unlimited()).matches();
        }

        @Override
        public GrepResult grep(String path, String text, GrepOptions options) {
            GrepOptions grepOptions = GrepOptions.effective(options);
            String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
            List<GrepMatch> matches = new java.util.ArrayList<>();
            long searchedFiles = 0L;
            String truncationReason = null;

            for (Map.Entry<String, String> entry : files.entrySet().stream()
                    .filter(entry -> FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                if (grepOptions.isMaxFilesReached(searchedFiles)) {
                    truncationReason = GrepResult.TRUNCATED_BY_MAX_FILES;
                    break;
                }
                if (grepOptions.isMaxMatchesReached(matches.size())) {
                    truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                    break;
                }

                searchedFiles++;
                String[] lines = entry.getValue().split("\\R", -1);
                for (int index = 0; index < lines.length; index++) {
                    if (lines[index].contains(text)) {
                        matches.add(new GrepMatch(entry.getKey(), index + 1L, lines[index]));
                        if (grepOptions.isMaxMatchesReached(matches.size())) {
                            truncationReason = GrepResult.TRUNCATED_BY_MAX_MATCHES;
                            break;
                        }
                    }
                }
                if (truncationReason != null) {
                    break;
                }
            }

            return new GrepResult(matches, truncationReason != null, truncationReason, 0L, searchedFiles);
        }

        @Override
        public FilesystemRetrievalResult retrieve(FilesystemRetrievalRequest request) {
            FilesystemRetrievalRequest retrievalRequest = request == null
                    ? new FilesystemRetrievalRequest("/", "", FilesystemRetrievalOptions.defaults())
                    : request;
            String normalizedPath = FilesystemPath.normalizeVirtualPath(retrievalRequest.path());
            if (retrievalRequest.query().isBlank()) {
                return FilesystemRetrievalResult.complete(List.of());
            }

            List<FilesystemRetrievalHit> hits = files.entrySet().stream()
                    .filter(entry -> FilesystemPath.isSameOrDescendant(normalizedPath, entry.getKey()))
                    .filter(entry -> entry.getValue().contains(retrievalRequest.query()))
                    .map(entry -> new FilesystemRetrievalHit(
                            entry.getKey(),
                            FilesystemPath.nameOf(entry.getKey()),
                            entry.getValue(),
                            entry.getValue().contains("two") ? 2.0D : 1.0D,
                            List.of(),
                            Map.of()
                    ))
                    .toList();
            return FilesystemRetrievalResult.complete(hits);
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
