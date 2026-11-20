import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import type { ToolCatalogItem } from '@/beans/prompt-management-bean'

const tools = vi.fn()
vi.mock('@/views/maintenance/prompt-management/prompt-management-api', () => ({ promptManagementApi: { tools: () => tools() } }))

import { injectToolCatalog, provideToolCatalog, useToolCatalog, type ToolCatalog } from '@/composables/use-tool-catalog'

const CATALOG: ToolCatalogItem[] = [{ name: 'getRealTimeCustomer', purpose: '即時顧客狀態', options: [] }]

beforeEach(() => tools.mockReset().mockResolvedValue(CATALOG))

describe('useToolCatalog', () => {
  it('fetches nothing until load is called', () => {
    const catalog = useToolCatalog()
    expect(tools).not.toHaveBeenCalled()
    expect(catalog.state).toEqual({ items: null, loading: false, failed: false })
  })

  it('keeps the loaded catalog and does not fetch it again', async () => {
    const catalog = useToolCatalog()
    await catalog.load()
    await catalog.load()
    expect(tools).toHaveBeenCalledTimes(1)
    expect(catalog.state.items).toEqual(CATALOG)
  })

  it('shares one request between calls made while it is in flight', async () => {
    const catalog = useToolCatalog()
    const a = catalog.load()
    expect(catalog.state.loading).toBe(true)
    const b = catalog.load()
    await Promise.all([a, b])
    expect(tools).toHaveBeenCalledTimes(1)
    expect(catalog.state.loading).toBe(false)
  })

  it('marks a failed load and fetches again on the next load', async () => {
    tools.mockRejectedValueOnce(new Error('boom'))
    const catalog = useToolCatalog()
    await catalog.load()
    expect(catalog.state).toEqual({ items: null, loading: false, failed: true })
    await catalog.load()
    expect(tools).toHaveBeenCalledTimes(2)
    expect(catalog.state).toEqual({ items: CATALOG, loading: false, failed: false })
  })

  it('gives every descendant the catalog the ancestor provided', () => {
    let provided: ToolCatalog | null = null
    const injected: ToolCatalog[] = []
    const Child = defineComponent({
      setup() {
        injected.push(injectToolCatalog())
        return () => null
      },
    })
    const Parent = defineComponent({
      setup() {
        provided = provideToolCatalog()
        return () => [h(Child), h(Child)]
      },
    })
    mount(Parent).unmount()
    expect(injected).toHaveLength(2)
    expect(injected.every((c) => c === provided)).toBe(true)
  })
})
