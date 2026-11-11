import { describe, expect, it } from 'vitest'
import { diffLines } from '@/utils/markdown/line-diff'
import { diffRows, diffStats } from '@/views/maintenance/prompt-management/diff-rows'

const text = (n: number, prefix = 'l') => Array.from({ length: n }, (_, i) => `${prefix}${i + 1}`).join('\n')

describe('diffRows', () => {
  it('numbers old and new lines', () => {
    expect(diffRows(diffLines('a\nb', 'a\nc'))).toEqual([
      { kind: 'same', text: 'a', oldNo: 1, newNo: 1 },
      { kind: 'removed', text: 'b', oldNo: 2, newNo: null, segments: [{ text: 'b', changed: true }] },
      { kind: 'added', text: 'c', oldNo: null, newNo: 2, segments: [{ text: 'c', changed: true }] },
    ])
  })

  it('collapses long unchanged runs, keeping two lines of context around changes', () => {
    const before = text(20)
    const after = before.replace('l10', 'X')
    const rows = diffRows(diffLines(before, after))
    expect(rows[0]).toEqual({ kind: 'gap', count: 7 })
    expect(rows.slice(1, 3).map((r) => (r.kind === 'gap' ? '' : r.text))).toEqual(['l8', 'l9'])
    expect(rows.slice(3, 5).map((r) => r.kind)).toEqual(['removed', 'added'])
    expect(rows.slice(5, 7).map((r) => (r.kind === 'gap' ? '' : r.text))).toEqual(['l11', 'l12'])
    expect(rows[7]).toEqual({ kind: 'gap', count: 8 })
    expect(rows).toHaveLength(8)
  })

  it('does not hide a single line behind a gap marker', () => {
    const rows = diffRows(diffLines(text(4), text(4).replace('l4', 'X')))
    // l1 would be the only hidden line: shown instead
    expect(rows.every((r) => r.kind !== 'gap')).toBe(true)
  })

  it('shows short unchanged text in full and collapses long unchanged text', () => {
    expect(diffRows(diffLines('名稱', '名稱'))).toEqual([{ kind: 'same', text: '名稱', oldNo: 1, newNo: 1 }])
    expect(diffRows(diffLines(text(10), text(10)))).toEqual([{ kind: 'gap', count: 10 }])
  })

  it('counts added and removed lines', () => {
    expect(diffStats(diffLines('a\nb\nc', 'a\nx\ny\nc'))).toEqual({ added: 2, removed: 1 })
  })

  it('pairs the k-th removed with the k-th added line and leaves the rest unpaired', () => {
    const rows = diffRows([
      { kind: 'same', text: 'x' },
      { kind: 'removed', text: 'ab' },
      { kind: 'removed', text: 'zz' },
      { kind: 'added', text: 'ac' },
      { kind: 'same', text: 'y' },
    ])
    expect(rows.map((r) => (r.kind === 'gap' ? null : r.segments))).toEqual([
      undefined,
      [
        { text: 'a', changed: false },
        { text: 'b', changed: true },
      ],
      undefined,
      [
        { text: 'a', changed: false },
        { text: 'c', changed: true },
      ],
      undefined,
    ])
  })
})
