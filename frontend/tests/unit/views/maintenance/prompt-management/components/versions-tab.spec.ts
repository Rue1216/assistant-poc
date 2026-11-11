import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'

const toast = vi.hoisted(() => vi.fn())
vi.mock('@/composables/use-toast', () => ({ useToast: () => ({ show: toast }) }))

import { DiffTooLargeError, diffLines } from '@/utils/markdown/line-diff'
import type { PromptAdmin } from '@/composables/use-prompt-admin'
import type { ConfigStatus, ConfigVersion, VersionHistoryItem } from '@/beans/prompt-management-bean'
import VersionsTab from '@/views/maintenance/prompt-management/components/versions-tab.vue'

function item(version: number, status: ConfigStatus, updatedAt: string, extra: Partial<VersionHistoryItem> = {}): VersionHistoryItem {
  return { version, status, scheduledAt: null, changeNote: null, basedOnVersion: null, createdBy: 'EEE22222', updatedBy: 'EEE22222', updatedAt, ...extra }
}

function full(h: VersionHistoryItem, content = `內容：${h.status}`): ConfigVersion {
  return {
    configKey: 'system.main',
    configType: 'SYSTEM_PROMPT',
    name: '主要系統提示',
    content,
    exampleQuestions: [],
    status: h.status,
    version: h.version,
    basedOnVersion: h.basedOnVersion,
    changeNote: h.changeNote,
    createdBy: h.createdBy,
    createdAt: h.updatedAt,
    updatedBy: h.updatedBy,
    updatedAt: h.updatedAt,
    scheduledAt: h.scheduledAt,
    docVersion: 3,
  }
}

const ARCHIVED = item(9100, 'ARCHIVED', '2026-10-18T08:00:00+08:00', { updatedBy: 'system' })
const PUBLISHED = item(9101, 'PUBLISHED', '2026-10-19T08:00:00+08:00')
const DRAFT = item(9102, 'DRAFT', '2026-10-19T09:30:00+08:00')
const ALL = [DRAFT, PUBLISHED, ARCHIVED]

const isWorking = (v: VersionHistoryItem) => v.status === 'DRAFT' || v.status === 'SCHEDULED'

function makeAdmin(versions: VersionHistoryItem[]) {
  const wc = versions.find(isWorking)
  const pub = versions.find((v) => v.status === 'PUBLISHED')
  const state = reactive({
    versions,
    feedback: {} as Record<number, { version: number; up: number; down: number }>,
    workingCopy: (wc ? full(wc) : null) as ConfigVersion | null,
    published: (pub ? full(pub) : null) as ConfigVersion | null,
    busy: false,
    conflict: false,
  })
  const loadVersion = vi.fn(async (v: number) => full(versions.find((x) => x.version === v) ?? ARCHIVED))
  const compare = vi.fn(async (a: number, b: number) => {
    const before = full(versions.find((x) => x.version === a)!)
    const after = full(versions.find((x) => x.version === b)!)
    return { nameDiff: diffLines(before.name, after.name), contentDiff: diffLines(before.content, after.content) }
  })
  return { state, loadVersion, compare } as unknown as PromptAdmin & {
    state: typeof state
    loadVersion: typeof loadVersion
    compare: typeof compare
  }
}

type Admin = ReturnType<typeof makeAdmin>
function mountTab(admin: Admin, canEdit = true) {
  return mount(VersionsTab, { props: { admin, configType: 'SYSTEM_PROMPT', canEdit } })
}
type W = ReturnType<typeof mountTab>

const rows = (w: W) => w.findAll('.pm-ver')
const rowOf = (w: W, text: string) => rows(w).find((r) => r.text().includes(text))!
const checkedStates = (w: W) => w.findAll('.pm-ver__check').map((c) => (c.element as HTMLInputElement).checked)

beforeEach(() => toast.mockReset())

