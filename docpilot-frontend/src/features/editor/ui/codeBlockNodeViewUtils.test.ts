import { describe, expect, it } from 'vitest'
import {
  blockSelectionDecoration,
  codeBlockWidthAttr,
  selectionStyleFromDecoration
} from './codeBlockNodeViewUtils'

describe('codeBlockNodeViewUtils', () => {
  it('clamps persisted special block widths', () => {
    expect(codeBlockWidthAttr('120')).toBe(240)
    expect(codeBlockWidthAttr(960)).toBe(720)
    expect(codeBlockWidthAttr('360')).toBe(360)
    expect(codeBlockWidthAttr('nope')).toBeNull()
  })

  it('keeps only selection CSS custom properties', () => {
    expect(selectionStyleFromDecoration('--docpilot-selection-before: 8px; color: red; --other: 1; --docpilot-selection-after: 4px')).toEqual({
      '--docpilot-selection-before': '8px',
      '--docpilot-selection-after': '4px'
    })
  })

  it('reads block selection class and style from decorations', () => {
    const decorations = [
      {
        type: {
          attrs: {
            class: 'docpilot-block-selected other',
            style: '--docpilot-selection-before: 8px; color: red'
          }
        }
      }
    ]

    expect(blockSelectionDecoration(decorations as never)).toEqual({
      isSelected: true,
      style: {
        '--docpilot-selection-before': '8px'
      }
    })
  })
})
