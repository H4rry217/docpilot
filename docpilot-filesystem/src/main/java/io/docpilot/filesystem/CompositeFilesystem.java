package io.docpilot.filesystem;

import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.exception.UnsupportedFilesystemOperationException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.model.GrepOptions;
import io.docpilot.filesystem.model.GrepResult;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.path.GlobMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Filesystem implementation that delegates paths to mounted child filesystems.
 */
public class CompositeFilesystem implements Filesystem {

    private static final Logger log = LoggerFactory.getLogger(CompositeFilesystem.class);

    private final List<MountedFilesystem> mounts = new CopyOnWriteArrayList<>();

    public CompositeFilesystem mount(String mountPath, Filesystem filesystem) {
        return mount(mountPath, filesystem, FilesystemPathNames.ROOT, MountOptions.readOnly());
    }

    public CompositeFilesystem mount(String mountPath, Filesystem filesystem, String targetRoot, MountOptions options) {
        MountedFilesystem mounted = new MountedFilesystem(mountPath, filesystem, targetRoot, options);

        if (mounts.stream().anyMatch(existing -> existing.mountPath().equals(mounted.mountPath()))) {
            throw new IllegalArgumentException("Filesystem mount already exists: " + mounted.mountPath());
        }

        // Prevent recursive mount graphs such as A -> B -> A.
        if (filesystem == this || filesystem instanceof CompositeFilesystem composite
                && composite.containsFilesystem(this, Collections.newSetFromMap(new IdentityHashMap<>()))) {
            throw new IllegalArgumentException("Filesystem mount would create a cycle: " + mounted.mountPath());
        }

        mounts.add(mounted);
        log.debug("composite filesystem mount added mountPath={} targetRoot={} capabilities={}",
                mounted.mountPath(), mounted.targetRoot(), mounted.options().capabilities());
        return this;
    }

    public List<MountedFilesystem> mounts() {
        return List.copyOf(mounts);
    }

    @Override
    public List<FileEntry> list(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        log.debug("composite filesystem list start path={} normalizedPath={}", path, normalizedPath);
        Map<String, FileEntry> entries = new LinkedHashMap<>();
        boolean resolved = false;

        Optional<MountedFilesystem> mounted = resolve(normalizedPath);
        if (mounted.isPresent()) {
            MountedFilesystem filesystem = mounted.get();
            require(filesystem, FilesystemCapability.LIST);

            try {
                filesystem.filesystem().list(filesystem.toTargetPath(normalizedPath)).stream()
                        .map(filesystem::toMountEntry)
                        .forEach(entry -> entries.put(entry.path(), entry));
                resolved = true;
            } catch (FileNotFoundException exception) {
                // A concrete mount may not contain the path, but child mounts can still create it synthetically.
                if (syntheticChildren(normalizedPath).isEmpty()) {
                    throw exception;
                }
            }
        }

        // Mount ancestors are visible as directories even when no child filesystem owns that exact path.
        syntheticChildren(normalizedPath).forEach(entry -> entries.putIfAbsent(entry.path(), entry));

        if (!resolved && entries.isEmpty()) {
            throw new FileNotFoundException("Path not found: " + normalizedPath);
        }

        List<FileEntry> result = entries.values().stream()
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
        log.debug("composite filesystem list done path={} normalizedPath={} entries={} resolved={}",
                path, normalizedPath, result.size(), resolved);
        return result;
    }

