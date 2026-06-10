import type {
  DocumentBlockContextItem,
  DocumentOperationDiagnostic,
  DocumentQuery,
  DocumentQueryMatch,
  DocumentQueryResult
} from './types'
import {
  blockPlainText,
  cloneBlock,
  collectBlockLocations,
  findBlockLocation,
  findTextMatches,
  stableBlockHash,
  type BlockLocation
} from './documentTree'
import type { BlockDocument, BlockNode } from '../types'

export function queryBlockDocument(document: BlockDocument, query: DocumentQuery): DocumentQueryResult {
  switch (query.type) {
    case 'getBlock':
      return getBlock(document, query)
    case 'searchText':
      return searchText(document, query)
    case 'getBlockContext':
      return getBlockContext(document, query)
    default:
      return {
        query,
        diagnostics: [diagnostic('invalid_operation', `Unsupported document query: ${(query as DocumentQuery).type}`)]
      }
  }
}

function getBlock(document: BlockDocument, query: Extract<DocumentQuery, { type: 'getBlock' }>): DocumentQueryResult {
  const location = findBlockLocation(document, query.blockId)

  if (!location) {
    return {
      query,
      diagnostics: [diagnostic('block_not_found', `Block "${query.blockId}" was not found.`, query.blockId)]
    }
  }

  return {
    query,
    diagnostics: [],
    block: cloneBlock(location.block),
    blockHash: stableBlockHash(location.block),
    parentBlockId: location.parentBlockId,
    path: location.path
  }
}

function searchText(document: BlockDocument, query: Extract<DocumentQuery, { type: 'searchText' }>): DocumentQueryResult {
  const matches: DocumentQueryMatch[] = []
  const limit = query.limit == null ? Number.POSITIVE_INFINITY : Math.max(0, query.limit)

  for (const location of collectBlockLocations(document)) {
    if (query.blockTypes && !query.blockTypes.includes(location.block.type)) continue

    const blockText = blockPlainText(location.block)
    for (const match of findTextMatches(blockText, query.text, query.caseSensitive)) {
      if (matches.length >= limit) break
      matches.push({
        blockId: location.block.id,
        blockType: location.block.type,
        path: location.path,
        range: {
          blockId: location.block.id,
          startOffset: match.startOffset,
          endOffset: match.endOffset
        },
        text: match.text
      })
    }

    if (matches.length >= limit) break
  }

  return {
    query,
    diagnostics: [],
    matches
  }
}

function getBlockContext(
  document: BlockDocument,
  query: Extract<DocumentQuery, { type: 'getBlockContext' }>
): DocumentQueryResult {
  const location = findBlockLocation(document, query.blockId)

  if (!location) {
    return {
      query,
      diagnostics: [diagnostic('block_not_found', `Block "${query.blockId}" was not found.`, query.blockId)]
    }
  }

  const beforeCount = Math.max(0, query.before ?? 1)
  const afterCount = Math.max(0, query.after ?? 1)
  const startIndex = Math.max(0, location.index - beforeCount)
  const endIndex = Math.min(location.parentBlocks.length - 1, location.index + afterCount)
  const context: DocumentBlockContextItem[] = []

  for (let index = startIndex; index <= endIndex; index += 1) {
    const block = location.parentBlocks[index]
    context.push(contextItem(block, relationForIndex(index, location.index), pathForSibling(location, index)))
  }

  return {
    query,
    diagnostics: [],
    block: cloneBlock(location.block),
    blockHash: stableBlockHash(location.block),
    parentBlockId: location.parentBlockId,
    path: location.path,
    context
  }
}

function contextItem(block: BlockNode, relation: DocumentBlockContextItem['relation'], path: number[]) {
  return {
    relation,
    blockId: block.id,
    blockType: block.type,
    path,
    text: blockPlainText(block),
    blockHash: stableBlockHash(block)
  }
}

function relationForIndex(index: number, targetIndex: number): DocumentBlockContextItem['relation'] {
  if (index < targetIndex) return 'before'
  if (index > targetIndex) return 'after'
  return 'target'
}

function pathForSibling(location: BlockLocation, siblingIndex: number): number[] {
  return [...location.path.slice(0, -1), siblingIndex]
}

function diagnostic(
  code: DocumentOperationDiagnostic['code'],
  message: string,
  blockId?: string
): DocumentOperationDiagnostic {
  return {
    severity: 'error',
    code,
    message,
    blockId
  }
}
