import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionList from '../VersionList.vue'
import type { ConfigStatus, ConfigType, VersionFeedback, VersionHistoryItem } from '../../types'

function item(version: number, status: ConfigStatus, updatedAt: string, extra: Partial<VersionHistoryItem> = {}) {
  return {
    version,
    status,
    scheduledAt: null,
    changeNote: null,
    basedOnVersion: null,
    createdBy: 'EEE22222',
    updatedBy: 'EEE22222',
    updatedAt,
    ...extra,
  } satisfies VersionHistoryItem
}

const VERSIONS: VersionHistoryItem[] = [
  item(9135, 'ARCHIVED', '2026-10-19T08:00:00+08:00', { updatedBy: 'system', changeNote: '初始版本' }),
  item(9136, 'PUBLISHED', '2026-10-19T09:30:00+08:00', { updatedBy: 'EEE11111', changeNote: '新增段落' }),
  item(9137, 'DISCARDED', '2026-10-19T10:40:00+08:00', {
    createdBy: 'EEE11111',
    scheduledAt: '2026-10-22T09:00:00+08:00',
    changeNote: '只列最近 3 筆',
  }),
  item(9138, 'DRAFT', '2026-10-19T11:00:00+08:00'),
]

function mountList(
  extra: {
    versions?: VersionHistoryItem[]
    selected?: number[]
    checked?: number[]
    feedback?: Record<number, VersionFeedback>
    configType?: ConfigType
  } = {},
  attachTo?: HTMLElement,
) {
  return mount(VersionList, {
    props: { versions: VERSIONS, selected: [], checked: [], feedback: {}, configType: 'SYSTEM_PROMPT', ...extra },
    attachTo,
  })
}

type W = ReturnType<typeof mountList>
const rows = (w: W) => w.findAll('.pm-ver')
const compareButton = (w: W) => w.find('.pm-versions__compare')

