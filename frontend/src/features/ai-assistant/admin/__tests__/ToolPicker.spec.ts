import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import type { ToolCatalogItem } from '../../types'

const tools = vi.fn()
vi.mock('../../api/aiAssistantApi', () => ({ aiAssistantApi: { tools: () => tools() } }))

import ToolPicker from '../ToolPicker.vue'

const CATALOG: ToolCatalogItem[] = [
  {
    name: 'getCustomer',
    purpose: '查詢顧客資料',
    options: [
      { key: 'management', name: '顧客經營', description: '行銷活動與經營紀錄' },
      { key: 'profile', name: '顧客基本資料', description: '姓名與聯絡方式' },
    ],
  },
  { name: 'getRealTimeCustomer', purpose: '即時顧客狀態', options: [] },
]

const mounted: VueWrapper[] = []
beforeEach(() => tools.mockReset().mockResolvedValue(CATALOG))
afterEach(() => mounted.splice(0).forEach((w) => w.unmount()))

function mountPicker() {
  const w = mount(ToolPicker, { attachTo: document.body })
  mounted.push(w)
  return w
}

const button = (w: VueWrapper) => w.find('button[aria-haspopup="listbox"]')
const input = (w: VueWrapper) => w.find('input[role="combobox"]')
const options = (w: VueWrapper) => w.findAll('[role="option"]')
async function openPanel(w: VueWrapper) {
  await button(w).trigger('click')
  await flushPromises()
}

