import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import type { ConfigSummary } from '@/beans/prompt-management-bean'
import ConfigPicker from '@/views/maintenance/prompt-management/components/config-picker.vue'

const T = { v1: '2026-10-19T08:00:00+08:00', sched: '2026-10-24T09:00:00+08:00' }

const CONFIGS: ConfigSummary[] = [
  { configKey: 'system.main', configType: 'SYSTEM_PROMPT', name: '主要系統提示', published: { version: 1, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'x', updatedAt: T.v1 }, workingCopy: { version: 2, status: 'DRAFT', scheduledAt: null, updatedBy: 'x', updatedAt: T.v1 } },
  { configKey: 'instruction.contact-prep', configType: 'SYSTEM_INSTRUCTION', name: '聯繫準備', published: null, workingCopy: { version: 3, status: 'SCHEDULED', scheduledAt: T.sched, updatedBy: 'x', updatedAt: T.v1 } },
  { configKey: 'instruction.customer-overview', configType: 'SYSTEM_INSTRUCTION', name: '顧客概況', published: { version: 4, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'x', updatedAt: T.v1 }, workingCopy: null },
]

const mounted: VueWrapper[] = []
afterEach(() => mounted.splice(0).forEach((w) => w.unmount()))

function mountPicker(props: Partial<{ configs: ConfigSummary[]; selectedKey: string; loading: boolean }> = {}) {
  const w = mount(ConfigPicker, {
    props: { configs: CONFIGS, selectedKey: 'system.main', loading: false, ...props },
    attachTo: document.body,
  })
  mounted.push(w)
  return w
}

const button = (w: VueWrapper) => w.find('button[aria-haspopup="listbox"]')
const input = (w: VueWrapper) => w.find('input[role="combobox"]')
const options = (w: VueWrapper) => w.findAll('[role="option"]')
async function openPanel(w: VueWrapper) {
  await button(w).trigger('click')
}

