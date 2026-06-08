import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { BlockDocument } from '../../../entities/block/types'
import type { ProseMirrorNode } from '../../../entities/prosemirror/types'
import { blockDocumentToProseMirrorJson } from './blockDocumentToProseMirror'
import { proseMirrorJsonToBlockDocument } from './proseMirrorToBlockDocument'

type JsonObject = Record<string, unknown>

const fixtureRoot = resolve(process.cwd(), '..', 'test-fixtures', 'document-format')

describe('document format golden fixtures', () => {
  it('converts canonical block json to the shared ProseMirror contract', () => {
    const blockDocument = readFixture<BlockDocument>('mixed.block.json')
    const expectedProseMirror = readFixture<ProseMirrorNode>('mixed.prosemirror.json')

    expect(normalizeJson(blockDocumentToProseMirrorJson(blockDocument))).toEqual(normalizeJson(expectedProseMirror))
  })

  it('converts the shared ProseMirror contract back to canonical block json', () => {
    const proseMirror = readFixture<ProseMirrorNode>('mixed.prosemirror.json')
    const expectedBlockDocument = readFixture<BlockDocument>('mixed.block.json')

    expect(withoutMetadata(proseMirrorJsonToBlockDocument(proseMirror))).toEqual(withoutMetadata(expectedBlockDocument))
  })
})

function readFixture<T>(name: string): T {
  return JSON.parse(readFileSync(resolve(fixtureRoot, name), 'utf8')) as T
}

function withoutMetadata(document: BlockDocument): Omit<BlockDocument, 'metadata'> {
  return {
    schemaVersion: document.schemaVersion,
    blocks: document.blocks
  }
}

function normalizeJson(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(normalizeJson)
  }

  if (!isObject(value)) {
    return value
  }

  const entries = Object.entries(value)
    .map(([key, child]) => [key, normalizeJson(child)] as const)
    .filter(([key, child]) => !isEmptyContainerField(key, child))

  return Object.fromEntries(entries)
}

function isEmptyContainerField(key: string, value: unknown): boolean {
  return ['attrs', 'content', 'marks'].includes(key) && (
    (Array.isArray(value) && value.length === 0)
    || (isObject(value) && Object.keys(value).length === 0)
  )
}

function isObject(value: unknown): value is JsonObject {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
