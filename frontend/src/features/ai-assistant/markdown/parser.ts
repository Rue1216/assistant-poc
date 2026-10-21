// Minimal, pure Markdown subset parser for untrusted model output.
// Raw HTML is never interpreted: `<` etc. stay ordinary text. Entities are not decoded.
// Headings deeper than ### are rendered as level 3. Images are rendered as their alt text.
// Table rows shorter than the header are padded with empty cells; longer rows keep their extra cells.

export type Inline =
  | { type: 'text'; value: string }
  | { type: 'strong'; children: Inline[] }
  | { type: 'em'; children: Inline[] }
  | { type: 'codespan'; value: string }
  | { type: 'link'; href: string; children: Inline[] }

export type MdNode =
  | { type: 'heading'; level: 1 | 2 | 3; children: Inline[] }
  | { type: 'paragraph'; children: Inline[] }
  | { type: 'list'; ordered: boolean; items: MdNode[][] }
  | { type: 'blockquote'; children: MdNode[] }
  | { type: 'code'; text: string }
  | { type: 'table'; header: Inline[][]; rows: Inline[][][] }

/** 0-based, inclusive source line range of a block, list item or table row. */
export interface LineRange {
  start: number
  end: number
}

/** Line ranges keyed by node: block nodes, list items (MdNode[]), table header and rows (Inline[][]). */
export type LineMap = Map<object, LineRange>

const MAX_DEPTH = 4

