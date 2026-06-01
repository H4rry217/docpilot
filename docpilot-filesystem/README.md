# DocPilot Filesystem

`docpilot-filesystem` provides path-first virtual filesystem building blocks for DocPilot. A filesystem can be a local directory, S3 bucket prefix, workspace document tree, remote drive, or another composed filesystem.

## Core Concepts

- `Filesystem` is the stable read-oriented interface for list, read, exists, stat, glob, grep, and readUrl operations.
- Write-style methods are present on the interface for future expansion, but v1 callers should expect unsupported-operation failures unless a concrete filesystem explicitly enables them.
- `CompositeFilesystem` is itself a filesystem. It mounts other filesystems by path and delegates requests by longest prefix match.
- `MountedFilesystem` records one mount: mount path, target filesystem, target root, and `MountOptions`.
- `MountOptions` controls capabilities such as READ, LIST, STAT, SEARCH, READ_URL, WRITE, DELETE, COPY, and MOVE.
- `ProviderFilesystem` adapts existing `FilesystemProvider` implementations such as local and S3 into the path-first `Filesystem` interface.

## Example

```java
Filesystem local = new ProviderFilesystem(new LocalFilesystemProvider("local", Path.of("./data")));

CompositeFilesystem project = new CompositeFilesystem()
        .mount("/project/uploads", local);

String text = project.readText("/project/uploads/readme.md");
List<GrepMatch> matches = project.grep("/project", "keyword");
```

Workspace trees are composed the same way. `WorkspaceFilesystem` represents one concrete workspace id; the `/workspace/{workspaceId}` namespace is created by mounting it:

```java
Long workspaceId = 123L;

Filesystem workspace = new WorkspaceFilesystem(
        workspaceId,
        workspaceRepository,
        nodeRepository,
        documentRepository
);

Filesystem workspaceNamespace = new CompositeFilesystem()
        .mount("/workspace/" + workspaceId, workspace);

CompositeFilesystem aiRoot = new CompositeFilesystem()
        .mount("/project", workspaceNamespace);

String markdown = aiRoot.readText("/project/workspace/123/docs/a.md");
```

For multiple readable workspaces, add one mount per authorized workspace before exposing the composed root:

```java
Filesystem workspaceNamespace = new CompositeFilesystem()
        .mount("/workspace/123",
                new WorkspaceFilesystem(123L, workspaceRepository, nodeRepository, documentRepository))
        .mount("/workspace/456",
                new WorkspaceFilesystem(456L, workspaceRepository, nodeRepository, documentRepository));

Filesystem aiRoot = new CompositeFilesystem()
        .mount("/project", workspaceNamespace);
```

The filesystem object does not need to be a global Spring bean. Callers can assemble a root filesystem per request, per user, per agent session, or per tool sandbox.

## Mount Behavior

- All paths are normalized to Unix-style absolute paths, and `..` traversal is rejected.
- Duplicate mount paths are rejected.
- Longest prefix wins, so `/project/tmp` beats `/project`.
- `list`, `stat`, and `exists` understand synthetic directories created by mounts. If `/project/workspace/ws1` and `/project/workspace/ws2` are mounted, `list("/project/workspace")` returns `ws1` and `ws2`.
- `read`, `glob`, `grep`, and `readUrl` must resolve to a real mount.
- Composite mount cycles are rejected.

## Providers

`LocalFilesystemProvider` stores files under a configured local root and rejects paths that escape that root.

`S3FilesystemProvider` uses AWS SDK v2 and supports S3-compatible services such as MinIO. It supports endpoint override, optional region, bucket, access key, secret key, path-style access, checksum settings, and presigned read URLs.

Provider implementations can still expose native write/delete/copy/move behavior. When mounted through `CompositeFilesystem`, those operations are additionally controlled by mount capabilities.

## Spring Startup Wiring

`docpilot-web-service` registers local and optional S3 providers in a `ProviderRegistry`. It does not create a required global filesystem root. Application code can build a `CompositeFilesystem` from the registered providers and per-workspace filesystems for the specific user or AI session.

Example config:

```yaml
docpilot:
  filesystem:
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

The S3 + workspace composite live test also needs a real workspace id. In PowerShell, keep the `-D...` arguments on the same Maven command, or use backticks for line continuation:

```powershell
mvn -pl docpilot-services/docpilot-web-service -am `
  "-Dtest=S3WorkspaceCompositeFilesystemLiveTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Ddocpilot.test.s3.endpoint=https://oss-cn-guangzhou.aliyuncs.com" `
  "-Ddocpilot.test.s3.region=cn-guangzhou" `
  "-Ddocpilot.test.s3.bucket=docpilot-dev" `
  "-Ddocpilot.test.s3.access-key=<access-key>" `
  "-Ddocpilot.test.s3.secret-key=<secret-key>" `
  "-Ddocpilot.test.s3.path-style-access=false" `
  "-Ddocpilot.test.workspace.id=<workspace-id>"
```
