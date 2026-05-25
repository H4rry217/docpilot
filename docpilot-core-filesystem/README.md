# DocPilot Filesystem

`docpilot-filesystem` provides a workspace virtual filesystem for DocPilot. Callers use workspace paths such as `/project/readme.md`; the module resolves those paths through `PathMapping` records and delegates the real work to a `FilesystemProvider`.

## Core Concepts

- `FilesystemService` is the main entry point for read, write, list, delete, copy, move, glob, grep, stat, and exists operations.
- `PathMapping` maps a workspace virtual path to a provider path root. For example, `/project` can map to provider `s3` root `workspaces/ws_001/project`.
- `PathMappingService` manages mappings and resolves paths by longest prefix. `/project/tmp/a.txt` will match `/project/tmp` before `/project`.
- `FilesystemProvider` is the storage implementation interface. This module includes `LocalFilesystemProvider` and `S3FilesystemProvider`.
- `PathMappingStore` stores mappings. Startup currently uses the in-memory implementation, so a database-backed store can be added later without changing callers.

## Example

```java
PathMapping mapping = new PathMapping();
mapping.setWorkspaceId("ws_001");
mapping.setVirtualPath("/project");
mapping.setProviderId("s3");
mapping.setProviderRoot("workspaces/ws_001/project");
mapping.setReadonly(false);
mapping.setEnabled(true);
pathMappingService.create(mapping);

filesystemService.writeText("ws_001", "/project/readme.md", "# Hello");
String markdown = filesystemService.readText("ws_001", "/project/readme.md");
```

The caller sees `/project/readme.md`; the S3 provider receives `workspaces/ws_001/project/readme.md`.

## Providers

`LocalFilesystemProvider` stores files under a configured local root and rejects paths that escape that root. It is useful for development and future agent sandbox storage.

`S3FilesystemProvider` uses AWS SDK v2 and supports S3-compatible services such as MinIO. It supports endpoint override, optional region, bucket, access key, secret key, path-style access, checksum settings, and presigned read URLs.

## Spring Startup Wiring

`docpilot-startup` registers:

- a local provider by default;
- an S3 provider when `docpilot.filesystem.s3.*` config is complete;
- an in-memory `PathMappingStore`;
- a default `/project` mapping whenever a workspace is created.

Example config:

```yaml
docpilot:
  filesystem:
    default-provider-id: s3
    local:
      provider-id: local
      root: ./data/filesystem
    s3:
      provider-id: s3
      endpoint: http://localhost:9000
      region: us-east-1
      bucket: docpilot
      access-key: minioadmin
      secret-key: minioadmin
      path-style-access: true
      presigned-url-ttl: 10m
```

## Notes

- Cross-provider `move` is implemented as copy then delete.
- Readonly mappings allow read/list/stat operations and reject writes, deletes, and move targets.
- Glob patterns are resolved through their static prefix, so a single glob operation stays inside one mapping.

## Real S3 Test

`S3FilesystemProviderTest` includes a real S3 write/read/list/delete test. It is skipped unless S3 test config is provided.

Run with Maven system properties:

```bash
mvn -pl docpilot-filesystem -Dtest=S3FilesystemProviderTest test \
  -Ddocpilot.test.s3.endpoint=http://localhost:9000 \
  -Ddocpilot.test.s3.bucket=docpilot-test \
  -Ddocpilot.test.s3.region=us-east-1 \
  -Ddocpilot.test.s3.access-key=minioadmin \
  -Ddocpilot.test.s3.secret-key=minioadmin \
  -Ddocpilot.test.s3.path-style-access=true
```

Or use environment variables:

```bash
DOCPILOT_TEST_S3_ENDPOINT=http://localhost:9000
DOCPILOT_TEST_S3_BUCKET=docpilot-test
DOCPILOT_TEST_S3_REGION=us-east-1
DOCPILOT_TEST_S3_ACCESS_KEY=minioadmin
DOCPILOT_TEST_S3_SECRET_KEY=minioadmin
DOCPILOT_TEST_S3_PATH_STYLE_ACCESS=true
```

Optional values:

- `docpilot.test.s3.provider-id` / `DOCPILOT_TEST_S3_PROVIDER_ID`
