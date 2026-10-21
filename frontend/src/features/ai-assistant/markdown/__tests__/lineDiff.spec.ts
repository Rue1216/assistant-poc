import { describe, expect, it } from 'vitest'
import { addedLineIndices, diffLines, DiffTooLargeError } from '../lineDiff'

describe('diffLines', () => {
  it('marks added, removed and unchanged lines', () => {
    expect(diffLines('a\nb\nc', 'a\nc\nd')).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'removed', text: 'b' },
      { kind: 'same', text: 'c' },
      { kind: 'added', text: 'd' },
    ])
  })

  it('handles empty inputs', () => {
    expect(diffLines('', '')).toEqual([])
    expect(diffLines('', 'x\ny')).toEqual([
      { kind: 'added', text: 'x' },
      { kind: 'added', text: 'y' },
    ])
    expect(diffLines('x', '')).toEqual([{ kind: 'removed', text: 'x' }])
  })

  it('lists a replaced line as removed then added', () => {
    expect(diffLines('a\nold\nz', 'a\nnew\nz')).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'removed', text: 'old' },
      { kind: 'added', text: 'new' },
      { kind: 'same', text: 'z' },
    ])
  })

  it('keeps the longest common subsequence unchanged', () => {
    const result = diffLines('x\na\nb\nc', 'a\nb\ny\nc')
    expect(result.filter((l) => l.kind === 'same').map((l) => l.text)).toEqual(['a', 'b', 'c'])
    expect(result.filter((l) => l.kind === 'removed').map((l) => l.text)).toEqual(['x'])
    expect(result.filter((l) => l.kind === 'added').map((l) => l.text)).toEqual(['y'])
  })

  it('treats CRLF like LF and keeps blank lines', () => {
    expect(diffLines('a\r\n\r\nb', 'a\n\nb')).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'same', text: '' },
      { kind: 'same', text: 'b' },
    ])
  })

  it('handles large inputs with a small change quickly', () => {
    const before = Array.from({ length: 3000 }, (_, i) => `line ${i}`).join('\n')
    const after = before.replace('line 1500', 'changed')
    const started = Date.now()
    const result = diffLines(before, after)
    expect(Date.now() - started).toBeLessThan(1000)
    expect(result.filter((l) => l.kind !== 'same')).toEqual([
      { kind: 'removed', text: 'line 1500' },
      { kind: 'added', text: 'changed' },
    ])
  })
})

describe('diffLines limits', () => {
  it('diffs ~2000 x 2000 fully different lines correctly within a time bound', () => {
    const before = Array.from({ length: 2000 }, (_, i) => `old ${i}`).join('\n')
    const after = Array.from({ length: 2000 }, (_, i) => `new ${i}`).join('\n')
    const started = Date.now()
    const result = diffLines(before, after)
    expect(Date.now() - started).toBeLessThan(2000)
    expect(result.filter((l) => l.kind === 'removed')).toHaveLength(2000)
    expect(result.filter((l) => l.kind === 'added')).toHaveLength(2000)
    expect(result.filter((l) => l.kind === 'same')).toHaveLength(0)
    expect(result[0]).toEqual({ kind: 'removed', text: 'old 0' })
    expect(result.at(-1)).toEqual({ kind: 'added', text: 'new 1999' })
  })

  it('refuses a changed middle part above the cell cap with DiffTooLargeError', () => {
    const before = Array.from({ length: 2001 }, (_, i) => `a ${i}`).join('\n')
    const after = Array.from({ length: 2001 }, (_, i) => `b ${i}`).join('\n')
    expect(() => diffLines(before, after)).toThrow(DiffTooLargeError)
    // the cap applies after trimming the common prefix and suffix
    const big = Array.from({ length: 5000 }, (_, i) => `same ${i}`).join('\n')
    expect(() => diffLines(big, big + '\nextra')).not.toThrow()
  })
})

describe('addedLineIndices', () => {
  it('returns 0-based indices of lines only in the new text', () => {
    expect([...addedLineIndices('a\nb\nc', 'a\nx\nc\nd')]).toEqual([1, 3])
    expect(addedLineIndices('same', 'same').size).toBe(0)
  })
})
