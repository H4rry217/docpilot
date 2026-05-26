package io.docpilot.filesystem.provider;

import io.docpilot.filesystem.exception.FilesystemException;
import io.docpilot.filesystem.exception.FileNotFoundException;
import io.docpilot.filesystem.model.FileEntry;
import io.docpilot.filesystem.model.FileEntryType;
import io.docpilot.filesystem.model.GrepMatch;
import io.docpilot.filesystem.path.FilesystemPath;
import io.docpilot.filesystem.path.FilesystemPathNames;
import io.docpilot.filesystem.path.GlobMatcher;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class S3FilesystemProvider implements FilesystemProvider {

    private final S3FilesystemProviderConfig config;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public S3FilesystemProvider(S3FilesystemProviderConfig config) {
        this(config, buildClient(config), buildPresigner(config));
    }

    public S3FilesystemProvider(S3FilesystemProviderConfig config, S3Client s3Client, S3Presigner s3Presigner) {
        if (config == null || !config.isComplete()) {
            throw new IllegalArgumentException("Complete S3 filesystem config is required");
        }
        this.config = config;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String providerId() {
        return config.getProviderId();
    }

    @Override
    public List<FileEntry> list(String path) {
        String prefix = directoryPrefix(toKey(path));
        // S3 is flat; delimiter "/" lets us expose immediate child "directories" under this prefix.
        ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(config.getBucket())
                .prefix(prefix)
                .delimiter(FilesystemPathNames.ROOT)
                .build());
        List<FileEntry> entries = new ArrayList<>();
        response.commonPrefixes().forEach(commonPrefix -> {
            String key = trimTrailingSlash(commonPrefix.prefix());
            entries.add(new FileEntry(key, FilesystemPath.nameOf(key), FileEntryType.DIRECTORY, 0L, null));
        });
        response.contents().stream()
                .filter(object -> !object.key().equals(prefix))
                .forEach(object -> entries.add(toEntry(object, FileEntryType.FILE)));
        return entries;
    }

    @Override
    public byte[] read(String path) {
        String key = toKey(path);
        try {
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build());
            return bytes.asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new FileNotFoundException("S3 object not found: " + key);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new FileNotFoundException("S3 object not found: " + key);
            }
            throw new FilesystemException("Failed to read S3 object: " + key, exception);
        }
    }

    @Override
    public void write(String path, byte[] content) {
        String key = toKey(path);
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .contentLength((long) content.length)
                .build(), RequestBody.fromBytes(content));
    }

    @Override
    public void delete(String path) {
        String key = toKey(path);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(key)
                .build());
        String prefix = directoryPrefix(key);
        for (String childKey : listAllKeys(prefix)) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(childKey)
                    .build());
        }
    }

    @Override
    public boolean exists(String path) {
        String key = toKey(path);
        if (objectExists(key)) {
            return true;
        }
        return !listAllKeys(directoryPrefix(key), 1).isEmpty();
    }

    @Override
    public FileEntry stat(String path) {
        String key = toKey(path);
        try {
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build());
            return new FileEntry(key, FilesystemPath.nameOf(key), FileEntryType.FILE, response.contentLength(), response.lastModified());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) {
                throw new FilesystemException("Failed to stat S3 object: " + key, exception);
            }
        }
        // A path can still be a virtual directory even when no object exists at the exact key.
        if (!listAllKeys(directoryPrefix(key), 1).isEmpty()) {
            return new FileEntry(key, FilesystemPath.nameOf(key), FileEntryType.DIRECTORY, 0L, null);
        }
        throw new FileNotFoundException("S3 path not found: " + key);
    }

    @Override
    public void copy(String sourcePath, String targetPath) {
        String sourceKey = toKey(sourcePath);
        String targetKey = toKey(targetPath);
        s3Client.copyObject(builder -> builder
                .copySource(config.getBucket() + FilesystemPathNames.ROOT + sourceKey)
                .bucket(config.getBucket())
                .key(targetKey));
    }

    @Override
    public void move(String sourcePath, String targetPath) {
        copy(sourcePath, targetPath);
        delete(sourcePath);
    }

    @Override
    public List<FileEntry> glob(String pathPattern) {
        String normalizedPattern = FilesystemPath.normalizeProviderPath(pathPattern);
        String prefix = GlobMatcher.prefixBeforeWildcard(normalizedPattern);
        return listAllObjects(prefix).stream()
                .map(object -> toEntry(object, FileEntryType.FILE))
                .filter(entry -> GlobMatcher.matches(normalizedPattern, entry.path()))
                .toList();
    }

    @Override
    public List<GrepMatch> grep(String path, String text) {
        String key = toKey(path);
        List<String> keys = objectExists(key) ? List.of(key) : listAllKeys(directoryPrefix(key));
        List<GrepMatch> matches = new ArrayList<>();
        for (String objectKey : keys) {
            String content = new String(read(objectKey), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (lines[index].contains(text)) {
                    matches.add(new GrepMatch(objectKey, index + 1L, lines[index]));
                }
            }
        }
        return matches;
    }

    @Override
    public Optional<String> readUrl(String path) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(toKey(path))
                .responseContentDisposition("inline")
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(config.getPresignedUrlTtl())
                .getObjectRequest(getObjectRequest)
                .build();
        return Optional.of(s3Presigner.presignGetObject(presignRequest).url().toString());
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private List<String> listAllKeys(String prefix) {
        return listAllKeys(prefix, Integer.MAX_VALUE);
    }

    private List<String> listAllKeys(String prefix, int limit) {
        return listAllObjects(prefix, limit).stream()
                .map(S3Object::key)
                .toList();
    }

    private List<S3Object> listAllObjects(String prefix) {
        return listAllObjects(prefix, Integer.MAX_VALUE);
    }

    private List<S3Object> listAllObjects(String prefix, int limit) {
        List<S3Object> objects = new ArrayList<>();
        String continuationToken = null;
        do {
            // Keep pagination here so glob/grep/delete do not need to know about S3 continuation tokens.
            ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(config.getBucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            for (S3Object object : response.contents()) {
                objects.add(object);
                if (objects.size() >= limit) {
                    return objects;
                }
            }
            continuationToken = response.nextContinuationToken();
        } while (StringUtils.isNotBlank(continuationToken));
        return objects;
    }

    private FileEntry toEntry(S3Object object, FileEntryType type) {
        Instant lastModified = object.lastModified();
        return new FileEntry(
                object.key(),
                FilesystemPath.nameOf(object.key()),
                type,
                type == FileEntryType.DIRECTORY ? 0L : object.size(),
                lastModified
        );
    }

    private String toKey(String path) {
        return FilesystemPath.normalizeProviderPath(path);
    }

    private String directoryPrefix(String key) {
        if (StringUtils.isBlank(key)) {
            return "";
        }
        return key.endsWith(FilesystemPathNames.ROOT) ? key : key + FilesystemPathNames.ROOT;
    }

    private String trimTrailingSlash(String value) {
        if (value != null && value.endsWith(FilesystemPathNames.ROOT)) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static S3Client buildClient(S3FilesystemProviderConfig config) {
        S3ClientBuilder builder = S3Client.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(credentials(config))
                .region(region(config))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                        .build());
        return builder.build();
    }

    private static S3Presigner buildPresigner(S3FilesystemProviderConfig config) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .endpointOverride(URI.create(config.getEndpoint()))
                .credentialsProvider(credentials(config))
                .region(region(config))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.isPathStyleAccess())
                        .build());
        return builder.build();
    }

    private static Region region(S3FilesystemProviderConfig config) {
        if (StringUtils.isNotBlank(config.getRegion())) {
            return Region.of(config.getRegion());
        }
        return Region.US_EAST_1;
    }

    private static StaticCredentialsProvider credentials(S3FilesystemProviderConfig config) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
    }

}
