import { describe, expect, it } from 'vitest'
import { formatUpdatedAt } from './DocumentEditor'

describe('formatUpdatedAt', () => {
  it('formats epoch second timestamps through the browser local time zone', () => {
    const value = 1781836200
    const formatter = new Intl.DateTimeFormat('en-US', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    })

    expect(formatUpdatedAt(value, 'en-US')).toBe(formatter.format(new Date(value * 1000)))
  })

  it('returns null for missing or invalid timestamp values', () => {
    expect(formatUpdatedAt(undefined, 'en-US')).toBeNull()
    expect(formatUpdatedAt(Number.NaN, 'en-US')).toBeNull()
  })
})