describe('ToolPicker', () => {
  it('expands a tool with options into one item per option, and has no item for the bare tool', async () => {
    const w = mountPicker()
    await openPanel(w)
    const texts = options(w).map((o) => o.text())
    expect(texts).toHaveLength(3)
    expect(texts[0]).toContain('顧客經營')
    expect(texts[1]).toContain('顧客基本資料')
    expect(texts.some((t) => t.trim() === 'getCustomer')).toBe(false)
  })

  it('labels an item with its name and marker text', async () => {
    const w = mountPicker()
    await openPanel(w)
    expect(options(w)[0]!.text()).toContain('顧客經營')
    expect(options(w)[0]!.text()).toContain('getCustomer: management')
    expect(options(w)[2]!.text()).toContain('即時顧客狀態')
    expect(options(w)[2]!.text()).toContain('getRealTimeCustomer')
  })

  it('emits the marker and closes on click', async () => {
    const w = mountPicker()
    await openPanel(w)
    await options(w)[0]!.trigger('click')
    expect(w.emitted('insert')).toEqual([['{{getCustomer: management}}']])
    expect(input(w).exists()).toBe(false)
  })

  it('emits a marker without options for a plain tool', async () => {
    const w = mountPicker()
    await openPanel(w)
    await options(w)[2]!.trigger('click')
    expect(w.emitted('insert')).toEqual([['{{getRealTimeCustomer}}']])
  })

  it('searches name, key, label and option description', async () => {
    const w = mountPicker()
    await openPanel(w)
    await input(w).setValue('活動')
    expect(options(w).map((o) => o.text())).toEqual([expect.stringContaining('顧客經營')])
    await input(w).setValue('realtime')
    expect(options(w).map((o) => o.text())).toEqual([expect.stringContaining('getRealTimeCustomer')])
    await input(w).setValue('PROFILE')
    expect(options(w)).toHaveLength(1)
    await input(w).setValue('即時')
    expect(options(w)).toHaveLength(1)
    await input(w).setValue('zzz')
    expect(options(w)).toHaveLength(0)
  })

  it('supports arrow keys, Enter and Escape', async () => {
    const w = mountPicker()
    await openPanel(w)
    expect(options(w)[0]!.classes()).toContain('is-active')
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    expect(options(w)[1]!.classes()).toContain('is-active')
    await input(w).trigger('keydown', { key: 'ArrowUp' })
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    await input(w).trigger('keydown', { key: 'Enter' })
    expect(w.emitted('insert')).toEqual([['{{getCustomer: profile}}']])
    expect(input(w).exists()).toBe(false)

    await openPanel(w)
    await input(w).trigger('keydown', { key: 'Escape' })
    expect(input(w).exists()).toBe(false)
    expect(w.emitted('insert')).toHaveLength(1)
  })

  it('loads once on first open', async () => {
    const w = mountPicker()
    expect(tools).not.toHaveBeenCalled()
    await openPanel(w)
    await button(w).trigger('click')
    await openPanel(w)
    expect(tools).toHaveBeenCalledTimes(1)
  })

  it('shows an error with retry when loading fails, and retries', async () => {
    tools.mockRejectedValueOnce(new Error('boom'))
    const w = mountPicker()
    await openPanel(w)
    expect(w.text()).toContain('無法載入工具清單')
    const retry = w.findAll('button').find((b) => b.text() === '重試')!
    await retry.trigger('click')
    await flushPromises()
    expect(tools).toHaveBeenCalledTimes(2)
    expect(w.text()).not.toContain('無法載入工具清單')
    expect(options(w)).toHaveLength(3)
  })

  it('gives every option a valid id and points aria-activedescendant at an existing option', async () => {
    const w = mountPicker()
    await openPanel(w)
    for (const o of options(w)) expect(o.attributes('id')).toMatch(/^[A-Za-z0-9_-]+$/)
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    const active = input(w).attributes('aria-activedescendant')!
    expect(active).toMatch(/^[A-Za-z0-9_-]+$/)
    expect(document.getElementById(active)).toBe(options(w)[1]!.element)
    expect(input(w).attributes('aria-controls')).toBeDefined()
  })

  it('moves focus to retry on Tab after a failed load, and retry works', async () => {
    tools.mockRejectedValueOnce(new Error('boom'))
    const w = mountPicker()
    await openPanel(w)
    expect(input(w).attributes('aria-controls')).toBeUndefined()
    await input(w).trigger('keydown', { key: 'Tab' })
    const retry = w.findAll('button').find((b) => b.text() === '重試')!
    expect(retry.exists()).toBe(true)
    expect(document.activeElement).toBe(retry.element)
    await retry.trigger('click')
    await flushPromises()
    expect(options(w)).toHaveLength(3)
  })

  async function openFailed() {
    tools.mockRejectedValueOnce(new Error('boom'))
    const w = mountPicker()
    await openPanel(w)
    const retry = w.findAll('button').find((b) => b.text() === '重試')!
    retry.element.focus()
    return { w, retry }
  }

  it('closes on Escape from retry and focuses the trigger', async () => {
    const { w, retry } = await openFailed()
    await retry.trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(input(w).exists()).toBe(false)
    expect(document.activeElement).toBe(button(w).element)
  })

  it('closes on Tab from retry', async () => {
    const { w, retry } = await openFailed()
    await retry.trigger('keydown', { key: 'Tab' })
    expect(input(w).exists()).toBe(false)
  })

  it('keeps the panel open on Shift+Tab from retry', async () => {
    const { w, retry } = await openFailed()
    await retry.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(input(w).exists()).toBe(true)
  })

  it('focuses the search input after a successful retry', async () => {
    const { w, retry } = await openFailed()
    await retry.trigger('click')
    await flushPromises()
    expect(document.activeElement).toBe(input(w).element)
  })

  it('refocuses the search input when a retry fails again, and Escape closes', async () => {
    tools.mockRejectedValueOnce(new Error('boom'))
    const w = mountPicker()
    await openPanel(w)
    tools.mockRejectedValueOnce(new Error('boom again'))
    const retry = w.findAll('button').find((b) => b.text() === '重試')!
    retry.element.focus()
    await retry.trigger('click')
    await flushPromises()
    expect(w.text()).toContain('無法載入工具清單')
    expect(document.activeElement).toBe(input(w).element)
    await input(w).trigger('keydown', { key: 'Escape' })
    expect(input(w).exists()).toBe(false)
  })

  it('has no aria-controls on the trigger or the input while the load has failed', async () => {
    const { w } = await openFailed()
    expect(button(w).attributes('aria-controls')).toBeUndefined()
    expect(input(w).attributes('aria-controls')).toBeUndefined()
  })

  it('still closes on Escape after a failed load', async () => {
    tools.mockRejectedValueOnce(new Error('boom'))
    const w = mountPicker()
    await openPanel(w)
    await input(w).trigger('keydown', { key: 'Escape' })
    expect(input(w).exists()).toBe(false)
  })

  it('shows no hints', async () => {
    const w = mountPicker()
    await openPanel(w)
    for (const hint of ['必查', '只適合', '已在內容中']) expect(w.text()).not.toContain(hint)
  })
})
