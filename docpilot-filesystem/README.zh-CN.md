# DocPilot Filesystem

`docpilot-filesystem` 为 DocPilot 提供 workspace 级虚拟文件系统。调用方只使用 `/project/readme.md` 这样的 workspace 路径；模块内部通过 `PathMapping` 找到真实存储位置，再交给对应的 `FilesystemProvider` 执行。

## 核心概念

- `FilesystemService` 是统一入口，提供 read、write、list、delete、copy、move、glob、grep、stat、exists 等操作。
- `PathMapping` 表示一条路径映射规则。例如 `/project` 可以映射到 provider `s3` 的 `workspaces/ws_001/project` 根路径。
- `PathMappingService` 负责管理映射并按最长前缀解析路径。`/project/tmp/a.txt` 会优先命中 `/project/tmp`，而不是 `/project`。
- `FilesystemProvider` 是真实存储实现接口。当前模块内置 `LocalFilesystemProvider` 和 `S3FilesystemProvider`。
- `PathMappingStore` 是映射存储接口。startup 当前使用内存实现，后续可以替换为数据库实现，调用方不需要改。

## 示例

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

调用方看到的是 `/project/readme.md`；S3 provider 实际收到的是 `workspaces/ws_001/project/readme.md`。

## Provider

`LocalFilesystemProvider` 把文件存储在配置的本地 root 下面，并拒绝任何逃逸 root 的路径。它适合开发环境，也适合未来 agent sandbox 的临时目录。

`S3FilesystemProvider` 使用 AWS SDK v2，支持 S3 兼容服务，例如 MinIO。它支持 endpoint override、可选 region、bucket、access key、secret key、path-style access、checksum 配置和预签名读取 URL。

## Spring Startup 装配

`docpilot-startup` 会注册：

- 默认 local provider；
- 当 `docpilot.filesystem.s3.*` 配置完整时注册 S3 provider；
- 内存版 `PathMappingStore`；
- 创建 workspace 时自动创建默认 `/project` 映射。

示例配置：

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

## 说明

- 跨 provider 的 `move` 会按 copy 再 delete 处理。
- readonly 映射允许 read/list/stat，拒绝 write/delete/move 目标写入。
- glob 会先用静态前缀定位 PathMapping，因此一次 glob 操作会留在同一条映射内。

## 真实 S3 测试

`S3FilesystemProviderTest` 包含一个真实 S3 写入/读取/list/delete 测试。没有提供 S3 测试配置时，这个测试会自动跳过。

使用 Maven system properties 运行：

```bash
mvn -pl docpilot-filesystem -Dtest=S3FilesystemProviderTest test \
  -Ddocpilot.test.s3.endpoint=http://localhost:9000 \
  -Ddocpilot.test.s3.bucket=docpilot-test \
  -Ddocpilot.test.s3.region=us-east-1 \
  -Ddocpilot.test.s3.access-key=minioadmin \
  -Ddocpilot.test.s3.secret-key=minioadmin \
  -Ddocpilot.test.s3.path-style-access=true
```

也可以使用环境变量：

```bash
DOCPILOT_TEST_S3_ENDPOINT=http://localhost:9000
DOCPILOT_TEST_S3_BUCKET=docpilot-test
DOCPILOT_TEST_S3_REGION=us-east-1
DOCPILOT_TEST_S3_ACCESS_KEY=minioadmin
DOCPILOT_TEST_S3_SECRET_KEY=minioadmin
DOCPILOT_TEST_S3_PATH_STYLE_ACCESS=true
```

可选配置：

- `docpilot.test.s3.provider-id` / `DOCPILOT_TEST_S3_PROVIDER_ID`
