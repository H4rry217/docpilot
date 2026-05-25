export type ProseMirrorMark = {
  type: string
  attrs?: Record<string, unknown>
}

export type ProseMirrorNode = {
  type: string
  attrs?: Record<string, unknown>
  content?: ProseMirrorNode[]
  text?: string
  marks?: ProseMirrorMark[]
}
