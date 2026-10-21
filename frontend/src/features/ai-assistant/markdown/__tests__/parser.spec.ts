import { describe, expect, it } from 'vitest'
import { parseMarkdown, parseMarkdownWithLines, type MdNode } from '../parser'

describe('parseMarkdown', () => {
  it('parses headings, lists, bold, table, fenced code', () => {
    const src = [
      '## 標題',
      '',
      '這是 **重點** 文字',
      '',
      '- 甲',
      '- 乙',
      '',
      '1. 一',
      '2. 二',
      '',
      '| 商品 | 金額 |',
      '| --- | ---: |',
      '| 基金 | 100 |',
      '',
      '```',
      'const a = 1',
      '```',
    ].join('\n')
    const nodes = parseMarkdown(src)
    expect(nodes.map((n) => n.type)).toEqual(['heading', 'paragraph', 'list', 'list', 'table', 'code'])
    expect(nodes[0]).toMatchObject({ type: 'heading', level: 2 })
    const p = nodes[1] as Extract<MdNode, { type: 'paragraph' }>
    expect(p.children.some((c) => c.type === 'strong')).toBe(true)
    expect(nodes[2]).toMatchObject({ type: 'list', ordered: false })
    expect((nodes[2] as Extract<MdNode, { type: 'list' }>).items).toHaveLength(2)
    expect(nodes[3]).toMatchObject({ type: 'list', ordered: true })
    const t = nodes[4] as Extract<MdNode, { type: 'table' }>
    expect(t.header).toHaveLength(2)
    expect(t.rows).toHaveLength(1)
    expect(nodes[5]).toEqual({ type: 'code', text: 'const a = 1' })
  })

  it('caps heading level at 3', () => {
    expect(parseMarkdown('###### x')[0]).toMatchObject({ type: 'heading', level: 3 })
  })

  it('parses blockquote, em, codespan and link', () => {
    const nodes = parseMarkdown('> 引用 *斜* _體_ `c` [t](https://a.b)')
    expect(nodes[0]!.type).toBe('blockquote')
    const inl = ((nodes[0] as Extract<MdNode, { type: 'blockquote' }>).children[0] as Extract<MdNode, { type: 'paragraph' }>).children
    expect(inl.filter((c) => c.type === 'em')).toHaveLength(2)
    expect(inl.some((c) => c.type === 'codespan')).toBe(true)
    expect(inl.some((c) => c.type === 'link' && c.href === 'https://a.b')).toBe(true)
  })

  it('tolerates table rows with fewer or more cells than the header', () => {
    const nodes = parseMarkdown('| a | b | c |\n|---|---|---|\n| 1 |\n| 1 | 2 | 3 | 4 |')
    const t = nodes[0] as Extract<MdNode, { type: 'table' }>
    expect(t.rows[0]).toHaveLength(3)
    expect(t.rows[1]!.length).toBeGreaterThanOrEqual(3)
  })

  it('parses long whitespace runs in linear time', () => {
    const spaces = ' '.repeat(20000)
    let t = performance.now()
    const h = parseMarkdown('# a' + spaces + 'x')
    expect(performance.now() - t).toBeLessThan(200)
    expect(h[0]).toMatchObject({ type: 'heading', level: 1 })

    t = performance.now()
    const tbl = parseMarkdown('| a | b |\n|---|' + spaces + '---|')
    expect(performance.now() - t).toBeLessThan(200)
    expect(tbl[0]!.type).toBe('table')

    t = performance.now()
    parseMarkdown('| a | b |\n|' + spaces + 'x')
    expect(performance.now() - t).toBeLessThan(200)
  })

  it('strips closing hashes from headings only when preceded by whitespace', () => {
    const text = (s: string) => ((parseMarkdown(s)[0] as { children: { value: string }[] }).children[0]!.value)
    expect(text('## 標題 ##')).toBe('標題')
    expect(text('# C#')).toBe('C#')
  })

  it('allows balanced parentheses in link urls', () => {
    const p = parseMarkdown('[w](https://a.b/x_(y)) z')[0] as { children: { type: string; href?: string }[] }
    expect(p.children[0]).toMatchObject({ type: 'link', href: 'https://a.b/x_(y)' })
  })

  it.each([
    '','   \n\t\n', '```\nunclosed', '**unclosed', '*x', '_x', '`x', '[a', '[a](', '[a](b', '![', '![a](', '|', '| a |', '| a |\n|---|', '>', '> > > >', '-', '1.', '#', '####### ',
    '>'.repeat(5000), '['.repeat(5000), '*'.repeat(5001), '**a'.repeat(2000), '[a](b)'.repeat(2000),
  ])('never throws on malformed input %#', (src) => {
    expect(() => parseMarkdown(src)).not.toThrow()
  })
})

describe('parseMarkdownWithLines', () => {
  const src = [
    '## 標題', //            0
    '', //                   1
    '第一行', //             2
    '接續第二行', //         3
    '', //                   4
    '- 甲', //               5
    '- 乙', //               6
    '  乙的續行', //         7
    '', //                   8
    '| 欄 | 值 |', //        9
    '| --- | --- |', //     10
    '| a | 1 |', //         11
    '', //                  12
    '> 引言', //            13
    '> - 引言清單', //      14
    '', //                  15
    '```', //               16
    'code', //              17
    '```', //               18
  ].join('\r\n')

  it('records 0-based inclusive source line ranges of blocks, list items and table rows', () => {
    const { nodes, lines } = parseMarkdownWithLines(src)
    const [heading, paragraph, list, table, quote, code] = nodes as [
      MdNode,
      MdNode,
      Extract<MdNode, { type: 'list' }>,
      Extract<MdNode, { type: 'table' }>,
      Extract<MdNode, { type: 'blockquote' }>,
      MdNode,
    ]
    expect(lines.get(heading)).toEqual({ start: 0, end: 0 })
    expect(lines.get(paragraph)).toEqual({ start: 2, end: 3 })
    expect(lines.get(list)).toEqual({ start: 5, end: 7 })
    expect(lines.get(list.items[0]!)).toEqual({ start: 5, end: 5 })
    expect(lines.get(list.items[1]!)).toEqual({ start: 6, end: 7 })
    expect(lines.get(list.items[1]![0]!)).toEqual({ start: 6, end: 7 })
    expect(lines.get(table.header)).toEqual({ start: 9, end: 9 })
    expect(lines.get(table.rows[0]!)).toEqual({ start: 11, end: 11 })
    expect(lines.get(quote)).toEqual({ start: 13, end: 14 })
    // nested blocks keep positions in the outer source
    expect(lines.get(quote.children[0]!)).toEqual({ start: 13, end: 13 })
    const innerList = quote.children[1] as Extract<MdNode, { type: 'list' }>
    expect(lines.get(innerList.items[0]!)).toEqual({ start: 14, end: 14 })
    expect(lines.get(code)).toEqual({ start: 16, end: 18 })
  })

  it('returns the same nodes as parseMarkdown', () => {
    expect(parseMarkdownWithLines(src).nodes).toEqual(parseMarkdown(src))
  })
})
