import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SafeMarkdown from '../SafeMarkdown'

const render = (source: string) => mount(SafeMarkdown, { props: { source } })

const XSS = [
  '<script>alert(1)</script>', '<img src=x onerror=alert(1)>', '[x](javascript:alert(1))',
  '[x](JaVaScRiPt:alert(1))', '[x](&#106;avascript:alert(1))', '[x](data:text/html,<script>alert(1)</script>)',
  '[x](vbscript:msgbox(1))', '<a href="javascript:alert(1)">x</a>', '<svg onload=alert(1)>', '``<iframe src=x>``',
]

describe('SafeMarkdown', () => {
  it.each(XSS)('renders %s without executable markup', (src) => {
    const w = render(src)
    expect(w.findAll('script,img,iframe,svg').length).toBe(0)
    for (const a of w.findAll('a')) {
      expect(a.attributes('href')).toMatch(/^https?:\/\//)
    }
    for (const el of w.element.querySelectorAll('*')) {
      expect([...el.attributes].some((at) => at.name.startsWith('on'))).toBe(false)
    }
    if (src.startsWith('<')) expect(w.text()).toContain('<')
  })

  it('renders https links with safe rel', () => {
    const a = render('[官網](https://example.com/x)').find('a')
    expect(a.attributes('href')).toBe('https://example.com/x')
    expect(a.attributes('rel')).toBe('noopener noreferrer nofollow')
    expect(a.attributes('target')).toBe('_blank')
    expect(a.text()).toBe('官網')
  })

  it('outputs only link text for unsafe or relative links', () => {
    const w = render('[甲](javascript:alert(1)) [乙](/rel/path) [丙](ftp://x.y)')
    expect(w.findAll('a').length).toBe(0)
    expect(w.text()).toContain('甲')
    expect(w.text()).toContain('乙')
  })

  it('never nests anchors', () => {
    const w = render('[a [b](https://in.ner) c](https://out.er)')
    expect(w.findAll('a a').length).toBe(0)
    expect(w.findAll('a').length).toBeLessThanOrEqual(1)
  })

  it('does not render images', () => {
    const w = render('![a](https://x/y.png)')
    expect(w.findAll('img').length).toBe(0)
    expect(w.text()).toContain('a')
  })

  it('renders structure with root class hook', () => {
    const w = render('# T\n\n**b** *i* `c`\n\n- x\n- y\n\n1. p\n\n> q\n\n```\ncode\n```\n\n| h1 | h2 |\n|---|---|\n| 1 | 2 |\n| 3 |')
    expect(w.classes()).toContain('safe-markdown')
    for (const sel of ['h1', 'strong', 'em', 'code', 'ul li', 'ol li', 'blockquote', 'pre code', 'table thead th', 'table tbody tr']) {
      expect(w.find(sel).exists(), sel).toBe(true)
    }
    expect(w.findAll('tbody tr')).toHaveLength(2)
  })

  it('renders single newlines in a paragraph as br', () => {
    const w = render('a\nb')
    expect(w.findAll('br')).toHaveLength(1)
  })

  it('renders empty source', () => {
    expect(render('').find('.safe-markdown').exists()).toBe(true)
    expect(mount(SafeMarkdown).find('.safe-markdown').exists()).toBe(true)
  })
})

describe('SafeMarkdown highlightLines', () => {
  const md = '## 標題\n\n- 沒變\n- 新增的項目\n\n段落'

  it('wraps a highlighted list item in <mark class="pm-diff"> and leaves unchanged ones alone', () => {
    const w = mount(SafeMarkdown, { props: { source: md, highlightLines: new Set([3]) } })
    const items = w.findAll('li')
    expect(items[1]!.find('mark.pm-diff').exists()).toBe(true)
    expect(items[1]!.find('mark.pm-diff').text()).toBe('新增的項目')
    expect(items[0]!.find('mark').exists()).toBe(false)
    expect(w.find('h2 mark').exists()).toBe(false)
    expect(w.findAll('p').at(-1)!.text()).toBe('段落')
    expect(w.findAll('p').at(-1)!.find('mark').exists()).toBe(false)
  })

  it('highlights headings, paragraphs and table rows containing a highlighted line', () => {
    const src = '## 標題\n\n段落一\n段落二\n\n| a | b |\n| - | - |\n| 1 | 2 |\n| 3 | 4 |'
    const w = mount(SafeMarkdown, { props: { source: src, highlightLines: new Set([0, 3, 8]) } })
    expect(w.find('h2 mark.pm-diff').text()).toBe('標題')
    expect(w.find('p mark.pm-diff').exists()).toBe(true)
    const rows = w.findAll('tbody tr')
    expect(rows[0]!.find('mark').exists()).toBe(false)
    expect(rows[1]!.findAll('mark.pm-diff')).toHaveLength(2)
  })

  it('renders no marks without highlightLines', () => {
    expect(mount(SafeMarkdown, { props: { source: md } }).find('mark').exists()).toBe(false)
  })
})
