import { defineComponent, h, type PropType, type VNode } from 'vue'
import { parseMarkdown, parseMarkdownWithLines, type Inline, type MdNode } from './parser'

type Child = VNode | string

function safeHref(href: string): string | null {
  try {
    const u = new URL(href) // no base: relative URLs throw and are dropped
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.href : null
  } catch {
    return null
  }
}

function text(value: string): Child[] {
  const out: Child[] = []
  value.split('\n').forEach((p, i) => {
    if (i > 0) out.push(h('br'))
    if (p) out.push(p)
  })
  return out
}

function inlines(nodes: Inline[], inLink = false): Child[] {
  return nodes.flatMap((n): Child[] => {
    switch (n.type) {
      case 'text':
        return text(n.value)
      case 'strong':
        return [h('strong', inlines(n.children, inLink))]
      case 'em':
        return [h('em', inlines(n.children, inLink))]
      case 'codespan':
        return [h('code', n.value)]
      case 'link': {
        const href = safeHref(n.href)
        if (!href || inLink) return inlines(n.children, inLink) // nested links become plain text
        return [h('a', { href, target: '_blank', rel: 'noopener noreferrer nofollow' }, inlines(n.children, true))]
      }
    }
  })
}

/** Decides which nodes get `<mark class="pm-diff">` around their inline content. */
type Highlight = (node: object) => boolean

const NONE: Highlight = () => false

function marked(children: Child[], on: boolean): Child[] {
  return on ? [h('mark', { class: 'pm-diff' }, children)] : children
}

function blocks(nodes: MdNode[], hl: Highlight): VNode[] {
  return nodes.map((n): VNode => {
    switch (n.type) {
      case 'heading':
        return h(`h${n.level}`, marked(inlines(n.children), hl(n)))
      case 'paragraph':
        return h('p', marked(inlines(n.children), hl(n)))
      case 'list':
        return h(
          n.ordered ? 'ol' : 'ul',
          n.items.map((item) => h('li', blocks(item, hl))),
        )
      case 'blockquote':
        return h('blockquote', blocks(n.children, hl))
      case 'code':
        return h('pre', [h('code', marked([n.text], hl(n)))])
      case 'table':
        return h('table', [
          h('thead', [h('tr', n.header.map((c) => h('th', marked(inlines(c), hl(n.header)))))]),
          h(
            'tbody',
            n.rows.map((r) =>
              h(
                'tr',
                r.map((c) => h('td', marked(inlines(c), hl(r)))),
              ),
            ),
          ),
        ])
    }
  })
}

export default defineComponent({
  name: 'SafeMarkdown',
  props: {
    source: { type: String as PropType<string>, default: '' },
    /**
     * 0-based source line indices to mark: every block, list item or table row whose source lines
     * include one of them gets its inline content wrapped in `<mark class="pm-diff">`.
     */
    highlightLines: { type: Object as PropType<ReadonlySet<number> | null>, default: null },
  },
  setup(props) {
    return () => {
      const hlLines = props.highlightLines
      if (!hlLines || hlLines.size === 0) {
        return h('div', { class: 'safe-markdown' }, blocks(parseMarkdown(props.source), NONE))
      }
      const { nodes, lines } = parseMarkdownWithLines(props.source)
      const hl: Highlight = (node) => {
        const range = lines.get(node)
        if (!range) return false
        for (let l = range.start; l <= range.end; l++) if (hlLines.has(l)) return true
        return false
      }
      return h('div', { class: 'safe-markdown' }, blocks(nodes, hl))
    }
  },
})
