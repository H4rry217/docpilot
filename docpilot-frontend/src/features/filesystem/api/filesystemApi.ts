import { postJson } from '../../../shared/api/http'

export type FilesystemRetrieveFailureMode = 'BEST_EFFORT' | 'STRICT'

export type FilesystemRetrievalHit = {
  path: string
  title?: string
  snippet: string
  score?: number
  headingPath: string[]
  metadata: Record<string, string>
}

export type FilesystemDiagnostic = {
  path: string
  code: string
  message: string
}

export type FilesystemRetrieveResponse = {
  hits: FilesystemRetrievalHit[]
  truncated: boolean
  truncationReason?: string
  searchedMounts: number
  diagnostics: FilesystemDiagnostic[]
}

export function retrieveFilesystemContext(input: {
  path?: string
  query?: string
  topK?: number
  maxCharsPerHit?: number
  failureMode?: FilesystemRetrieveFailureMode
}): Promise<FilesystemRetrieveResponse> {
  return postJson('/filesystem/retrieve', input)
}
