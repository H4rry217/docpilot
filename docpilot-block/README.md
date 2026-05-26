# docpilot-block

`docpilot-block` is the foundational document-structure library for DocPilot. It turns Markdown into DocPilot's own mutable block tree, renders that tree back to Markdown, and converts it to ProseMirror-compatible JSON DTOs for the editor frontend.

## Responsibilities

- Parse Markdown with flexmark-java.
- Preserve a DocPilot-owned block model as the backend document contract.
- Keep source ranges for later AI patch positioning.
- Convert DocPilot blocks to ProseMirror JSON DTO objects.
- Render DocPilot blocks back to normalized Markdown.
- Preserve raw HTML as data, without executing or sanitizing it.

## Package Layout

- `io.docpilot.block.model`: mutable JavaBean domain model.
- `io.docpilot.block.processing`: parser, renderer, ID generator, and ProseMirror JSON converter.
- `io.docpilot.block.prosemirror`: mutable DTO objects used for ProseMirror JSON output.

## Core Model

- `BlockDocument`
  - Root document object.
  - Holds `schemaVersion`, top-level `blocks`, and document `metadata`.
- `BlockNode`
  - A block-level node such as paragraph, heading, list, table, or HTML block.
  - Every block has a UUID-based `id` without hyphens.
  - Uses `attrs` for block-specific fields such as heading level, code language, table alignment, or HTML source.
- `InlineNode`
  - Inline content inside text-like blocks.
  - Supports text, code, link, image, breaks, raw HTML inline, and unsupported inline fallback.
- `SourceRange`
  - Original Markdown source location.
  - Intended for later AI edit/patch workflows.

The model uses JavaBean classes with Lombok `@Getter` and `@Setter`, because later document editing will need to replace or update individual blocks.

## Supported Markdown

The first implementation supports:

- Paragraphs and headings.
- Block quotes.
- Bullet lists and ordered lists.
- GFM task list items.
- Fenced and indented code blocks.
- Thematic breaks.
- GFM tables.
- Bold, italic, and strikethrough marks.
- Links and images.
- Soft and hard breaks.
- Raw HTML block and inline nodes.

Parser nodes that do not yet have a first-class DocPilot representation fall back to `UNSUPPORTED_BLOCK` or `UNSUPPORTED_INLINE`.

## HTML Strategy

HTML is preserved as source data. The backend does not render it, execute scripts, sanitize it, or extract plain text.

HTML block attrs:

- `id`: same value as `BlockNode.id`
- `title`: defaults to `HTML`
- `source`: raw HTML source
- `displayMode`: defaults to `HtmlDisplayMode.FIT`
- `allowScripts`: defaults to `false`

ProseMirror output uses:

```json
{
  "type": "docpilotHtmlBlock",
  "attrs": {
    "id": "1234567890abcdef1234567890abcdef",
    "title": "HTML",
    "source": "<div>hello</div>",
    "displayMode": "fit",
    "allowScripts": false
  }
}
```

Text extraction for search, indexing, or AI context is a later module decision.

## Main Entry Points

```java
MarkdownBlockParser parser = new MarkdownBlockParser();
BlockDocument document = parser.parse(markdown);

ProseMirrorJsonConverter converter = new ProseMirrorJsonConverter();
ProseMirrorNode prosemirrorDoc = converter.toProseMirror(document);

MarkdownBlockRenderer renderer = new MarkdownBlockRenderer();
String normalizedMarkdown = renderer.render(document);
```

## ProseMirror Conversion

`ProseMirrorJsonConverter` converts DocPilot blocks into ProseMirror-like DTOs:

- `PARAGRAPH` -> `paragraph`
- `HEADING` -> `heading`
- `BULLET_LIST` -> `bulletList`
- `ORDERED_LIST` -> `orderedList`
- `TASK_LIST_ITEM` -> `taskItem`
- `CODE_BLOCK` -> `codeBlock`
- `TABLE` -> `table`
- `HTML_BLOCK` -> `docpilotHtmlBlock`

The converter uses string constants internally for node names, mark names, and attr keys.

## Tests

Run this module through the root Maven build:

```bash
mvn test
```

Current tests cover Markdown parsing, GFM tables/task lists, HTML preservation, ProseMirror conversion, and Markdown rendering.
