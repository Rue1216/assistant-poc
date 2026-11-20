import { inject, provide, reactive, type InjectionKey } from 'vue'
import { promptManagementApi } from '@/views/maintenance/prompt-management/prompt-management-api'
import type { ToolCatalogItem } from '@/beans/prompt-management-bean'

/**
 * The tool catalog the instruction editor's tool picker inserts from.
 *
 * Owned by the page, not by the picker: the picker is remounted on every edit/preview switch,
 * config switch and new working-copy version, so a catalog kept inside it was fetched again each time.
 * Loading stays lazy (nothing is fetched until the first `load()`), a loaded catalog is kept for the
 * page's lifetime, and a failed load is not kept, so calling `load()` again is the retry.
 */
export function useToolCatalog() {
  const state = reactive({
    items: null as ToolCatalogItem[] | null,
    loading: false,
    failed: false,
  })

  let pending: Promise<void> | null = null

  /** Fetches the catalog unless it is already loaded; calls made while a request is in flight share it. */
  function load(): Promise<void> {
    if (state.items) return Promise.resolve()
    pending ??= (async () => {
      state.loading = true
      state.failed = false
      try {
        state.items = await promptManagementApi.tools()
      } catch {
        state.failed = true
      } finally {
        state.loading = false
        pending = null
      }
    })()
    return pending
  }

  return { state, load }
}

export type ToolCatalog = ReturnType<typeof useToolCatalog>

export const TOOL_CATALOG_KEY: InjectionKey<ToolCatalog> = Symbol('tool-catalog')

/** Creates the page's tool catalog and provides it to every tool picker below the calling component. */
export function provideToolCatalog(): ToolCatalog {
  const catalog = useToolCatalog()
  provide(TOOL_CATALOG_KEY, catalog)
  return catalog
}

/**
 * The tool catalog provided by an ancestor (the page, via provideToolCatalog).
 * Throws when there is none: a picker with a catalog of its own would fetch again on every remount.
 */
export function injectToolCatalog(): ToolCatalog {
  const catalog = inject(TOOL_CATALOG_KEY, null)
  if (!catalog) throw new Error('injectToolCatalog: no tool catalog provided; call provideToolCatalog() in the page')
  return catalog
}
