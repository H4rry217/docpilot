# DocPilot Block

DocPilot Block is the document structure layer for DocPilot. It turns Markdown into a stable document shape that the editor, backend services, and AI features can reason about without treating the whole file as one long string.

## Why It Exists

Markdown is comfortable for people, but it is not a very precise boundary for software. A heading, a table, a callout, a code block, and a footnote all carry different meaning. DocPilot Block gives those parts names, identity, attributes, and children so the rest of the system can edit, render, compare, search, and transform documents in smaller reliable pieces.

The module is not the visual editor and it is not the database. It is the shared document model that sits between plain Markdown text and richer application workflows.

## Core Concepts

### Blocks

A block is a top-level piece of a document, such as a paragraph, heading, list, table, code block, math block, diagram, callout, footnote definition, definition list, table of contents marker, link reference definition, or raw HTML block.

Each block can carry:

- an id, so the same document part can be tracked across processing steps
- a type, so callers know what kind of content they are handling
- attributes, such as heading level, code language, table alignment, callout kind, or HTML display settings
- child blocks or inline content, depending on the block type
- optional source position information from the original Markdown

### Inlines And Marks

Inline content represents the text-level pieces inside a block: text, line breaks, images, inline math, footnote references, emoji, and inline HTML.

Marks describe styling or meaning layered over inline text, such as bold, italic, strike, code, link, underline, insert, subscript, superscript, and highlight.

This split keeps the model clear: inline nodes say what the content is, marks say how a span is annotated.

### Typed Views

The generic block model is flexible, but some workflows need a more convenient shape. The typed view gives common blocks a clearer form, such as heading blocks, paragraph blocks, tables, callouts, math blocks, diagrams, and HTML blocks.

The typed view does not replace the base model. It is a safer and more readable way to work with known block types while keeping extension and unsupported content available.

### Round Trip Boundaries

DocPilot Block currently owns three important boundaries:

- Markdown to block document
- block document back to Markdown
- block document to and from ProseMirror-style JSON for the editor surface

The goal is not to make every Markdown extension look identical after a round trip. The goal is to preserve document meaning, keep known structures explicit, and avoid silently dropping unfamiliar content.

### Preservation And Fallbacks

The module keeps more than basic Markdown. Current support includes front matter, tables, task lists, math, diagrams, callouts, footnotes, definition lists, table of contents markers, link reference definitions, raw HTML, and common inline marks.

When the parser sees content that DocPilot does not yet model in detail, it is represented as extension or unsupported content instead of being discarded. That gives the application room to display, store, and later upgrade the content model.

### HTML Handling

Raw HTML is treated as content that needs an explicit display contract. HTML blocks preserve their source and carry display attributes such as fixed or automatic height, script allowance, and sandbox-style constraints.

The default posture is conservative. HTML can be preserved, but script execution and rendering behavior must be explicit rather than accidental.

## Where It Fits

In the current project, DocPilot Block is the shared language for document content processing. It is used by backend document workflows and by the editor conversion boundary. Higher layers decide where documents live, who can access them, and which AI behavior should run on top.

DocPilot Block should remain focused on document structure. Storage, authentication, workspace permissions, and provider-specific file access belong elsewhere.

## Current Scope

DocPilot Block is responsible for:

- parsing Markdown into structured document blocks
- normalizing document structure and block identity
- rendering structured documents back to Markdown
- converting between the block model and editor JSON
- offering typed block helpers for common content shapes
- preserving extension and unsupported content safely

It is not responsible for:

- storing documents in MongoDB or any other database
- deciding user permissions
- mounting filesystems or loading external files
- choosing AI prompts or model providers
- implementing the frontend editor UI

## Maintenance Notes

When adding a new document concept, keep the parser, renderer, typed view, editor conversion, and golden fixtures aligned. If a concept can appear in user Markdown, it should either have a first-class model or a deliberate fallback path.