describe('ConfigPicker', () => {
  it('shows the current name and opens on click', async () => {
    const w = mountPicker()
    expect(button(w).text()).toContain('主要系統提示')
    expect(button(w).attributes('aria-expanded')).toBe('false')
    expect(input(w).exists()).toBe(false)

    await openPanel(w)
    expect(button(w).attributes('aria-expanded')).toBe('true')
    expect(input(w).attributes('placeholder')).toBe('搜尋名稱或 key')
    expect(document.activeElement).toBe(input(w).element)
  })

  it('shows 載入中… while loading and 請選擇設定 without a selection', () => {
    expect(button(mountPicker({ loading: true, configs: [], selectedKey: '' })).text()).toContain('載入中…')
    expect(button(mountPicker({ selectedKey: '' })).text()).toContain('請選擇設定')
  })

  it('says 尚無設定 when the list is empty', async () => {
    const w = mountPicker({ configs: [], selectedKey: '' })
    await openPanel(w)
    expect(w.find('[role="listbox"]').text()).toContain('尚無設定')
  })

  it('groups configs with status badges', async () => {
    const w = mountPicker()
    await openPanel(w)
    const groups = w.findAll('[role="group"]')
    expect(groups).toHaveLength(2)
    const label = (g: (typeof groups)[number]) => document.getElementById(g.attributes('aria-labelledby')!)!.textContent
    expect(groups.map(label)).toEqual(['System Prompt', '問題分類 2'])

    expect(options(w).find((o) => o.text().includes('聯繫準備'))!.text()).toContain('已排程 10/24 09:00')
    expect(options(w).find((o) => o.text().includes('主要系統提示'))!.text()).toContain('草稿')
    expect(options(w).find((o) => o.text().includes('顧客概況'))!.text()).not.toMatch(/草稿|已排程/)
    expect(options(w).map((o) => o.attributes('aria-selected'))).toEqual(['true', 'false', 'false'])
  })

  it('filters by name or key and hides empty groups', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).setValue('contact')
    expect(options(w).map((o) => o.text())).toEqual([expect.stringContaining('聯繫準備')])
    expect(w.findAll('[role="group"]')).toHaveLength(1)
    expect(w.text()).toContain('問題分類 2') // the count ignores the search
    expect(w.text()).not.toContain('System Prompt')

    await input(w).setValue('  顧客 ')
    expect(options(w)).toHaveLength(1)
    await input(w).setValue('MAIN')
    expect(options(w).map((o) => o.text())).toEqual([expect.stringContaining('主要系統提示')])

    await input(w).setValue('zzz')
    expect(options(w)).toHaveLength(0)
    expect(w.find('[role="listbox"]').text()).toContain('沒有符合的設定')
  })

  it('keyboard: arrow keys and Enter select', async () => {
    const w = mountPicker()
    await button(w).trigger('keydown', { key: 'ArrowDown' })
    expect(input(w).exists()).toBe(true)

    const ids = options(w).map((o) => o.attributes('id'))
    expect(input(w).attributes('aria-activedescendant')).toBe(ids[0]) // the current config
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    expect(input(w).attributes('aria-activedescendant')).toBe(ids[1])
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    await input(w).trigger('keydown', { key: 'ArrowDown' }) // stops at the end
    expect(input(w).attributes('aria-activedescendant')).toBe(ids[2])
    await input(w).trigger('keydown', { key: 'ArrowUp' })
    expect(input(w).attributes('aria-activedescendant')).toBe(ids[1])

    await input(w).trigger('keydown', { key: 'Enter' })
    expect(w.emitted('select')).toEqual([['instruction.contact-prep']])
    expect(input(w).exists()).toBe(false)
  })

  it('starts on the first visible option when the current config is filtered out', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).setValue('顧客')
    expect(input(w).attributes('aria-activedescendant')).toBe(options(w)[0]!.attributes('id'))
    await input(w).trigger('keydown', { key: 'Enter' })
    expect(w.emitted('select')).toEqual([['instruction.customer-overview']])
  })

  it('exposes open()', async () => {
    const w = mountPicker()
    ;(w.vm as unknown as { open: () => void }).open()
    await w.vm.$nextTick()
    expect(input(w).exists()).toBe(true)
    expect(document.activeElement).toBe(input(w).element)
  })

  it('re-validates the active option when the configs change while open', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    await input(w).trigger('keydown', { key: 'ArrowDown' }) // on 顧客概況
    const gone = options(w)[2]!.attributes('id')
    expect(input(w).attributes('aria-activedescendant')).toBe(gone)

    await w.setProps({ configs: CONFIGS.slice(0, 2) })
    const active = input(w).attributes('aria-activedescendant')!
    expect(active).not.toBe(gone)
    expect(options(w).map((o) => o.attributes('id'))).toContain(active)
    expect(active).toBe(options(w)[0]!.attributes('id')) // falls back to the current config
  })

  it('Escape closes and returns focus to the button', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).trigger('keydown', { key: 'Escape' })
    expect(input(w).exists()).toBe(false)
    expect(document.activeElement).toBe(button(w).element)
  })

  it('Tab closes', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).trigger('keydown', { key: 'Tab' })
    expect(input(w).exists()).toBe(false)
  })

  it('clicking an option emits select and closes', async () => {
    const w = mountPicker()
    await openPanel(w)
    await options(w)[2]!.trigger('click')
    expect(w.emitted('select')).toEqual([['instruction.customer-overview']])
    expect(input(w).exists()).toBe(false)
  })

  it('pointerdown outside closes; inside does not', async () => {
    const w = mountPicker()
    await openPanel(w)
    input(w).element.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await w.vm.$nextTick()
    expect(input(w).exists()).toBe(true)

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await w.vm.$nextTick()
    expect(input(w).exists()).toBe(false)
    expect(w.emitted('select')).toBeUndefined()
  })

  it('adds one document pointerdown listener while open and removes it on close and unmount', async () => {
    const add = vi.spyOn(document, 'addEventListener')
    const remove = vi.spyOn(document, 'removeEventListener')
    const pd = (spy: typeof add) => spy.mock.calls.filter((c) => c[0] === 'pointerdown')
    const w = mountPicker()
    expect(pd(add)).toHaveLength(0)

    await openPanel(w)
    ;(w.vm as unknown as { open: () => void }).open() // opening again adds nothing
    expect(pd(add)).toHaveLength(1)
    const handler = pd(add)[0]![1]

    await input(w).trigger('keydown', { key: 'Escape' })
    expect(pd(remove).map((c) => c[1])).toEqual([handler])

    await openPanel(w)
    expect(pd(add)).toHaveLength(2)
    w.unmount()
    mounted.splice(mounted.indexOf(w), 1)
    expect(pd(remove)).toHaveLength(2)
    expect(pd(remove)[1]![1]).toBe(pd(add)[1]![1])
    add.mockRestore()
    remove.mockRestore()
  })

  it('selecting the current config only closes', async () => {
    const w = mountPicker()
    await openPanel(w)
    await options(w)[0]!.trigger('click')
    expect(w.emitted('select')).toBeUndefined()
    expect(input(w).exists()).toBe(false)

    await openPanel(w)
    await input(w).trigger('keydown', { key: 'Enter' }) // active starts on the current config
    expect(w.emitted('select')).toBeUndefined()
    expect(input(w).exists()).toBe(false)
  })
})
