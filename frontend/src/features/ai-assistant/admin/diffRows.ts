import type { DiffLine } from '../markdown/lineDiff'

export type DiffRow =
  | { kind: DiffLine['kind']; text: string; oldNo: number | null; newNo: number | null }
  | { kind: 'gap'; count: number }

const CONTEXT = 2
/** Unchanged text up to this many lines is shown in full when nothing changed. */
const SHORT = 3

/** Numbers each line (old / new side) and folds unchanged runs into "⋯ N 行未變更" gaps. */
export function diffRows(lines: DiffLine[]): DiffRow[] {
  let oldNo = 0
  let newNo = 0
  const numbered = lines.map((l) => ({
    kind: l.kind,
    text: l.text,
    oldNo: l.kind === 'added' ? null : ++oldNo,
    newNo: l.kind === 'removed' ? null : ++newNo,
  }))

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