describe('VersionsTab', () => {
  it('defaults to the published vs draft diff', async () => {
    const admin = makeAdmin(ALL)
    const w = mountTab(admin)
    await flushPromises()
    expect(admin.compare).toHaveBeenCalledWith(9101, 9102)
    expect(w.find('.pm-diff').exists()).toBe(true)
    expect(checkedStates(w)).toEqual([true, true, false]) // draft, published, archived
    expect(rows(w).map((r) => r.attributes('aria-current'))).toEqual(['true', 'true', undefined])
  })

  it('defaults to the published version without a draft', async () => {
    const admin = makeAdmin([PUBLISHED, ARCHIVED])
    const w = mountTab(admin)
    await flushPromises()
    expect(admin.compare).not.toHaveBeenCalled()
    expect(w.find('.pm-viewer').exists()).toBe(true)
    expect(w.text()).toContain('以此版本建立草稿')
    expect(checkedStates(w)).toEqual([false, false])
  })

  it('shows a hint when never published', async () => {
    const w = mountTab(makeAdmin([DRAFT]))
    await flushPromises()
    expect(w.text()).toContain('尚未發布過，草稿請在內容分頁編輯')
    expect(w.find('.pm-viewer').exists()).toBe(false)
  })

  it('shows 載入中… while the default diff loads', async () => {
    const admin = makeAdmin(ALL)
    let release!: () => void
    admin.compare.mockImplementationOnce(
      () => new Promise((r) => (release = () => r({ nameDiff: [], contentDiff: [] }))),
    )
    const w = mountTab(admin)
    await flushPromises()
    expect(w.text()).toContain('載入中…')
    release()
    await flushPromises()
    expect(w.text()).not.toContain('載入中…')
    expect(w.find('.pm-diff').exists()).toBe(true)
  })

  it('recomputes the default pane when the versions change', async () => {
    const admin = makeAdmin(ALL)
    const w = mountTab(admin)
    await flushPromises()
    expect(w.find('.pm-diff').exists()).toBe(true)

    // the draft was published: it is now the live version and there is no working copy
    const live = item(9102, 'PUBLISHED', DRAFT.updatedAt)
    admin.state.versions = [live, item(9101, 'ARCHIVED', PUBLISHED.updatedAt), ARCHIVED]
    admin.state.workingCopy = null
    admin.state.published = full(live)
    await flushPromises()

    expect(w.find('.pm-diff').exists()).toBe(false)
    expect(w.find('.pm-viewer').exists()).toBe(true)
    expect(w.find('.pm-viewer .md').text()).toBe('內容：PUBLISHED')
    expect(checkedStates(w)).toEqual([false, false, false])
  })

  it('clicking a draft row emits open-content', async () => {
    const w = mountTab(makeAdmin(ALL))
    await flushPromises()
    await rowOf(w, '草稿').find('.pm-ver__main').trigger('click')
    expect(w.emitted('open-content')).toHaveLength(1)
  })

  it('clicking an archived row shows it read-only and 以此版本建立草稿 emits apply', async () => {
    const admin = makeAdmin(ALL)
    const w = mountTab(admin)
    await flushPromises()
    await rowOf(w, '已封存').find('.pm-ver__main').trigger('click')
    await flushPromises()
    expect(admin.loadVersion).toHaveBeenCalledWith(9100)
    expect(w.find('.pm-viewer .md').text()).toBe('內容：ARCHIVED')
    expect(rowOf(w, '已封存').attributes('aria-current')).toBe('true')
    const apply = w.findAll('button').find((b) => b.text() === '以此版本建立草稿')!
    await apply.trigger('click')
    expect(w.emitted('apply')).toEqual([[9100]])
  })

  it('hides 以此版本建立草稿 without edit permission', async () => {
    const w = mountTab(makeAdmin([PUBLISHED]), false)
    await flushPromises()
    expect(w.find('.pm-viewer').exists()).toBe(true)
    expect(w.text()).not.toContain('以此版本建立草稿')
  })

  it('compares the two checked rows, older version first', async () => {
    const admin = makeAdmin([PUBLISHED, ARCHIVED])
    const w = mountTab(admin)
    await flushPromises()
    const checks = w.findAll('.pm-ver__check')
    await checks[1]!.setValue(true)
    await checks[0]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    await flushPromises()
    expect(admin.compare).toHaveBeenCalledWith(9100, 9101)
    expect(w.find('.pm-diff').exists()).toBe(true)
  })

  it('does not compare when the button is disabled', async () => {
    const admin = makeAdmin([PUBLISHED, ARCHIVED])
    const w = mountTab(admin)
    await flushPromises()
    await w.findAll('.pm-ver__check')[0]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    expect(admin.compare).not.toHaveBeenCalled()
  })

  it('falls back to the published version when the default diff is too large', async () => {
    const admin = makeAdmin(ALL)
    admin.compare.mockRejectedValueOnce(new DiffTooLargeError())
    const w = mountTab(admin)
    await flushPromises()
    expect(toast).toHaveBeenCalledWith('內容過長，無法逐行比較')
    expect(w.find('.pm-diff').exists()).toBe(false)
    expect(w.find('.pm-viewer .md').text()).toBe('內容：PUBLISHED')
  })

  it('toasts the generic error for other non-API failures and falls back', async () => {
    const admin = makeAdmin(ALL)
    admin.compare.mockRejectedValueOnce(new Error('boom'))
    const w = mountTab(admin)
    await flushPromises()
    expect(toast).toHaveBeenCalledWith('系統發生錯誤，請稍後再試')
    expect(w.find('.pm-viewer').exists()).toBe(true)
  })

  it('drops a stale version response when another row was clicked since', async () => {
    const older = item(9099, 'ARCHIVED', '2026-10-17T08:00:00+08:00')
    const admin = makeAdmin([PUBLISHED, ARCHIVED, older])
    const w = mountTab(admin)
    await flushPromises()
    let releaseSlow!: () => void
    admin.loadVersion.mockImplementationOnce(() => new Promise((r) => (releaseSlow = () => r(full(ARCHIVED, '慢的版本')))))
    await rows(w)[1]!.find('.pm-ver__main').trigger('click') // slow
    admin.loadVersion.mockImplementationOnce(async () => full(older, '快的版本'))
    await rows(w)[2]!.find('.pm-ver__main').trigger('click')
    await flushPromises()
    releaseSlow()
    await flushPromises()
    expect(w.find('.pm-viewer .md').text()).toBe('快的版本')
  })

  it('emits reload from the conflict banner of the viewer', async () => {
    const admin = makeAdmin([PUBLISHED])
    admin.state.conflict = true
    const w = mountTab(admin)
    await flushPromises()
    await w.find('.pm-banner--conflict button').trigger('click')
    expect(w.emitted('reload')).toHaveLength(1)
  })

  it('shows no version numbers', async () => {
    const w = mountTab(makeAdmin(ALL))
    await flushPromises()
    expect(w.text()).not.toMatch(/91\d\d/)
  })
})
