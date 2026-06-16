# DocPilot

[Chinese](README.zh-CN.md)

DocPilot is an open source AI document editor. It treats rich Markdown as a structured document project: the backend keeps a stable block model, the frontend edits that model through a workspace UI, and AI features can work against explicit document and filesystem boundaries.

The project is under active development. The current codebase focuses on the block document contract, workspace/document services, local authentication, virtual filesystem composition, OpenAI-compatible model integration, and a React editor frontend.

## Features

- Structured editing for rich Markdown documents.
- Workspace-based document organization, editing, and persistence.
- A stable block document contract shared by backend services, AI workflows, and the editor frontend.
- Virtual filesystem access that can combine project files, workspace documents, and external storage.
- AI integration points for chat, inline completion, document assistance, and knowledge retrieval.
- Bilingual editor UI with document navigation and block-level editing workflows.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `docpilot-shared` | Shared backend modules: common result/error types, auth subjects, JSON helpers, and Spring Web infrastructure. |
| `docpilot-block` | Markdown parsing, DocPilot block model, Markdown rendering, and ProseMirror JSON conversion. |
| `docpilot-filesystem` | Virtual filesystem interfaces, composite mounts, local provider, S3-compatible provider, and workspace filesystem integration. |
| `docpilot-ai` | Provider-neutral AI request/response models, model registries, and OpenAI-compatible chat/embedding adapters. |
| `docpilot-services` | User, workspace, document, knowledge, inline completion, and Spring Boot web-service modules. |
| `docpilot-frontend` | Vite React frontend for the DocPilot editor workspace. |
| `scripts` | Operational helper scripts, currently including Elasticsearch index creation. |
| `test-fixtures` | Shared document format fixtures for parser/conversion tests. |

## Requirements

- JDK 25.
- Maven 3.9+.
- Node.js 22 and npm for frontend development.
- Docker and Docker Compose for the easiest full-stack startup.

The Dockerfile builds the frontend with `node:22-alpine`, builds the backend with `maven:3.9.11-eclipse-temurin-25`, and runs the packaged app on an Eclipse Temurin 25 JRE.

## Technology Notes

DocPilot uses a Spring Boot and Maven multi-module backend, a Vite + React frontend, MongoDB for workspace documents, MySQL for built-in auth data, Redis for queued knowledge-index work, Elasticsearch for knowledge retrieval, and OpenAI-compatible endpoints for chat and embedding models.

## Quick Start

Run the full stack with the bundled MySQL, MongoDB, Redis, and Elasticsearch services:

```bash
docker compose up --build
```

The web service listens on port `11451` by default:

```bash
curl http://127.0.0.1:11451/health
```

You can change the exposed web port with `DOCPILOT_WEB_PORT`.

## Local Development

Run backend tests from the repository root:

```bash
mvn test
```

Run only the Spring Boot web service and the modules it depends on:

```bash
mvn -pl docpilot-services/docpilot-web-service -am spring-boot:run
```

For local backend startup, make sure MySQL, MongoDB, Redis, and Elasticsearch are reachable or override the corresponding Spring environment variables. `docker-compose.yml` shows the default service names and ports.

Run the frontend dev server:

```bash
cd docpilot-frontend
npm ci
npm run dev
```

The Vite dev server proxies `/api/*` to `http://127.0.0.1:11451`, so the backend should be running for authenticated workspace and document workflows.

## Useful Commands

```bash
# All backend tests
mvn test

# Build the backend web service and its dependencies
mvn -pl docpilot-services/docpilot-web-service -am package

# Frontend checks
cd docpilot-frontend
npm run typecheck
npm test
npm run build
```

## Configuration Notes

- `server.port` defaults to `11451`.
- Built-in auth defaults to the `local-password` provider. Registration is enabled by default and can be controlled with `DOCPILOT_AUTH_ALLOW_REGISTRATION`.
- Knowledge indexing is disabled by default with `DOCPILOT_KNOWLEDGE_ENABLED=false`. Enabling it requires Redis, Elasticsearch, and configured AI embedding/summary models.
- AI models are configured through the Spring `docpilot.ai` settings. The built-in adapter expects OpenAI-compatible chat and embedding endpoints.
- `application-local.yml` is imported if present and is ignored by Git. Keep local API keys and machine-specific overrides there or in environment variables.

## Module Documentation

- [docpilot-block](docs/docpilot-block.md)
- [docpilot-filesystem](docs/docpilot-filesystem.md)

## License

DocPilot is licensed under the [Apache License 2.0](LICENSE).