describe('VersionList', () => {
  it('shows two lines per row: status and time, then operator and note', () => {
    const w = mountList()
    expect(rows(w).map((r) => r.find('.pm-ver__status').text())).toEqual(['草稿', '目前生效', '已封存'])
    expect(rows(w)[1]!.find('.pm-ver__time').text()).toBe('10/19 09:30')
    expect(rows(w)[1]!.find('.pm-ver__by').text()).toBe('EEE11111・新增段落')
    expect(rows(w)[0]!.find('.pm-ver__by').text()).toBe('EEE22222')
  })

  it('has no apply buttons, no hint text, no details wrapper and no inline provenance', async () => {
    const w = mountList({ versions: [...VERSIONS, item(9139, 'ARCHIVED', '2026-10-18T08:00:00+08:00', { basedOnVersion: 9135 })] })
    await w.find('.pm-versions__toggle input').setValue(true)
    expect(w.findAll('button').some((b) => b.text() === '套用')).toBe(false)
    expect(w.find('.pm-ver__apply').exists()).toBe(false)
    expect(w.find('details').exists()).toBe(false)
    expect(w.find('.pm-versions__hint').exists()).toBe(false)
    expect(w.text()).not.toContain('原作者')
    expect(w.text()).not.toContain('原預約')
    expect(w.text()).not.toContain('為底')
  })

  it('never puts a version number in any text or attribute', async () => {
    const w = mountList({ checked: [9135] })
    await w.find('.pm-versions__toggle input').setValue(true)
    expect(w.text()).not.toMatch(/913[5-9]/)
    for (const el of w.findAll('*')) {
      for (const attr of Array.from(el.element.attributes)) expect(attr.value, attr.name).not.toMatch(/913[5-9]/)
    }
  })

  it('marks the selected rows with aria-current', () => {
    const w = mountList({ selected: [9136] })
    expect(rows(w).map((r) => r.attributes('aria-current'))).toEqual([undefined, 'true', undefined])
  })

  it('reflects the checked prop and emits update:checked when a box is toggled', async () => {
    const w = mountList({ checked: [9135] })
    const checks = w.findAll('.pm-ver__check')
    expect(checks.map((c) => (c.element as HTMLInputElement).checked)).toEqual([false, false, true])
    await checks[1]!.setValue(true)
    expect(w.emitted('update:checked')).toEqual([[[9135, 9136]]])
    await checks[2]!.setValue(false)
    expect(w.emitted('update:checked')![1]).toEqual([[]])
  })

  describe('compare button', () => {
    it('is always present and aria-disabled unless exactly two are checked', () => {
      for (const checked of [[], [9135], [9135, 9136, 9138]]) {
        const w = mountList({ checked })
        expect(compareButton(w).text()).toBe('比較勾選的兩版')
        expect(compareButton(w).attributes('aria-disabled')).toBe('true')
        expect(compareButton(w).attributes('disabled')).toBeUndefined()
      }
      const ready = mountList({ checked: [9135, 9136] })
      expect(ready.find('.pm-versions__compare').attributes('aria-disabled')).toBe('false')
    })

    it('does nothing when clicked or activated while disabled', async () => {
      const w = mountList({ checked: [9135] })
      await compareButton(w).trigger('click')
      await compareButton(w).trigger('keydown', { key: 'Enter' })
      expect(w.emitted('compare')).toBeUndefined()
    })

    it('emits compare when two rows are checked', async () => {
      const w = mountList({ checked: [9135, 9136] })
      await compareButton(w).trigger('click')
      expect(w.emitted('compare')).toEqual([[]])
    })

    it('shows the tooltip on hover or focus, only while disabled, linked by aria-describedby', async () => {
      const w = mountList({ checked: [9135] }, document.body)
      const tip = w.find('[role="tooltip"]')
      expect(tip.text()).toBe('勾選任兩個版本後比較')
      expect(compareButton(w).attributes('aria-describedby')).toBe(tip.attributes('id'))
      expect(tip.isVisible()).toBe(false)
      await compareButton(w).trigger('mouseenter')
      expect(tip.isVisible()).toBe(true)
      await compareButton(w).trigger('mouseleave')
      expect(tip.isVisible()).toBe(false)
      await compareButton(w).trigger('focus')
      expect(tip.isVisible()).toBe(true)
      await compareButton(w).trigger('blur')
      expect(tip.isVisible()).toBe(false)

      await w.setProps({ checked: [9135, 9136] })
      await compareButton(w).trigger('mouseenter')
      expect(w.find('[role="tooltip"]').isVisible()).toBe(false)
      expect(compareButton(w).attributes('aria-describedby')).toBeUndefined()
      w.unmount()
    })

    it('hides the tooltip on Escape while the compare button has focus', async () => {
      const w = mountList({ checked: [9135] }, document.body)
      const tip = w.find('[role="tooltip"]')
      await compareButton(w).trigger('focus')
      expect(tip.isVisible()).toBe(true)
      await compareButton(w).trigger('keydown', { key: 'Escape' })
      expect(tip.isVisible()).toBe(false)
      w.unmount()
    })
  })

  it('hides discarded rows until "顯示已捨棄" is toggled', async () => {
    const w = mountList()
    expect(w.text()).not.toContain('只列最近 3 筆')
    const toggle = w.find('.pm-versions__toggle')
    expect(toggle.text()).toBe('顯示已捨棄（1）')
    await toggle.find('input').setValue(true)
    const discarded = rows(w).find((r) => r.classes('is-discarded'))!
    expect(discarded.find('.pm-ver__status').text()).toBe('已捨棄')
    expect(discarded.text()).toContain('只列最近 3 筆')
  })

  it('drops checked rows that get hidden with the discarded rows', async () => {
    const w = mountList({ checked: [9135, 9137] })
    await w.find('.pm-versions__toggle input').setValue(true)
    await w.find('.pm-versions__toggle input').setValue(false)
    expect(w.emitted('update:checked')?.at(-1)).toEqual([[9135]])
  })

  it('shows seconds only on rows that share a minute', () => {
    const versions = [
      item(9140, 'DRAFT', '2026-10-19T12:00:41+08:00'),
      item(9139, 'PUBLISHED', '2026-10-19T12:00:07+08:00'),
      item(9138, 'ARCHIVED', '2026-10-19T11:00:00+08:00'),
    ]
    const w = mountList({ versions })
    expect(rows(w).map((r) => r.find('.pm-ver__time').text())).toEqual(['10/19 12:00:41', '10/19 12:00:07', '10/19 11:00'])
    expect(w.findAll('.pm-ver__check')[0]!.attributes('aria-label')).toContain('10/19 12:00:41')
    expect(w.findAll('.pm-ver__check')[2]!.attributes('aria-label')).not.toContain('11:00:')
  })

  it('opens a version when its row is clicked', async () => {
    const w = mountList()
    await rows(w)[1]!.find('.pm-ver__main').trigger('click')
    expect(w.emitted('view')).toEqual([[9136]])
  })

  describe('feedback statistics', () => {
    const fb = (version: number, up: number, down: number) => ({ version, up, down })

    it('shows counts and percentage on published and archived rows only', () => {
      const w = mountList({
        feedback: { 9135: fb(9135, 1, 1), 9136: fb(9136, 12, 3), 9137: fb(9137, 5, 5), 9138: fb(9138, 4, 0) },
      })
      const text = rows(w).map((r) => r.text())
      const published = text.find((t) => t.includes('目前生效'))!
      expect(published).toContain('12')
      expect(published).toContain('（80%）')
      expect(text.find((t) => t.includes('已封存'))).toContain('（50%）')
      expect(w.text()).not.toContain('（100%）')
      expect(w.text()).not.toContain('（0%）')
      const labels = w.findAll('.pm-ver__feedback').map((f) => f.attributes('aria-label'))
      expect(labels).toEqual(['讚 12、倒讚 3（80%）', '讚 1、倒讚 1（50%）'])
      expect(w.findAll('.pm-ver__feedback svg')).toHaveLength(4)
      expect(w.text()).toContain('回饋統計涵蓋 5 年內的回答')
    })

    it('shows nothing without statistics, footnote included', () => {
      const w = mountList()
      expect(w.find('.pm-ver__feedback').exists()).toBe(false)
      expect(w.text()).not.toContain('回饋統計涵蓋')
    })

    it('hides the footnote when the only statistics belong to a draft', () => {
      const w = mountList({ versions: [item(1, 'DRAFT', '2026-10-19T08:00:00+08:00')], feedback: { 1: fb(1, 2, 2) } })
      expect(w.text()).not.toContain('回饋統計涵蓋')
    })
  })

  it('words the footnote for instruction categories as confirmed-use answers only', () => {
    const w = mountList({ feedback: { 9136: { version: 9136, up: 3, down: 1 } }, configType: 'SYSTEM_INSTRUCTION' })
    expect(w.find('.pm-versions__note').text()).toBe('回饋統計涵蓋 5 年內、且確定使用此分類的回答')
  })

  it('keeps the plain footnote for the system prompt', () => {
    const w = mountList({ feedback: { 9136: { version: 9136, up: 3, down: 1 } } })
    expect(w.find('.pm-versions__note').text()).toBe('回饋統計涵蓋 5 年內的回答')
  })
})
