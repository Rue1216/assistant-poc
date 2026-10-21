export type DiffLine = { kind: 'same' | 'added' | 'removed'; text: string }

/** Upper bound of LCS table cells (changed lines before x after); about 16 MB and well under a second. */
export const MAX_DIFF_CELLS = 4_000_000

/** The changed middle parts are too long to diff line by line in the browser. */
export class DiffTooLargeError extends Error {
  constructor() {
    super('內容過長，無法逐行比較')
    this.name = 'DiffTooLargeError'
  }
}

function toLines(text: string): string[] {
  return text === '' ? [] : text.replace(/\r\n?/g, '\n').split('\n')
}

/**
 * Line-by-line diff via the longest common subsequence. The common prefix and suffix are trimmed
 * first, so a small edit in a long prompt only runs the O(n*m) table on the changed middle part.
 * Within a changed run, removed lines come before added lines. Throws DiffTooLargeError above MAX_DIFF_CELLS.
 */
export function diffLines(before: string, after: string): DiffLine[] {
  const a = toLines(before)
  const b = toLines(after)

  let start = 0
  while (start < a.length && start < b.length && a[start] === b[start]) start++
  let endA = a.length
  let endB = b.length
  while (endA > start && endB > start && a[endA - 1] === b[endB - 1]) {
    endA--
    endB--
  }

  const midA = a.slice(start, endA)
  const midB = b.slice(start, endB)
  const n = midA.length
  const m = midB.length
  if (n * m > MAX_DIFF_CELLS) throw new DiffTooLargeError()
  const width = m + 1

  // lcs[i * width + j] = LCS length of midA[i..] and midB[j..]
  const lcs = new Uint32Array((n + 1) * width)
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      lcs[i * width + j] =
        midA[i] === midB[j]
          ? lcs[(i + 1) * width + j + 1]! + 1
          : Math.max(lcs[(i + 1) * width + j]!, lcs[i * width + j + 1]!)
    }
  }

  const out: DiffLine[] = a.slice(0, start).map((text) => ({ kind: 'same', text }))
  let i = 0
  let j = 0
  while (i < n && j < m) {
    if (midA[i] === midB[j]) {
      out.push({ kind: 'same', text: midA[i]! })
      i++
      j++
    } else if (lcs[(i + 1) * width + j]! >= lcs[i * width + j + 1]!) {
      out.push({ kind: 'removed', text: midA[i]! })
      i++
    } else {
      out.push({ kind: 'added', text: midB[j]! })
      j++
    }
  }
  while (i < n) out.push({ kind: 'removed', text: midA[i++]! })
  while (j < m) out.push({ kind: 'added', text: midB[j++]! })
  for (const text of a.slice(endA)) out.push({ kind: 'same', text })
  return out
}

/**
 * 0-based indices (in `after`, split like `diffLines`) of the lines that are not in `before`.
 * Throws DiffTooLargeError like `diffLines`.
 */
export function addedLineIndices(before: string, after: string): Set<number> {
  const added = new Set<number>()
  let index = 0
  for (const line of diffLines(before, after)) {
    if (line.kind === 'removed') continue
    if (line.kind === 'added') added.add(index)
    index++
  }
  return added
}