    @Override
    public byte[] read(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.READ);
        String targetPath = mounted.toTargetPath(normalizedPath);
        log.debug("composite filesystem read start path={} normalizedPath={} mountPath={} targetPath={}",
                path, normalizedPath, mounted.mountPath(), targetPath);
        byte[] content = mounted.filesystem().read(targetPath);
        log.debug("composite filesystem read done path={} normalizedPath={} mountPath={} targetPath={} bytes={}",
                path, normalizedPath, mounted.mountPath(), targetPath, content.length);
        return content;
    }

    @Override
    public void write(String path, byte[] content) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.WRITE);
        String targetPath = mounted.toTargetPath(normalizedPath);
        int bytes = content == null ? 0 : content.length;
        log.debug("composite filesystem write start path={} normalizedPath={} mountPath={} targetPath={} bytes={}",
                path, normalizedPath, mounted.mountPath(), targetPath, bytes);
        mounted.filesystem().write(targetPath, content);
        log.debug("composite filesystem write done path={} normalizedPath={} mountPath={} targetPath={} bytes={}",
                path, normalizedPath, mounted.mountPath(), targetPath, bytes);
    }

    @Override
    public void delete(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);
        require(mounted, FilesystemCapability.DELETE);
        String targetPath = mounted.toTargetPath(normalizedPath);
        log.debug("composite filesystem delete start path={} normalizedPath={} mountPath={} targetPath={}",
                path, normalizedPath, mounted.mountPath(), targetPath);
        mounted.filesystem().delete(targetPath);
        log.debug("composite filesystem delete done path={} normalizedPath={} mountPath={} targetPath={}",
                path, normalizedPath, mounted.mountPath(), targetPath);
    }

    @Override
    public boolean exists(String path) {
        try {
            stat(path);
            return true;
        } catch (FileNotFoundException exception) {
            return false;
        }
    }

    @Override
    public FileEntry stat(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        log.debug("composite filesystem stat start path={} normalizedPath={}", path, normalizedPath);
        Optional<MountedFilesystem> mounted = resolve(normalizedPath);

        if (mounted.isPresent()) {
            MountedFilesystem filesystem = mounted.get();
            require(filesystem, FilesystemCapability.STAT);
            String targetPath = filesystem.toTargetPath(normalizedPath);

            try {
                FileEntry entry = filesystem.toMountEntry(filesystem.filesystem().stat(targetPath));
                log.debug("composite filesystem stat done path={} normalizedPath={} mountPath={} targetPath={} type={} size={}",
                        path, normalizedPath, filesystem.mountPath(), targetPath, entry.type(), entry.size());
                return entry;
            } catch (FileNotFoundException exception) {
                if (!syntheticChildren(normalizedPath).isEmpty()) {
                    FileEntry entry = syntheticDirectory(normalizedPath);
                    log.debug("composite filesystem stat done path={} normalizedPath={} synthetic=true type={}",
                            path, normalizedPath, entry.type());
                    return entry;
                }
                throw exception;
            }
        }

        if (!syntheticChildren(normalizedPath).isEmpty()) {
            FileEntry entry = syntheticDirectory(normalizedPath);
            log.debug("composite filesystem stat done path={} normalizedPath={} synthetic=true type={}",
                    path, normalizedPath, entry.type());
            return entry;
        }

        throw new FileNotFoundException("Path not found: " + normalizedPath);
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        String normalizedSourcePath = FilesystemPath.normalizeVirtualPath(sourcePath);
        String normalizedTargetPath = FilesystemPath.normalizeVirtualPath(targetPath);
        MountedFilesystem source = requireResolved(normalizedSourcePath);
        MountedFilesystem target = requireResolved(normalizedTargetPath);

        require(source, FilesystemCapability.READ);
        require(target, FilesystemCapability.WRITE);
        String sourceTargetPath = source.toTargetPath(normalizedSourcePath);
        String targetTargetPath = target.toTargetPath(normalizedTargetPath);
        log.debug("composite filesystem copy start sourcePath={} targetPath={} sourceMountPath={} targetMountPath={} sourceTargetPath={} targetTargetPath={}",
                sourcePath, targetPath, source.mountPath(), target.mountPath(), sourceTargetPath, targetTargetPath);

        if (source.filesystem() == target.filesystem()) {
            source.filesystem().copy(sourceTargetPath, targetTargetPath);
            log.debug("composite filesystem copy done sourcePath={} targetPath={} sameFilesystem=true",
                    sourcePath, targetPath);
            return;
        }

        byte[] content = source.filesystem().read(sourceTargetPath);
        target.filesystem().write(targetTargetPath, content);
        log.debug("composite filesystem copy done sourcePath={} targetPath={} sameFilesystem=false bytes={}",
                sourcePath, targetPath, content.length);
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        String normalizedSourcePath = FilesystemPath.normalizeVirtualPath(sourcePath);
        MountedFilesystem source = requireResolved(normalizedSourcePath);
        require(source, FilesystemCapability.DELETE);
        log.debug("composite filesystem move start sourcePath={} targetPath={} sourceMountPath={}",
                sourcePath, targetPath, source.mountPath());
        copy(normalizedSourcePath, targetPath);
        source.filesystem().delete(source.toTargetPath(normalizedSourcePath));
        log.debug("composite filesystem move done sourcePath={} targetPath={}", sourcePath, targetPath);
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeGlobPattern(pathPattern);
        String staticPrefix = FilesystemPath.staticPrefixForGlob(normalizedPattern);
        log.debug("composite filesystem glob start pattern={} normalizedPattern={} staticPrefix={}",
                pathPattern, normalizedPattern, staticPrefix);
        List<FileEntry> matches = new ArrayList<>();

        walkGlob(staticPrefix, normalizedPattern, matches, new HashSet<>());

        List<FileEntry> result = matches.stream()
                .sorted(Comparator.comparing(FileEntry::path))
                .toList();
        log.debug("composite filesystem glob done pattern={} normalizedPattern={} matches={}",
                pathPattern, normalizedPattern, result.size());
        return result;
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        return grep(path, text, GrepOptions.unlimited()).matches();
    }

    @Override
    public GrepResult grep(String path, String text, GrepOptions options) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        GrepAccumulator accumulator = new GrepAccumulator(GrepOptions.effective(options));
        log.debug("composite filesystem grep start path={} normalizedPath={} textLength={} maxFiles={} maxMatches={}",
                path, normalizedPath, text == null ? 0 : text.length(),
                accumulator.options.maxFiles(), accumulator.options.maxMatches());

        walkGrep(normalizedPath, text, accumulator, new HashSet<>());

        GrepResult result = accumulator.toResult();
        log.debug("composite filesystem grep done path={} normalizedPath={} matches={} truncated={} reason={} searchedMounts={} searchedFiles={}",
                path, normalizedPath, result.matches().size(), result.truncated(), result.truncationReason(),
                result.searchedMounts(), result.searchedFiles());
        return result;
    }

    @Override
    public Optional<String> readUrl(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        MountedFilesystem mounted = requireResolved(normalizedPath);

        require(mounted, FilesystemCapability.READ_URL);

        String targetPath = mounted.toTargetPath(normalizedPath);
        log.debug("composite filesystem readUrl start path={} normalizedPath={} mountPath={} targetPath={}",
                path, normalizedPath, mounted.mountPath(), targetPath);
        Optional<String> url = mounted.filesystem().readUrl(targetPath);
        log.debug("composite filesystem readUrl done path={} normalizedPath={} mountPath={} targetPath={} present={}",
                path, normalizedPath, mounted.mountPath(), targetPath, url.isPresent());
        return url;
    }

    private Optional<MountedFilesystem> resolve(String normalizedPath) {
        // Longest prefix wins: /project/tmp should beat /project.
        return mounts.stream()
                .filter(mount -> mount.matches(normalizedPath))
                .max(Comparator.comparingInt(mount -> mount.mountPath().length()));
    }

    private MountedFilesystem requireResolved(String normalizedPath) {
        return resolve(normalizedPath)
                .orElseThrow(() -> new FileNotFoundException("No filesystem mount for " + normalizedPath));
    }

    private void require(MountedFilesystem mounted, FilesystemCapability capability) {
        if (!mounted.options().allows(capability)) {
            throw new UnsupportedFilesystemOperationException(
                    "Filesystem mount " + mounted.mountPath() + " does not allow " + capability
            );
        }
    }

    private List<FileEntry> syntheticChildren(String normalizedPath) {
        Map<String, FileEntry> entries = new LinkedHashMap<>();

        for (MountedFilesystem mounted : mounts) {
            if (!FilesystemPath.isStrictAncestor(normalizedPath, mounted.mountPath())) {
                continue;
            }

            String relativePath = FilesystemPath.relativeVirtualPath(normalizedPath, mounted.mountPath());
            String childName = relativePath.split(FilesystemPathNames.ROOT, 2)[0];
            String childPath = FilesystemPath.joinVirtualPath(normalizedPath, childName);

            entries.putIfAbsent(childPath, new FileEntry(childPath, childName, FileEntryType.DIRECTORY, 0L, null));
        }

        return new ArrayList<>(entries.values());
    }

    private void walkGlob(String path, String normalizedPattern, List<FileEntry> matches, Set<String> visited) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);

        if (!visited.add(normalizedPath)) {
            return;
        }

        Optional<MountedFilesystem> mounted = resolve(normalizedPath);
        mounted.ifPresent(filesystem -> require(filesystem, FilesystemCapability.SEARCH));

        FileEntry entry = stat(normalizedPath);
        if (entry.type() == FileEntryType.FILE) {
            if (GlobMatcher.matches(normalizedPattern, entry.path())) {
                matches.add(entry);
            }
            return;
        }

        // Walk through normal and synthetic directories so a broad glob can span nested mounts.
        for (FileEntry child : list(normalizedPath)) {
            if (child.type() == FileEntryType.DIRECTORY) {
                walkGlob(child.path(), normalizedPattern, matches, visited);
                continue;
            }

            if (GlobMatcher.matches(normalizedPattern, child.path())) {
                matches.add(child);
            }
        }
    }

    private void walkGrep(String path, String text, GrepAccumulator accumulator, Set<String> visited) {
        if (accumulator.truncated()) {
            return;
        }

        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        if (!visited.add(normalizedPath)) {
            return;
        }

        Optional<MountedFilesystem> mounted = resolve(normalizedPath);
        List<MountedFilesystem> descendantMounts = descendantMounts(normalizedPath);

        if (mounted.isPresent()) {
            // Search the resolved mount first, then visit deeper mounts explicitly.
            // Parent results under deeper mount paths are filtered to preserve longest-prefix overlay semantics.
            List<String> excludedMountPaths = descendantMounts.stream()
                    .map(MountedFilesystem::mountPath)
                    .toList();
            grepMounted(mounted.get(), normalizedPath, text, accumulator, excludedMountPaths);
        } else if (descendantMounts.isEmpty()) {
            throw new FileNotFoundException("No filesystem mount for " + normalizedPath);
        }

        for (MountedFilesystem descendant : topLevelMounts(descendantMounts)) {
            walkGrep(descendant.mountPath(), text, accumulator, visited);
        }
    }

    private void grepMounted(MountedFilesystem mounted,
                             String normalizedPath,
                             String text,
                             GrepAccumulator accumulator,
                             List<String> excludedMountPaths) {
        if (accumulator.truncated()) {
            return;
        }

        require(mounted, FilesystemCapability.SEARCH);

        if (accumulator.maxMatchesReached()) {
            accumulator.truncate(GrepResult.TRUNCATED_BY_MAX_MATCHES);
            return;
        }
        if (accumulator.maxFilesReached()) {
            accumulator.truncate(GrepResult.TRUNCATED_BY_MAX_FILES);
            return;
        }

        boolean leafMount = !(mounted.filesystem() instanceof CompositeFilesystem);
        if (leafMount) {
            accumulator.incrementSearchedMounts();
        }

        GrepOptions childOptions = accumulator.remainingOptions();

        try {
            GrepResult result = mounted.filesystem().grep(mounted.toTargetPath(normalizedPath), text, childOptions);
            accumulator.addResult(
                    result,
                    mounted::toMountMatch,
                    // Exclude paths handled by more specific mounts so parent content cannot duplicate or shadow them.
                    match -> excludedMountPaths.stream()
                            .noneMatch(excludedPath -> FilesystemPath.isSameOrDescendant(excludedPath, match.path()))
            );
        } catch (FileNotFoundException exception) {
            if (excludedMountPaths.isEmpty()) {
                throw exception;
            }
        }
    }

    private List<MountedFilesystem> descendantMounts(String normalizedPath) {
        return mounts.stream()
                .filter(mount -> FilesystemPath.isStrictAncestor(normalizedPath, mount.mountPath()))
                .sorted(Comparator.comparing(MountedFilesystem::mountPath))
                .toList();
    }

    private List<MountedFilesystem> topLevelMounts(List<MountedFilesystem> candidates) {
        return candidates.stream()
                .filter(candidate -> candidates.stream()
                        .noneMatch(other -> other != candidate
                                && FilesystemPath.isStrictAncestor(other.mountPath(), candidate.mountPath())))
                .toList();
    }

    private FileEntry syntheticDirectory(String path) {
        String normalizedPath = FilesystemPath.normalizeVirtualPath(path);
        return new FileEntry(
                normalizedPath,
                FilesystemPath.nameOf(normalizedPath),
                FileEntryType.DIRECTORY,
                0L,
                null
        );
    }

    private boolean containsFilesystem(Filesystem filesystem, Set<CompositeFilesystem> visited) {
        if (!visited.add(this)) {
            return false;
        }

        for (MountedFilesystem mount : mounts) {
            if (mount.filesystem() == filesystem) {
                return true;
            }

            if (mount.filesystem() instanceof CompositeFilesystem composite
                    && composite.containsFilesystem(filesystem, visited)) {
                return true;
            }
        }

        return false;
    }

    private static class GrepAccumulator {

        private final GrepOptions options;
        private final List<GrepMatch> matches = new ArrayList<>();
        private long searchedMounts;
        private long searchedFiles;
        private String truncationReason;

        private GrepAccumulator(GrepOptions options) {
            this.options = options;
        }

        private boolean truncated() {
            return truncationReason != null;
        }

        private boolean maxFilesReached() {
            return options.isMaxFilesReached(searchedFiles);
        }

        private boolean maxMatchesReached() {
            return options.isMaxMatchesReached(matches.size());
        }

        private void incrementSearchedMounts() {
            searchedMounts++;
        }

        private GrepOptions remainingOptions() {
            // Convert global counters into a child-local budget for early stopping.
            return options.remainingAfter(searchedFiles, matches.size());
        }

        private void addResult(GrepResult result,
                               Function<GrepMatch, GrepMatch> mapper,
                               Predicate<GrepMatch> include) {
            searchedMounts += result.searchedMounts();
            searchedFiles += result.searchedFiles();

            for (GrepMatch match : result.matches()) {
                GrepMatch mappedMatch = mapper.apply(match);
                if (!include.test(mappedMatch)) {
                    continue;
                }

                if (maxMatchesReached()) {
                    truncate(GrepResult.TRUNCATED_BY_MAX_MATCHES);
                    return;
                }

                matches.add(mappedMatch);
            }

            if (result.truncated()) {
                truncate(result.truncationReason());
            }
        }

        private void truncate(String reason) {
            if (truncationReason == null) {
                truncationReason = reason;
            }
        }

        private GrepResult toResult() {
            return new GrepResult(matches, truncated(), truncationReason, searchedMounts, searchedFiles);
        }

    }

}
