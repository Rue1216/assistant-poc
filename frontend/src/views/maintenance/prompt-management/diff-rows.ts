import { inlineSegments, type DiffLine, type Segment } from '@/utils/markdown/line-diff'

export type DiffRow =
  | { kind: DiffLine['kind']; text: string; oldNo: number | null; newNo: number | null; segments?: Segment[] }
  | { kind: 'gap'; count: number }

const CONTEXT = 2
/** Unchanged text up to this many lines is shown in full when nothing changed. */
const SHORT = 3

type NumberedRow = Extract<DiffRow, { kind: DiffLine['kind'] }>

/** In each run of changes (removed first, then added) the k-th removed line pairs with the k-th added line. */
function pairChangedLines(rows: NumberedRow[]): void {
  let i = 0
  while (i < rows.length) {
    if (rows[i]!.kind === 'same') {
      i++
      continue
    }
    const removed: NumberedRow[] = []
    const added: NumberedRow[] = []
    for (; i < rows.length && rows[i]!.kind !== 'same'; i++) {
      const row = rows[i]!
      ;(row.kind === 'removed' ? removed : added).push(row)
    }
    for (let k = 0; k < Math.min(removed.length, added.length); k++) {
      const seg = inlineSegments(removed[k]!.text, added[k]!.text)
      removed[k]!.segments = seg.before
      added[k]!.segments = seg.after
    }
  }
}

/** Numbers each line (old / new side) and folds unchanged runs into "⋯ N 行未變更" gaps. */
export function diffRows(lines: DiffLine[]): DiffRow[] {
  let oldNo = 0
  let newNo = 0
  const numbered: NumberedRow[] = lines.map((l) => ({
    kind: l.kind,
    text: l.text,
    oldNo: l.kind === 'added' ? null : ++oldNo,
    newNo: l.kind === 'removed' ? null : ++newNo,
  }))

  pairChangedLines(numbered)

  if (numbered.every((r) => r.kind === 'same')) {
    return numbered.length <= SHORT ? numbered : numbered.length ? [{ kind: 'gap', count: numbered.length }] : []
  }

  const out: DiffRow[] = []
  let i = 0
  while (i < numbered.length) {
    if (numbered[i]!.kind !== 'same') {
      out.push(numbered[i++]!)
      continue
    }
    let end = i
    while (end < numbered.length && numbered[end]!.kind === 'same') end++
    const run = numbered.slice(i, end)
    const head = i === 0 ? 0 : CONTEXT // context after the previous change
    const tail = end === numbered.length ? 0 : CONTEXT // context before the next change
    const hidden = run.length - head - tail
    if (hidden >= 2) {
      out.push(...run.slice(0, head), { kind: 'gap', count: hidden }, ...run.slice(run.length - tail))
    } else {
      out.push(...run)
    }
    i = end
  }
  return out
}

export function diffStats(lines: DiffLine[]): { added: number; removed: number } {
  return {
    added: lines.filter((l) => l.kind === 'added').length,
    removed: lines.filter((l) => l.kind === 'removed').length,
  }
}
