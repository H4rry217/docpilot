# DocPilot Project

## Goal

DocPilot is an open source AI document editor. It follows the Codex idea of letting an AI agent operate on a structured project, but the editing target is extended Markdown rich text rather than code.

## Current Backend Shape

- Java 25.
- Spring Boot 3.5.10.
- Maven multi-module project.
- No dependency on `moonpx-parent` or any `moonpx-*` artifact.
- `moonpx-*` repositories are only style references for module boundaries and Java coding conventions.

## Modules

- `docpilot-shared`: grouping parent for shared backend modules.
  - `docpilot-common`: framework-neutral shared types such as `Result`, `StatusCode`, JSON helpers, common exceptions, auth subject abstractions, and `BaseEntity`.
  - `docpilot-web-common`: Spring Web shared infrastructure such as global exception handling, request trace/log filters, `@RequireAuth`, and the default Bearer JWT subject resolver.
- `docpilot-block`: foundational document structure library.
  - Parses Markdown with flexmark-java.
  - Keeps DocPilot's own block model as the stable internal document representation.
  - Maps block documents to ProseMirror JSON DTOs.
  - Renders block documents back to normalized Markdown.
  - Preserves HTML blocks as `docpilotHtmlBlock` data nodes.
- `docpilot-filesystem`: path-first virtual filesystem module.
  - Exposes read/search operations through `Filesystem`.
  - Composes arbitrary filesystems through `CompositeFilesystem` and mount paths.
  - Includes local filesystem and S3 provider implementations, adapted through `ProviderFilesystem`.
  - Leaves root filesystem assembly to the caller, so apps can build per-user or per-agent views.
- `docpilot-ai`: AI model integration infrastructure module.
  - Keeps provider-neutral chat request, response, usage, stream event, response format, and model metadata types.
  - Provides `AiChatModel` and `AiModelRegistry` as the stable service-facing AI boundary.
  - Includes an OpenAI-compatible provider adapter behind `io.docpilot.ai.provider.openai`.
- `docpilot-services`: grouping parent for service and backend application modules.
  - `docpilot-user-service`: user domain boundary service module.
  - `docpilot-document-service`: document domain service module.
  - `docpilot-web-service`: backend application entrypoint.
    - Provides `io.docpilot.DocPilotApplication`.
    - Exposes `GET /health`.
    - Depends on `docpilot-block`, `docpilot-document-service`, `docpilot-user-service`, `docpilot-ai`, and `docpilot-filesystem`.

## Block Model

- `BlockDocument`: schema version, top-level blocks, metadata.
- `BlockNode`: block id, type, attrs, inline children, block children, source range.
- `InlineNode`: inline type, text, attrs, rich-text marks, source range.
- `InlineMark`: mark type, attrs, source range.
- `SourceRange`: original Markdown offsets and line/column positions.

Current schema version is `docpilot-block/2`.

Supported block types include paragraph, heading, blockquote, bullet list, ordered list, list item, task list item, code block, thematic break, table, table row, table cell, front matter, math block, diagram block, callout, footnote definition, definition list, TOC, link reference definition, HTML block, extension block, and unsupported block.

Supported inline types include text, soft break, hard break, image, math inline, footnote reference, emoji, HTML inline, extension inline, and unsupported inline.

Supported marks include bold, italic, strike, code, link, underline, insert, subscript, superscript, and highlight.

## HTML Strategy

Markdown HTML is parsed and preserved, not executed. HTML block ProseMirror output uses:

- `type: "docpilotHtmlBlock"`
- `attrs.id`
- `attrs.title`
- `attrs.source`
- `attrs.displayMode`
- `attrs.allowScripts`

Default HTML attrs:

- `id = BlockNode.id`
- `title = "HTML"`
- `displayMode = "fit"`
- `allowScripts = false`

The backend only stores and outputs these fields. Frontend rendering, sandboxing, script policy, and plain-text extraction for indexing are separate future decisions.

## Identifier Strategy

Every block has a single `BlockNode.id`. The id is generated with UUID and stored without hyphen separators, for example `1234567890abcdef1234567890abcdef`. HTML blocks reuse the same value in `attrs.id`; there is no separate `syncId` or `htmlId`.

## Document Domain

The `docpilot-document-service` module is a pure Java domain service module. It does not implement login, registration, user persistence, database adapters, or REST APIs.

Current package boundaries:

- `model`: mutable JavaBean domain models and enums.
- `auth`: document permission abstractions.
- `repository`: storage ports for documents, workspaces, workspace nodes, and document shares.
- `application`: use-case entry points.
- `processing`: id generators and pure domain helpers.

## User Domain

The `docpilot-user-service` module is the shared user boundary for cloud document features. It keeps platform identity separate from document ownership and sharing.

Current package boundaries:

- `auth`: authenticated subject and current-subject provider.
- `model`: user information DTOs exposed to other modules.
- `provider`: read-side user information lookup boundary.
- `repository`: persistence ports for user information.
- `application`: user information use cases.

Core concepts:

- `Workspace`: a user-owned resource container. A user can own multiple workspaces.
- `WorkspaceNode`: a node inside a workspace tree. First supported node types are `FOLDER` and `DOCUMENT`.
- `DocPilotDocument`: the document content aggregate. It owns Markdown, parsed `BlockDocument`, lifecycle state, and version. It does not own tree placement.
- `DocumentShare`: a document-level share from the owner to a target user. First supported roles are `OWNER`, `EDITOR`, and `VIEWER`.

Application boundaries:

- `WorkspaceManager`: creates owned workspaces and creates/lists first-level resource nodes inside a workspace.
- `DocumentManager`: creates document content aggregates, reads document content, replaces Markdown, reparses blocks, and checks optimistic versions.
- `DocumentShareManager`: creates document-level shares and lists shares received by the current user.

Workspace nodes are intentionally thin in this version. Full tree movement, recursive deletion, renaming rules, permission inheritance, workspace sharing, and team spaces are future design work.

## Next Work

- Add a concrete persistence implementation for `DocumentRepository`.
- Add concrete persistence implementations for workspace, workspace node, and document share repositories.
- Add concrete startup adapters for auth/user providers.
- Add registration/login and persistent user information storage behind `docpilot-user-service`.
- Add AI edit request and patch application primitives.
- Add REST APIs only after the internal block contract is exercised by startup and frontend integration.
