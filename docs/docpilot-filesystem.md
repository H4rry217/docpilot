# DocPilot Filesystem

DocPilot Filesystem is the path-first access layer for DocPilot content. It lets services and agents work with documents and external storage through one familiar shape: paths, directories, files, search, and retrieval.

## Why It Exists

DocPilot content does not live in only one place. Some content comes from workspace documents, some may come from local storage, and some may come from S3-compatible object storage. Higher-level features should not need to know those storage details every time they list, read, search, or retrieve content.

DocPilot Filesystem gives the rest of the system a consistent content boundary. A caller can ask for a path, and the filesystem layer decides which mounted source owns it.

## Core Concepts

### Path-First Access

The main idea is simple: content is addressed by path. A path can point to a folder-like workspace node, a document snapshot, a local file, or an object from a remote provider.

This makes the boundary useful for user-facing features and AI-facing tools. Both can talk about content as a tree instead of learning every backing store.

### Mounts

A composite filesystem can mount several child filesystems into one virtual tree. For example, one branch can represent workspace documents while another branch points to provider-backed storage.

Mounts are resolved by the most specific path prefix. That lets a deeper mount override a broader parent path while still keeping the whole tree navigable.

### Capabilities

Each mount has an explicit capability set. A child filesystem might be able to write or delete, but the mount can expose only read-oriented behavior if that is what the caller should be allowed to do.

Read-only mounts currently cover listing, reading, stat information, literal search, semantic retrieval, and read URLs. Write, delete, copy, and move are opt-in capabilities.

This keeps permissions close to the boundary where paths enter the composed tree.

### Providers And Workspace Content

Providers expose storage systems such as the local filesystem or S3-compatible object storage.

Workspace content is different: it comes from DocPilot workspace nodes and document snapshots. The workspace filesystem presents those nodes as directories and files, while still using the workspace repositories as the source of truth.

The result is one path model over several kinds of content.

### Search And Retrieval

The filesystem layer supports two different ways to find content:

- literal search, which looks for text matches in files
- semantic retrieval, which asks for relevant snippets for a query

Workspace retrieval uses the knowledge retrieval service when it is configured. The filesystem layer still controls the path scope, so retrieval results are mapped back to caller-visible paths and filtered to the requested subtree.

### Read URLs

Some mounted sources can expose a URL for reading content directly. This is optional. Callers should treat read URLs as an extra capability rather than something every path will have.

## Where It Fits

DocPilot Filesystem sits below workspace-facing and agent-facing features. It gives those features a common way to list, read, inspect, search, and retrieve content without embedding provider-specific logic everywhere.

It does not replace the workspace database, the knowledge index, or authentication. Those systems remain the source of truth for their own concerns. The filesystem layer gives them a path-shaped access surface.

## Current Scope

DocPilot Filesystem currently covers:

- virtual paths and path normalization
- composite mounts
- capability enforcement at the mount boundary
- local and S3-compatible providers
- workspace document access through a filesystem view
- listing, reading, stat, glob, literal grep, semantic retrieval, and optional read URLs

It is not responsible for:

- deciding who owns a workspace
- storing workspace metadata
- building the knowledge index
- guaranteeing every mount is writable
- hiding provider failures behind unrelated behavior

## Working Model

Think of DocPilot Filesystem as a map:

- paths are the addresses
- mounts decide which source owns each address
- capabilities decide what a caller may do there
- providers and workspace filesystems fetch the actual content
- search and retrieval make that content discoverable

That model is the important part to preserve as the module grows.