const FENCE = /^\s{0,3}```/
const HEADING = /^\s{0,3}(#{1,6})\s+(.*)$/
const LIST_ITEM = /^\s*([-*]|\d+\.)\s+(.*)$/
const QUOTE = /^\s{0,3}>\s?(.*)$/
const SEP_CELL = /^:?-+:?$/

// Linear-time separator check (no regex with adjacent whitespace quantifiers).
function isSeparatorRow(line: string): boolean {
  let s = line.trim()
  if (s.startsWith('|')) s = s.slice(1)
  if (s.endsWith('|')) s = s.slice(0, -1)
  return s.split('|').every((c) => SEP_CELL.test(c.trim()))
}

// Strip an optional closing run of '#' that is preceded by whitespace.
function headingText(raw: string): string {
  const s = raw.trimEnd()
  let end = s.length
  while (end > 0 && s[end - 1] === '#') end--
  if (end < s.length && (end === 0 || /\s/.test(s[end - 1]!))) return s.slice(0, end).trimEnd()
  return s
}

function isTableStart(lines: string[], i: number): boolean {
  const head = lines[i] ?? ''
  const sep = lines[i + 1]
  return head.includes('|') && sep !== undefined && sep.includes('|') && isSeparatorRow(sep)
}

function splitRow(line: string): string[] {
  let s = line.trim()
  if (s.startsWith('|')) s = s.slice(1)
  if (s.endsWith('|') && !s.endsWith('\\|')) s = s.slice(0, -1)
  const cells: string[] = []
  let cur = ''
  for (let i = 0; i < s.length; i++) {
    const ch = s[i]!
    if (ch === '\\' && s[i + 1] === '|') {
      cur += '|'
      i++
    } else if (ch === '|') {
      cells.push(cur.trim())
      cur = ''
    } else {
      cur += ch
    }
  }
  cells.push(cur.trim())
  return cells
}

function startsBlock(lines: string[], i: number): boolean {
  const l = lines[i] ?? ''
  return FENCE.test(l) || HEADING.test(l) || LIST_ITEM.test(l) || QUOTE.test(l) || isTableStart(lines, i)
}

/**
 * `offset` is the source line of `src`'s first line; `ranges`, when given, receives the line range of
 * every block, list item and table row (the node shapes stay the same either way).
 */
function parseBlocks(src: string, depth: number, offset = 0, ranges: LineMap | null = null): MdNode[] {
  const lines = src.replace(/\r\n?/g, '\n').split('\n')
  const out: MdNode[] = []
  const at = <T extends object>(node: T, start: number, end: number): T => {
    ranges?.set(node, { start: offset + start, end: offset + Math.min(end, lines.length - 1) })
    return node
  }
  let i = 0
  while (i < lines.length) {
    const line = lines[i]!
    if (line.trim() === '') {
      i++
      continue
    }
    const start = i
    if (FENCE.test(line)) {
      const body: string[] = []
      i++
      while (i < lines.length && !FENCE.test(lines[i]!)) body.push(lines[i++]!)
      i++ // closing fence (or EOF)
      out.push(at({ type: 'code', text: body.join('\n') }, start, i - 1))
      continue
    }
    const h = HEADING.exec(line)
    if (h) {
      const level = Math.min(h[1]!.length, 3) as 1 | 2 | 3
      out.push(at({ type: 'heading', level, children: parseInline(headingText(h[2]!), 0) }, start, start))
      i++
      continue
    }
    if (isTableStart(lines, i)) {
      const header = at(
        splitRow(line).map((c) => parseInline(c, 0)),
        start,
        start,
      )
      i += 2
      const rows: Inline[][][] = []
      while (i < lines.length && lines[i]!.trim() !== '' && lines[i]!.includes('|')) {
        const cells = splitRow(lines[i]!).map((c) => parseInline(c, 0))
        while (cells.length < header.length) cells.push([])
        rows.push(at(cells, i, i))
        i++
      }
      out.push(at({ type: 'table', header, rows }, start, i - 1))
      continue
    }
    if (QUOTE.test(line)) {
      const inner: string[] = []
      while (i < lines.length && QUOTE.test(lines[i]!)) {
        inner.push(QUOTE.exec(lines[i]!)![1]!)
        i++
      }
      const text = inner.join('\n')
      const children: MdNode[] =
        depth >= MAX_DEPTH
          ? [at({ type: 'paragraph', children: [{ type: 'text', value: text }] }, start, i - 1)]
          : parseBlocks(text, depth + 1, offset + start, ranges) // quoted lines map 1:1 to source lines
      out.push(at({ type: 'blockquote', children }, start, i - 1))
      continue
    }
    const li = LIST_ITEM.exec(line)
    if (li) {
      const ordered = /^\d/.test(li[1]!)
      const items: MdNode[][] = []
      while (i < lines.length) {
        const m = LIST_ITEM.exec(lines[i]!)
        if (!m || /^\d/.test(m[1]!) !== ordered) break
        const itemStart = i
        const parts = [m[2]!]
        i++
        while (i < lines.length && lines[i]!.trim() !== '' && !startsBlock(lines, i)) parts.push(lines[i++]!.trim())
        const paragraph: MdNode = { type: 'paragraph', children: parseInline(parts.join('\n'), 0) }
        items.push(at([at(paragraph, itemStart, i - 1)], itemStart, i - 1))
      }
      out.push(at({ type: 'list', ordered, items }, start, i - 1))
      continue
    }
    const parts = [line.trim()]
    i++
    while (i < lines.length && lines[i]!.trim() !== '' && !startsBlock(lines, i)) parts.push(lines[i++]!.trim())
    out.push(at({ type: 'paragraph', children: parseInline(parts.join('\n'), 0) }, start, i - 1))
  }
  return out
}

function pushText(out: Inline[], value: string) {
  if (!value) return
  const last = out[out.length - 1]
  if (last && last.type === 'text') last.value += value
  else out.push({ type: 'text', value })
}

function matchLink(s: string, start: number): { text: string; href: string; end: number } | null {
  const close = s.indexOf(']', start + 1)
  if (close < 0 || s[close + 1] !== '(') return null
  // find the matching ')' allowing balanced parentheses inside the URL (linear scan)
  let paren = -1
  let nest = 0
  for (let k = close + 2; k < s.length; k++) {
    const c = s[k]
    if (c === '(') nest++
    else if (c === ')') {
      if (nest === 0) {
        paren = k
        break
      }
      nest--
    }
  }
  if (paren < 0) return null
  const href = s.slice(close + 2, paren).trim().split(/\s+/)[0] ?? ''
  return { text: s.slice(start + 1, close), href, end: paren + 1 }
}

function parseInline(s: string, depth: number): Inline[] {
  const out: Inline[] = []
  if (depth > MAX_DEPTH) {
    pushText(out, s)
    return out
  }
  let i = 0
  while (i < s.length) {
    const ch = s[i]!
    if (ch === '`') {
      let n = 1
      while (s[i + n] === '`') n++
      const fence = '`'.repeat(n)
      const end = s.indexOf(fence, i + n)
      if (end > i + n) {
        out.push({ type: 'codespan', value: s.slice(i + n, end).trim() })
        i = end + n
        continue
      }
      pushText(out, fence)
      i += n
      continue
    }
    if (ch === '!' && s[i + 1] === '[') {
      const link = matchLink(s, i + 1)
      if (link) {
        pushText(out, link.text) // image: alt text only, never an <img>
        i = link.end
        continue
      }
    }
    if (ch === '[') {
      const link = matchLink(s, i)
      if (link) {
        out.push({ type: 'link', href: link.href, children: parseInline(link.text, depth + 1) })
        i = link.end
        continue
      }
    }
    if (ch === '*' && s[i + 1] === '*') {
      const end = s.indexOf('**', i + 2)
      if (end > i + 2) {
        out.push({ type: 'strong', children: parseInline(s.slice(i + 2, end), depth + 1) })
        i = end + 2
        continue
      }
    }
    if (ch === '*' || (ch === '_' && !/[\p{L}\p{N}]/u.test(s[i - 1] ?? ''))) {
      const end = s.indexOf(ch, i + 1)
      if (end > i + 1 && !/\s/.test(s[i + 1]!) && s[i + 1] !== ch) {
        out.push({ type: 'em', children: parseInline(s.slice(i + 1, end), depth + 1) })
        i = end + 1
        continue
      }
    }
    pushText(out, ch)
    i++
  }
  return out
}

/** Like parseMarkdown, plus the source line range of every block, list item and table row. */
export function parseMarkdownWithLines(src: string): { nodes: MdNode[]; lines: LineMap } {
  const lines: LineMap = new Map()
  try {
    return { nodes: parseBlocks(typeof src === 'string' ? src : '', 0, 0, lines), lines }
  } catch {
    return { nodes: parseMarkdown(src), lines: new Map() }
  }
}

export function parseMarkdown(src: string): MdNode[] {
  try {
    return parseBlocks(typeof src === 'string' ? src : '', 0)
  } catch {
    return [{ type: 'paragraph', children: [{ type: 'text', value: String(src ?? '') }] }]
  }
}
