<script setup lang="ts">
import { computed, type FunctionalComponent } from 'vue'
import { parseInline } from '@/utils/markdown/parser'
import { renderInlines } from '@/utils/markdown/safe-markdown'
import type { AnswerBlock, Citation } from '@/beans/ai-assistant-bean'

const props = defineProps<{ blocks: AnswerBlock[]; citations: Citation[] }>()
defineEmits<{ cite: [id: number] }>()

type BlockGroup =
  | { kind: 'list'; tag: 'ul' | 'ol'; blocks: AnswerBlock[] }
  | { kind: 'single'; tag: 'h3' | 'p'; block: AnswerBlock }

/** Consecutive bullets (or numbered items) share one list; headings and paragraphs stand alone. */
const groups = computed<BlockGroup[]>(() => {
  const result: BlockGroup[] = []
  for (const block of props.blocks) {
    if (block.type === 'bullet' || block.type === 'numbered') {
      const tag = block.type === 'bullet' ? 'ul' : 'ol'
      const previous = result[result.length - 1]
      if (previous?.kind === 'list' && previous.tag === tag) previous.blocks.push(block)
      else result.push({ kind: 'list', tag, blocks: [block] })
      continue
    }
    result.push({ kind: 'single', tag: block.type === 'heading' ? 'h3' : 'p', block })
  }
  return result
})

/** Citations of a block that exist in the answer's list; headings never carry any. */
function citationsOf(block: AnswerBlock): Citation[] {
  if (block.type === 'heading') return []
  return block.citations.flatMap((id) => {
    const citation = props.citations.find((candidate) => candidate.id === id)
    return citation ? [citation] : []
  })
}

/** Inline markdown as vnodes built by renderInlines, so model text never reaches v-html. */
const InlineText: FunctionalComponent<{ text: string }> = (inlineProps) => renderInlines(parseInline(inlineProps.text))
InlineText.props = { text: { type: String, required: true } }
</script>

<template>
  <div class="safe-markdown answer-blocks">
    <template v-for="(group, groupIndex) in groups" :key="groupIndex">
      <component :is="group.tag" v-if="group.kind === 'list'">
        <li v-for="(block, blockIndex) in group.blocks" :key="blockIndex">
          <inline-text :text="block.text" />
          <sup v-for="citation in citationsOf(block)" :key="citation.id">
            <button
              type="button"
              class="ai-cite"
              :title="citation.title"
              :aria-label="`引用 ${citation.id}：${citation.title}`"
              @click="$emit('cite', citation.id)"
            >{{ citation.id }}</button>
          </sup>
        </li>
      </component>
      <component :is="group.tag" v-else>
        <inline-text :text="group.block.text" />
        <sup v-for="citation in citationsOf(group.block)" :key="citation.id">
          <button
            type="button"
            class="ai-cite"
            :title="citation.title"
            :aria-label="`引用 ${citation.id}：${citation.title}`"
            @click="$emit('cite', citation.id)"
          >{{ citation.id }}</button>
        </sup>
      </component>
    </template>
  </div>
</template>
