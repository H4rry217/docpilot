# DocPilot Filesystem

`docpilot-filesystem` 提供 path-first 的虚拟文件系统构件。一个 filesystem 可以是本地目录、S3 bucket 前缀、workspace 文档树、远程网盘，也可以是另一棵组合 filesystem。

## 核心概念

- `Filesystem` 是稳定的只读能力接口，包含 list、read、exists、stat、glob、grep、readUrl。
- write/delete/copy/move 已预留在接口上，但 v1 默认不支持，除非具体 filesystem 明确开启。
- `CompositeFilesystem` 本身也是 filesystem，内部按路径挂载其他 filesystem，并用最长前缀转发请求。
- `MountedFilesystem` 表示一条挂载记录：mount path、目标 filesystem、目标 root、`MountOptions`。
- `MountOptions` 控制 READ、LIST、STAT、SEARCH、READ_URL、WRITE、DELETE、COPY、MOVE 等能力。
- `ProviderFilesystem` 把已有 local、S3 这类 `FilesystemProvider` 适配成 path-first 的 `Filesystem`。

## 示例

```java
Filesystem local = new ProviderFilesystem(new LocalFilesystemProvider("local", Path.of("./data")));

CompositeFilesystem project = new CompositeFilesystem()
        .mount("/project/uploads", local);

String text = project.readText("/project/uploads/readme.md");
List<GrepMatch> matches = project.grep("/project", "keyword");
```

workspace 文件树也可以被组合进去：

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

多个可读 workspace 可以先逐个挂到 `/workspace/{workspaceId}`，再整体挂到 `/project`：

```java
Filesystem workspaceNamespace = new CompositeFilesystem()
        .mount("/workspace/123",
                new WorkspaceFilesystem(123L, workspaceRepository, nodeRepository, documentRepository))
        .mount("/workspace/456",
                new WorkspaceFilesystem(456L, workspaceRepository, nodeRepository, documentRepository));

Filesystem aiRoot = new CompositeFilesystem()
        .mount("/project", workspaceNamespace);
```

filesystem 对象不需要是全局 Spring Bean。调用方可以按请求、用户、AI 会话或工具沙箱自行组装 root filesystem。

## 挂载行为

- 所有路径都会规范化为 Unix 风格绝对路径，并拒绝 `..` 逃逸。
- 重复 mount path 会被拒绝。
- 最长前缀优先，`/project/tmp` 会优先于 `/project`。
- `list`、`stat`、`exists` 支持由挂载点合成出来的目录。例如只挂了 `/project/workspace/ws1` 和 `/project/workspace/ws2`，`list("/project/workspace")` 会返回 `ws1` 和 `ws2`。
- `read`、`glob`、`grep`、`readUrl` 必须命中真实 mount。
- 禁止组合 filesystem 形成循环挂载。

## Providers

`LocalFilesystemProvider` 把文件存储在配置的本地 root 下，并拒绝逃逸 root 的路径。

`S3FilesystemProvider` 使用 AWS SDK v2，支持 MinIO 等 S3 兼容服务，包含 endpoint、region、bucket、access key、secret key、path-style access、checksum 和预签名 URL 配置。

provider 自身可以保留 write/delete/copy/move 能力；通过 `CompositeFilesystem` 挂载后，还会再受到 mount capabilities 控制。

## Spring 启动配置

`docpilot-web-service` 会把 local provider 和可选 S3 provider 注册进 `ProviderRegistry`，但不会强制创建全局 filesystem root。应用代码可以为具体用户或 AI 会话，用 provider 与单个 workspace filesystem 自行组装 `CompositeFilesystem`。

示例配置：

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

## 真实 S3 测试

`S3FilesystemProviderTest` 包含真实 S3 write/read/list/delete 测试。未提供 S3 测试配置时会自动跳过。

```bash
mvn -pl docpilot-filesystem -Dtest=S3FilesystemProviderTest test \
  -Ddocpilot.test.s3.endpoint=http://localhost:9000 \
  -Ddocpilot.test.s3.bucket=docpilot-test \
  -Ddocpilot.test.s3.region=us-east-1 \
  -Ddocpilot.test.s3.access-key=minioadmin \
  -Ddocpilot.test.s3.secret-key=minioadmin \
  -Ddocpilot.test.s3.path-style-access=true
```

S3 + workspace 组合式 live test 还需要真实 workspace id。PowerShell 下要把 `-D...` 参数放在同一个 Maven 命令里，或者用反引号续行：

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
