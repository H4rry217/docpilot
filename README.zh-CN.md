# DocPilot

[English](README.md)

DocPilot 是一个开源 AI 文档编辑器。它把富 Markdown 文档当作结构化项目来处理：后端维护稳定的 Block 文档模型，前端围绕这个模型提供工作区编辑体验，AI 能力则通过明确的文档、文件系统和模型边界接入。

项目仍在活跃开发中。当前代码重点覆盖 Block 文档契约、工作区/文档服务、本地认证、虚拟文件系统组合、OpenAI 兼容模型接入，以及 React 编辑器前端。

## 功能概览

- 面向富 Markdown 文档的结构化编辑体验。
- 基于工作区的文档组织、编辑和持久化。
- 后端服务、AI 流程和前端编辑器共用的稳定 Block 文档契约。
- 可组合项目文件、工作区文档和外部存储的虚拟文件系统访问能力。
- 面向对话、行内补全、文档辅助和知识检索的 AI 接入点。
- 支持文档导航和 Block 级编辑流程的中英文编辑器界面。

## 仓库结构

| 路径 | 说明 |
| --- | --- |
| `docpilot-shared` | 后端共享模块，包含通用返回/错误类型、认证主体、JSON 工具和 Spring Web 基础设施。 |
| `docpilot-block` | Markdown 解析、DocPilot Block 模型、Markdown 渲染和 ProseMirror JSON 转换。 |
| `docpilot-filesystem` | 虚拟文件系统接口、组合挂载、本地 provider、S3 兼容 provider 和工作区文件系统集成。 |
| `docpilot-ai` | AI 请求/响应模型、模型注册表，以及 OpenAI 兼容 chat/embedding 适配器。 |
| `docpilot-services` | 用户、工作区、文档、知识库、行内补全和 Spring Boot Web 服务模块。 |
| `docpilot-frontend` | DocPilot 编辑器工作区的 Vite React 前端。 |
| `scripts` | 运维辅助脚本，目前包含 Elasticsearch 索引创建脚本。 |
| `test-fixtures` | 解析和格式转换测试共用的文档 fixture。 |

## 环境要求

- JDK 25。
- Maven 3.9+。
- Node.js 22 和 npm，用于前端开发。
- Docker 和 Docker Compose，用于最省心的完整栈启动。

当前 Dockerfile 使用 `node:22-alpine` 构建前端，使用 `maven:3.9.11-eclipse-temurin-25` 构建后端，并在 Eclipse Temurin 25 JRE 上运行打包后的应用。

## 技术栈说明

DocPilot 后端使用 Spring Boot 和 Maven 多模块结构，前端使用 Vite + React。工作区文档使用 MongoDB，内置认证数据使用 MySQL，知识索引队列使用 Redis，知识检索使用 Elasticsearch，AI chat 和 embedding 模型通过 OpenAI 兼容 endpoint 接入。

## 快速启动

使用内置的 MySQL、MongoDB、Redis 和 Elasticsearch 启动完整栈：

```bash
docker compose up --build
```

Web 服务默认监听 `11451` 端口：

```bash
curl http://127.0.0.1:11451/health
```

可以通过 `DOCPILOT_WEB_PORT` 修改暴露端口。

## 本地开发

在仓库根目录运行后端测试：

```bash
mvn test
```

只启动 Spring Boot Web 服务及其依赖模块：

```bash
mvn -pl docpilot-services/docpilot-web-service -am spring-boot:run
```

本地启动后端时，需要确保 MySQL、MongoDB、Redis 和 Elasticsearch 可访问，或通过对应的 Spring 环境变量覆盖连接配置。`docker-compose.yml` 里列出了默认服务名和端口。

启动前端开发服务器：

```bash
cd docpilot-frontend
npm ci
npm run dev
```

Vite 开发服务器会把 `/api/*` 代理到 `http://127.0.0.1:11451`，因此需要先启动后端，才能使用登录、工作区和文档相关流程。

## 常用命令

```bash
# 全部后端测试
mvn test

# 构建后端 Web 服务及其依赖模块
mvn -pl docpilot-services/docpilot-web-service -am package

# 前端检查
cd docpilot-frontend
npm run typecheck
npm test
npm run build
```

## 配置说明

- `server.port` 默认是 `11451`。
- 内置认证默认使用 `local-password` provider。注册默认开启，可通过 `DOCPILOT_AUTH_ALLOW_REGISTRATION` 控制。
- 知识索引默认关闭，即 `DOCPILOT_KNOWLEDGE_ENABLED=false`。开启后需要 Redis、Elasticsearch，以及配置好的 embedding/summary AI 模型。
- AI 模型通过 Spring 的 `docpilot.ai` 配置接入。内置适配器面向 OpenAI 兼容的 chat 和 embedding endpoint。
- 如果存在 `application-local.yml`，应用会自动导入；该文件已被 Git 忽略。建议把本机 API key 和环境覆盖配置放在这里，或通过环境变量提供。

## 模块文档

- [docpilot-block](docs/docpilot-block.zh-CN.md)
- [docpilot-filesystem](docs/docpilot-filesystem.zh-CN.md)

## 许可证

DocPilot 使用 [Apache License 2.0](LICENSE) 许可证。
