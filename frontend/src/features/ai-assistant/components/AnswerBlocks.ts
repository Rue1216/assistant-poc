import { defineComponent, h, type PropType, type VNode } from 'vue'
import { parseInline } from '../markdown/parser'
import { renderInlines } from '../markdown/SafeMarkdown'
import type { AnswerBlock, Citation } from '../types'

export default defineComponent({
  name: 'AnswerBlocks',
  props: {
    blocks: { type: Array as PropType<AnswerBlock[]>, required: true },
    citations: { type: Array as PropType<Citation[]>, required: true },
  },
  emits: { cite: (id: number) => typeof id === 'number' },
  setup(props, { emit }) {
    function citeButtons(block: AnswerBlock): VNode[] {
      const out: VNode[] = []
      for (const id of block.citations) {
        const c = props.citations.find((x) => x.id === id)
        if (!c) continue
        out.push(
          h('sup', [
            h(
              'button',
              {
                type: 'button',
                class: 'ai-cite',
                title: c.title,
                'aria-label': `引用 ${c.id}：${c.title}`,
                onClick: () => emit('cite', c.id),
              },
              String(c.id),
            ),
          ]),
        )
      }
      return out
    }

    function content(block: AnswerBlock): (VNode | string)[] {
      return [...renderInlines(parseInline(block.text)), ...(block.type === 'heading' ? [] : citeButtons(block))]
    }

    return () => {
      const out: VNode[] = []
      let list: { tag: 'ul' | 'ol'; items: VNode[] } | null = null
      const flush = () => {
        if (list) out.push(h(list.tag, list.items))
        list = null
      }
      for (const b of props.blocks) {
        if (b.type === 'bullet' || b.type === 'numbered') {
          const tag = b.type === 'bullet' ? 'ul' : 'ol'
          if (list && list.tag !== tag) flush()
          if (!list) list = { tag, items: [] }
          list.items.push(h('li', content(b)))
          continue
        }
        flush()
        out.push(h(b.type === 'heading' ? 'h3' : 'p', content(b)))
      }
      flush()
      return h('div', { class: 'safe-markdown answer-blocks' }, out)
    }
  },
})
