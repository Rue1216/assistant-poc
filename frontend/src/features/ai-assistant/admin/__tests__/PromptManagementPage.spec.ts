import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import type { ConfigStatus, ConfigSummary, ConfigVersion, VersionHistoryItem } from '../../types'

const api = vi.hoisted(() => ({
  listConfigs: vi.fn(),
  createConfig: vi.fn(),
  listVersions: vi.fn(),
  getVersion: vi.fn(),
  createDraft: vi.fn(),
  saveDraft: vi.fn(),
  discardDraft: vi.fn(),
  validateDraft: vi.fn(),
  publish: vi.fn(),
  applyVersion: vi.fn(),
  listEvaluations: vi.fn(),
  currentEvaluation: vi.fn(),
  getEvaluation: vi.fn(),
  startEvaluation: vi.fn(),
  evaluationCases: vi.fn(),
}))
vi.mock('../../api/aiAssistantApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/aiAssistantApi')>()),
  aiAssistantApi: api,
}))
const toast = vi.hoisted(() => vi.fn())
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: toast }) }))

import { ApiError, apiClient } from '../../../../platform-mock/apiClient'
import { createAppRouter } from '../../../../platform-mock/router'
import { useAuthStore } from '../../../../platform-mock/useAuthStore'
import PromptManagementPage from '../PromptManagementPage.vue'

const T = {
  v1: '2026-10-19T08:00:00+08:00',
  v2: '2026-10-19T09:30:00+08:00',
  v3: '2026-10-19T11:00:00+08:00',
  sched: '2026-10-24T09:00:00+08:00',
}

/** Internal version numbers are distinctive (91xx) so a leak onto the screen is unmistakable. */
const VERSION_NUMBER = /91\d\d/

const NAMES: Record<string, string> = {
  'system.main': '主要系統提示',
  'instruction.contact-prep': '聯繫準備',
  'instruction.customer-overview': '顧客概況',
}

function item(version: number, status: ConfigStatus, updatedAt: string, extra: Partial<VersionHistoryItem> = {}): VersionHistoryItem {
  return { version, status, scheduledAt: null, changeNote: null, basedOnVersion: null, createdBy: 'EEE22222', updatedBy: 'EEE22222', updatedAt, ...extra }
}

/** Content names the status (no digits), so it can never be mistaken for a version number. */
function full(configKey: string, h: VersionHistoryItem, content = `內容：${h.status}`): ConfigVersion {
  return {
    configKey,
    configType: configKey.startsWith('system.') ? 'SYSTEM_PROMPT' : 'SYSTEM_INSTRUCTION',
    name: NAMES[configKey] ?? '新分類',
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
    docVersion: 5,
  }
}

const BASE_HISTORY: Record<string, VersionHistoryItem[]> = {
  'system.main': [item(9102, 'DRAFT', T.v2), item(9101, 'PUBLISHED', T.v1)],
  'instruction.contact-prep': [
    item(9113, 'SCHEDULED', T.v3, { scheduledAt: T.sched }),
    item(9112, 'PUBLISHED', T.v2, { updatedBy: 'EEE11111' }),
    item(9111, 'ARCHIVED', T.v1, { updatedBy: 'system' }),
  ],
  'instruction.customer-overview': [item(9121, 'PUBLISHED', T.v1, { updatedBy: 'system' })],
}

let HISTORY: Record<string, VersionHistoryItem[]> = {}

const CONFIGS: ConfigSummary[] = [
  { configKey: 'system.main', configType: 'SYSTEM_PROMPT', name: '主要系統提示', published: { version: 9101, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'EEE22222', updatedAt: T.v1 }, workingCopy: { version: 9102, status: 'DRAFT', scheduledAt: null, updatedBy: 'EEE22222', updatedAt: T.v2 } },
  { configKey: 'instruction.contact-prep', configType: 'SYSTEM_INSTRUCTION', name: '聯繫準備', published: { version: 9112, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'EEE11111', updatedAt: T.v2 }, workingCopy: { version: 9113, status: 'SCHEDULED', scheduledAt: T.sched, updatedBy: 'EEE22222', updatedAt: T.v3 } },
  { configKey: 'instruction.customer-overview', configType: 'SYSTEM_INSTRUCTION', name: '顧客概況', published: { version: 9121, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'system', updatedAt: T.v1 }, workingCopy: null },
]

async function mountPage(attachTo?: HTMLElement) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = {
    username: 'EEE22222',
    displayName: 'IT',
    roleKey: 'ADMIN',
    roleName: 'IT 管理',
    permissions: ['AI_CONFIG_READ', 'AI_CONFIG_EDIT', 'AI_CONFIG_PUBLISH'],
  }
  router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/prompt', component: PromptManagementPage },
      { path: '/other', component: { render: () => h('p', '其他頁面') } },
    ],
  })
  await router.push('/prompt')
  const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [pinia, router] }, attachTo })
  mounted.push(wrapper)
  await flushPromises()
  return wrapper
}

/** Unmounted after each test so a page left dirty cannot keep its beforeunload listener. */
const mounted: VueWrapper[] = []
afterEach(() => {
  mounted.splice(0).forEach((w) => w.unmount())
  // Re-spying an already spied method reuses the spy (and its calls) on Vitest 4
  vi.restoreAllMocks()
})

let router: Router

const byText = (w: VueWrapper, selector: string, text: string) => w.findAll(selector).find((e) => e.text().includes(text))!
const textarea = (w: VueWrapper) => w.find('textarea').element as HTMLTextAreaElement

/** The config picker button in the page header (it shows the current config's name). */
const pickerButton = (w: VueWrapper) => w.find('.pm-confighead button[aria-haspopup="listbox"]')
const input = (w: VueWrapper) => w.find('input[role="combobox"]')
const pickerOptions = (w: VueWrapper) => w.findAll('[role="option"]')
async function openPicker(w: VueWrapper) {
  await pickerButton(w).trigger('click')
  await flushPromises()
}
/** Opens the picker and picks a config by name, like a user would. */
async function pickConfig(w: VueWrapper, name: string) {
  await openPicker(w)
  await byText(w, '[role="option"]', name).trigger('click')
  await flushPromises()
}
const openInstructions = pickConfig

/** The 內容／版本／評測 tabs under the header. */
const pageTab = (w: VueWrapper, label: string) => w.findAll('.pm-pagetabs [role="tab"]').find((t) => t.text().startsWith(label))!
async function openTab(w: VueWrapper, label: string) {
  await pageTab(w, label).trigger('click')
  await flushPromises()
}

/** In the 版本 tab: opens the row with that text, then presses 以此版本建立草稿 and waits. */
async function applyFromRow(w: VueWrapper, rowText: string) {
  await byText(w, '.pm-ver', rowText).find('.pm-ver__main').trigger('click')
  await flushPromises()
  await byText(w, '.pm-actionbar button', '以此版本建立草稿').trigger('click')
  await flushPromises()
}

/** No internal version number in the text or in any attribute (aria-label, title, value, ...). */
function expectNoVersionNumbers(w: VueWrapper) {
  expect(w.text()).not.toMatch(VERSION_NUMBER)
  for (const el of w.findAll('*')) {
    for (const attr of Array.from(el.element.attributes)) expect(attr.value, attr.name).not.toMatch(VERSION_NUMBER)
  }
}

describe('PromptManagementPage', () => {
  beforeEach(() => {
    for (const fn of Object.values(api)) fn.mockReset()
    toast.mockReset()
    HISTORY = structuredClone(BASE_HISTORY)
    api.listConfigs.mockResolvedValue(CONFIGS)
    api.validateDraft.mockResolvedValue({ valid: true, errors: [] })
    api.listEvaluations.mockResolvedValue([])
    api.currentEvaluation.mockResolvedValue(null)
    api.listVersions.mockImplementation(async (key: string) => HISTORY[key])
    api.getVersion.mockImplementation(async (key: string, v: number) => full(key, HISTORY[key]!.find((h) => h.version === v)!))
  })

  it('shows the pre-publish check passed inside the action bar without pressing anything', async () => {
    const w = await mountPage()
    const bar = w.find('.pm-actionbar')
    expect(bar.text()).toContain('✓ 發布前檢查通過')
    expect(w.findAll('button').some((b) => b.text().includes('檢查'))).toBe(false)
  })

  it('opens the system prompt draft in the editor with the page header', async () => {
    const w = await mountPage()
    expect(w.find('.breadcrumb').text()).toBe('系統管理/Prompt 管理')
    expect(w.find('.pm-confighead h2').text()).toBe('主要系統提示')
    expect(pickerButton(w).text()).toContain('主要系統提示')
    expect(w.find('textarea').exists()).toBe(true)
    expectNoVersionNumbers(w)
  })

  it('shows the published version read-only with 編輯 when there is no working copy', async () => {
    const w = await mountPage()
    await openInstructions(w, '顧客概況')

    expect(w.find('textarea').exists()).toBe(false)
    expect(w.find('.pm-confighead').text()).toContain('instruction.customer-overview・目前生效：10/19 08:00（system 發布）')
    expect(w.text()).not.toContain('正在檢視')
    expect(w.find('.pm-viewer .md').text()).toBe('內容：PUBLISHED')
    expectNoVersionNumbers(w)

    api.createDraft.mockResolvedValue(full('instruction.customer-overview', item(9122, 'DRAFT', T.v3)))
    HISTORY['instruction.customer-overview'] = [item(9122, 'DRAFT', T.v3), item(9121, 'PUBLISHED', T.v1, { updatedBy: 'system' })]
    await byText(w, '.pm-actionbar button', '編輯').trigger('click')
    await flushPromises()

    expect(api.createDraft).toHaveBeenCalledWith('instruction.customer-overview')
    expect(w.find('textarea').exists()).toBe(true)
  })

  it('asks to confirm applying over a scheduled version with the spec wording, then opens the new draft on the edit tab', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await byText(w, '[role="tab"]', '預覽').trigger('click')
    expect(w.find('textarea').exists()).toBe(false)
    await openTab(w, '版本')
    api.applyVersion.mockRejectedValueOnce(
      new ApiError(409, 'AI_CONFIG_OVERWRITE_PENDING', 'x', 'r', [
        { status: 'SCHEDULED', scheduledAt: T.sched, updatedAt: T.v3, docVersion: 7 },
      ]),
    )

    await applyFromRow(w, '已封存')

    const dialog = w.find('[role="dialog"]')
    expect(dialog.find('.dialog__title').text()).toBe('以 10/19 08:00 的版本建立草稿？')
    expect(dialog.find('.dialog__text').text()).toBe(
      '目前預約於 10/24 09:00 發布的版本會取消排程並改為已捨棄，可在版本列表找回。',
    )
    expect(dialog.text()).not.toContain('套用後')
    expect(dialog.find('.dialog__summary').exists()).toBe(false)
    expect(dialog.find('.dialog__actions').text()).toContain('建立草稿')
    expectNoVersionNumbers(w)

    HISTORY['instruction.contact-prep'] = [
      item(9114, 'DRAFT', '2026-10-21T10:00:00+08:00', { basedOnVersion: 9111 }),
      ...HISTORY['instruction.contact-prep']!.slice(1),
    ]
    api.applyVersion.mockResolvedValueOnce(full('instruction.contact-prep', HISTORY['instruction.contact-prep']![0]!))
    await byText(w, '[role="dialog"] button', '建立草稿').trigger('click')
    await flushPromises()

    expect(api.applyVersion).toHaveBeenLastCalledWith('instruction.contact-prep', 9111, {
      workingCopyDocVersion: 7,
      confirmOverwrite: true,
    })
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(w.find('.pm-confighead .badge').text()).toBe('草稿')
    expect(pageTab(w, '內容').attributes('aria-selected')).toBe('true')
    expect(textarea(w).value).toBe('內容：DRAFT') // the applied version is the new draft
    await openTab(w, '版本')
    expectNoVersionNumbers(w)
  })

  it('reloads from the conflict banner of the 版本 tab after a 409 on apply', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await openTab(w, '版本')
    api.applyVersion.mockRejectedValueOnce(
      new ApiError(409, 'AI_CONFIG_OVERWRITE_PENDING', 'x', 'r', [
        { status: 'SCHEDULED', scheduledAt: T.sched, updatedAt: T.v3, docVersion: 7 },
      ]),
    )
    await applyFromRow(w, '已封存')
    api.applyVersion.mockRejectedValueOnce(new ApiError(409, 'AI_CONFIG_CONFLICT', 'x'))
    await byText(w, '[role="dialog"] button', '建立草稿').trigger('click')
    await flushPromises()
    await openTab(w, '版本')
    const calls = api.listVersions.mock.calls.length
    await w.find('.pm-versions-tab .pm-banner--conflict button').trigger('click')
    await flushPromises()
    expect(api.listVersions.mock.calls.length).toBeGreaterThan(calls)
  })

  it('uses the draft wording when the working copy is a draft', async () => {
    const w = await mountPage()
    await openTab(w, '版本')
    api.applyVersion.mockRejectedValueOnce(
      new ApiError(409, 'AI_CONFIG_OVERWRITE_PENDING', 'x', 'r', [
        { status: 'DRAFT', scheduledAt: null, updatedAt: T.v2, docVersion: 5 },
      ]),
    )
    await applyFromRow(w, '目前生效')
    expect(w.find('.dialog__title').text()).toBe('以 10/19 08:00 的版本建立草稿？')
    expect(w.find('.dialog__text').text()).toBe('目前 10/19 09:30 儲存的草稿會改為已捨棄，可在版本列表找回。')
    expect(w.find('[role="dialog"]').text()).not.toContain('套用後')
    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(api.applyVersion).toHaveBeenCalledTimes(1)
  })

  it('opens a version read-only and compares two checked versions without showing version numbers', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await openTab(w, '版本')

    await byText(w, '.pm-ver', '已封存').find('.pm-ver__main').trigger('click')
    await flushPromises()
    expect(w.find('.pm-viewer .md').text()).toBe('內容：ARCHIVED')
    expect(w.text()).not.toContain('正在檢視')
    expectNoVersionNumbers(w)

    const checks = w.findAll('.pm-ver__check') // the default diff checked the scheduled and published rows
    await checks[0]!.setValue(false)
    await checks[2]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    await flushPromises()

    const diff = w.find('.pm-diff')
    expect(diff.text()).toContain('名稱相同')
    expect(diff.find('.pm-dl--del').text()).toContain('內容：ARCHIVED')
    expect(diff.find('.pm-dl--add').text()).toContain('內容：PUBLISHED')
    expect(api.getVersion).toHaveBeenCalledWith('instruction.contact-prep', 9111)
    expect(api.getVersion).toHaveBeenCalledWith('instruction.contact-prep', 9112)
    expectNoVersionNumbers(w)
  })

  it('says so when two versions are too long to compare line by line', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await openTab(w, '版本')
    const lines = (p: string) => Array.from({ length: 2001 }, (_, i) => p + i).join('\n')
    api.getVersion.mockImplementation(async (key: string, v: number) =>
      full(key, HISTORY[key]!.find((h) => h.version === v)!, lines(v === 9111 ? 'a' : 'b')),
    )
    const checks = w.findAll('.pm-ver__check')
    await checks[0]!.setValue(false)
    await checks[2]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    await flushPromises()
    expect(toast).toHaveBeenCalledWith('內容過長，無法逐行比較')
    expect(w.find('.pm-versions-tab .pm-diff').exists()).toBe(false) // no diff pane opened
    expect(w.find('.pm-versions-tab .pm-viewer').exists()).toBe(true) // the live version instead
  })

  it('asks before switching config with unsaved edits; cancel keeps them, confirm switches', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await w.find('textarea').setValue('我的未儲存內容')

    await pickConfig(w, '顧客概況')
    const dialog = () => w.find('[role="dialog"]')
    expect(dialog().text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    expect(api.listVersions).not.toHaveBeenCalledWith('instruction.customer-overview')

    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(dialog().exists()).toBe(false)
    expect(textarea(w).value).toBe('我的未儲存內容')
    expect(w.find('.pm-confighead h2').text()).toBe('聯繫準備')
    expect(w.find('[role="option"]').exists()).toBe(false) // the panel closed

    // picking the system prompt is guarded the same way
    await pickConfig(w, '主要系統提示')
    expect(dialog().text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(textarea(w).value).toBe('我的未儲存內容')

    await pickConfig(w, '顧客概況')
    await byText(w, '[role="dialog"] button', '放棄修改').trigger('click')
    await flushPromises()
    expect(api.listVersions).toHaveBeenCalledWith('instruction.customer-overview')
    expect(w.find('.pm-viewer').text()).toContain('顧客概況')
  })

  it('opens the evaluation tab and returns to 內容 with unsaved edits intact', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await w.find('textarea').setValue('我的未儲存內容')

    await openTab(w, '評測')
    expect(w.find('textarea').exists()).toBe(false)
    expect(w.text()).toContain('評測：聯繫準備')
    expect(w.text()).toContain('請先儲存，評測使用已儲存的內容')
    expect(api.listEvaluations).toHaveBeenCalledWith('instruction.contact-prep')
    expect(w.text()).not.toContain('返回')

    await openTab(w, '內容')
    expect(textarea(w).value).toBe('我的未儲存內容')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
  })

  it('shows the header meta and the three tabs', async () => {
    const w = await mountPage()
    expect(w.find('.pm-confighead').text()).toContain('system.main・目前生效：10/19 08:00（EEE22222 發布）')
    expect(w.findAll('.pm-pagetabs [role="tab"]').map((t) => t.text())).toEqual(['內容', '版本', '評測'])
    expect(pageTab(w, '內容').attributes('aria-selected')).toBe('true')
  })

  it('puts the header, the tabs and the active tab body in one main card', async () => {
    const w = await mountPage()
    for (const label of ['內容', '版本', '評測']) {
      await openTab(w, label)
      const main = w.findAll('.pm-main.card')
      expect(main).toHaveLength(1)
      expect(main[0]!.find('.pm-confighead').exists()).toBe(true)
      expect(main[0]!.find('.pm-pagetabs').exists()).toBe(true)
      expect(main[0]!.find('.pm-editor, .pm-versions-tab, .pm-eval').exists()).toBe(true)
      expect(main[0]!.find('.card').exists()).toBe(false)
    }
  })

  it('the picker lists configs and selecting one switches config', async () => {
    const w = await mountPage()
    expect(w.find('.pm-sidebar').exists()).toBe(false)
    expect(w.find('.pm-layout').exists()).toBe(false)

    await openPicker(w)
    const panel = w.find('[role="listbox"]').text()
    expect(panel).toContain('System Prompt')
    expect(panel).toContain('問題分類 2')
    expect(panel).not.toContain('System Instructions')
    expect(panel).not.toContain('system.main') // names and status only
    expect(panel).not.toContain('已發布')
    expect(byText(w, '[role="option"]', '聯繫準備').text()).toContain('已排程 10/24 09:00')
    expect(pickerOptions(w).find((o) => o.attributes('aria-selected') === 'true')!.text()).toContain('主要系統提示')

    await byText(w, '[role="option"]', '聯繫準備').trigger('click')
    await flushPromises()
    expect(w.find('.pm-confighead h2').text()).toBe('聯繫準備')
    expect(pickerButton(w).text()).toContain('聯繫準備')
    expect(pageTab(w, '內容').attributes('aria-selected')).toBe('true')
    expect(w.find('[role="option"]').exists()).toBe(false)
  })

  it('selects the first system prompt when the list loads, else the first question category', async () => {
    api.listConfigs.mockResolvedValue([CONFIGS[2]!, CONFIGS[1]!, CONFIGS[0]!])
    expect(pickerButton(await mountPage()).text()).toContain('主要系統提示')

    api.listConfigs.mockResolvedValue([CONFIGS[2]!, CONFIGS[1]!])
    expect(pickerButton(await mountPage()).text()).toContain('顧客概況')
  })

  it('keeps the picker above the empty card when there are no configs', async () => {
    api.listConfigs.mockResolvedValue([])
    const w = await mountPage()
    expect(w.find('.pm-confighead').exists()).toBe(false)
    expect(w.find('button[aria-haspopup="listbox"]').text()).toContain('請選擇設定')
    expect(w.find('.pm-empty').text()).toContain('請從上方選單選擇一個設定')
  })

  it('keeps the header and picker mounted and focused while switching config', async () => {
    const w = await mountPage(document.body)
    const before = pickerButton(w).element
    const metaBefore = w.find('.pm-confighead__meta').exists()
    expect(metaBefore).toBe(true)

    let release!: (v: VersionHistoryItem[]) => void
    api.listVersions.mockImplementation((key: string) =>
      key === 'instruction.customer-overview' ? new Promise<VersionHistoryItem[]>((r) => (release = r)) : Promise.resolve(HISTORY[key]!),
    )
    await openPicker(w)
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    await input(w).trigger('keydown', { key: 'ArrowDown' })
    await input(w).trigger('keydown', { key: 'Enter' })
    await flushPromises()

    // still loading: same header, same button, the new name, no badge or meta, 載入中… in the body
    expect(pickerButton(w).element).toBe(before)
    expect(document.activeElement).toBe(before)
    expect(pickerButton(w).text()).toContain('顧客概況')
    expect(w.find('.pm-confighead h2').text()).toBe('顧客概況')
    expect(w.find('.pm-confighead .badge').exists()).toBe(false)
    expect(w.find('.pm-confighead__meta').exists()).toBe(false)
    expect(w.find('.pm-main').text()).toContain('載入中…')
    expect(w.find('.pm-emptypick').exists()).toBe(false)

    release(HISTORY['instruction.customer-overview']!)
    await flushPromises()
    expect(pickerButton(w).element).toBe(before)
    expect(document.activeElement).toBe(before)
    expect(w.find('.pm-confighead .badge').text()).toBe('目前生效')
    expect(w.find('.pm-confighead__meta').text()).toContain('instruction.customer-overview')
  })

  it('switching config returns to the 內容 tab', async () => {
    const w = await mountPage()
    await pickConfig(w, '顧客概況')
    await openTab(w, '版本')
    expect(pageTab(w, '版本').attributes('aria-selected')).toBe('true')
    await pickConfig(w, '聯繫準備')
    expect(pageTab(w, '內容').attributes('aria-selected')).toBe('true')
  })

  it('查看問題分類 opens the picker', async () => {
    const w = await mountPage()
    expect(w.find('[role="listbox"]').exists()).toBe(false)
    await byText(w, 'button', '查看問題分類').trigger('click')
    expect(pickerButton(w).attributes('aria-expanded')).toBe('true')
    expect(w.find('[role="listbox"]').exists()).toBe(true)
  })

  it('switching tabs keeps unsaved edits without asking', async () => {
    const w = await mountPage()
    await w.find('input.input').setValue('改過的名稱')
    await openTab(w, '版本')
    await openTab(w, '內容')
    expect((w.find('input.input').element as HTMLInputElement).value).toBe('改過的名稱')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
  })

  it('cancelling the discard dialog keeps the config and the tab', async () => {
    const w = await mountPage()
    await w.find('textarea').setValue('我的未儲存內容')
    await openTab(w, '版本')
    await pickConfig(w, '聯繫準備')
    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    await flushPromises()
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(w.find('.pm-confighead h2').text()).toBe('主要系統提示')
    expect(pageTab(w, '版本').attributes('aria-selected')).toBe('true')
  })

  it('opens the 版本 tab on the published vs draft diff, or on the live version without a draft', async () => {
    const w = await mountPage()
    await openTab(w, '版本')
    expect(w.find('.pm-versions-tab .pm-diff').exists()).toBe(true)
    expect(w.findAll('.pm-ver__check').filter((c) => (c.element as HTMLInputElement).checked)).toHaveLength(2)

    await pickConfig(w, '顧客概況')
    await openTab(w, '版本')
    expect(w.find('.pm-versions-tab .pm-diff').exists()).toBe(false)
    expect(w.find('.pm-versions-tab .pm-viewer .md').text()).toBe('內容：PUBLISHED')
    expectNoVersionNumbers(w)
  })

  it('clicking the draft row in 版本 opens 內容', async () => {
    const w = await mountPage()
    await openTab(w, '版本')
    await byText(w, '.pm-ver', '草稿').find('.pm-ver__main').trigger('click')
    await flushPromises()
    expect(pageTab(w, '內容').attributes('aria-selected')).toBe('true')
    expect(w.find('textarea').exists()).toBe(true)
  })

  it('has no back buttons or viewing banners anywhere', async () => {
    const w = await mountPage()
    await openTab(w, '版本')
    await byText(w, '.pm-ver', '目前生效').find('.pm-ver__main').trigger('click')
    await flushPromises()
    expect(w.text()).not.toContain('返回')
    expect(w.text()).not.toContain('正在檢視')
  })

  it('switches config without asking when there are no unsaved edits', async () => {
    const w = await mountPage()
    await openInstructions(w, '顧客概況')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(w.find('.pm-viewer').text()).toContain('顧客概況')
  })

  it('asks before reloading after a conflict, then replaces the form', async () => {
    const w = await mountPage()
    await w.find('textarea').setValue('我的未儲存內容')
    api.saveDraft.mockRejectedValue(new ApiError(409, 'AI_CONFIG_CONFLICT', 'x'))
    await byText(w, '.pm-actionbar button', '儲存').trigger('click')
    await flushPromises()

    await w.find('.pm-banner--conflict button').trigger('click')
    expect(w.find('[role="dialog"]').text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    expect(textarea(w).value).toBe('我的未儲存內容')

    await byText(w, '[role="dialog"] button', '放棄修改').trigger('click')
    await flushPromises()
    expect(w.find('.pm-banner--conflict').exists()).toBe(false)
    expect(textarea(w).value).toBe('內容：DRAFT')
  })

  it('asks before discarding a draft with unsaved edits', async () => {
    const w = await mountPage()
    await w.find('textarea').setValue('我的未儲存內容')
    await byText(w, '.pm-actionbar button', '捨棄草稿').trigger('click')
    expect(w.find('[role="dialog"]').text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    expect(api.discardDraft).not.toHaveBeenCalled()
    await byText(w, '[role="dialog"] button', '放棄修改').trigger('click')
    await flushPromises()
    expect(api.discardDraft).toHaveBeenCalledWith('system.main', 5)
  })

  describe('leaving the page with unsaved edits', () => {
    const beforeUnload = () => {
      const event = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(event)
      return event
    }

    it('asks first; cancel keeps the user on the page and the edits', async () => {
      const w = await mountPage()
      await w.find('textarea').setValue('我的未儲存內容')

      const navigation = router.push('/other') // settles only once the dialog is answered
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/prompt')
      expect(w.find('[role="dialog"]').text()).toContain('有尚未儲存的修改，確定要放棄嗎？')

      await byText(w, '[role="dialog"] button', '取消').trigger('click')
      await navigation
      await flushPromises()
      expect(router.currentRoute.value.path).toBe('/prompt')
      expect(w.find('[role="dialog"]').exists()).toBe(false)
      expect(textarea(w).value).toBe('我的未儲存內容')
    })

    it('confirming leaves the page', async () => {
      const w = await mountPage()
      await w.find('textarea').setValue('我的未儲存內容')

      const navigation = router.push('/other')
      await flushPromises()
      await byText(w, '[role="dialog"] button', '放棄修改').trigger('click')
      await navigation
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/other')
      expect(w.text()).toContain('其他頁面')
    })

    it('leaves without asking when the form is clean', async () => {
      const w = await mountPage()

      await router.push('/other')
      await flushPromises()

      expect(router.currentRoute.value.path).toBe('/other')
      expect(w.find('[role="dialog"]').exists()).toBe(false)
    })

    it('registers the beforeunload prompt only while the form is dirty', async () => {
      const w = await mountPage()
      expect(beforeUnload().defaultPrevented).toBe(false)

      await w.find('textarea').setValue('我的未儲存內容')
      expect(beforeUnload().defaultPrevented).toBe(true)

      await w.find('textarea').setValue('內容：DRAFT') // back to the saved text
      expect(beforeUnload().defaultPrevented).toBe(false)

      await w.find('textarea').setValue('又改了')
      expect(beforeUnload().defaultPrevented).toBe(true)
      w.unmount()
      expect(beforeUnload().defaultPrevented).toBe(false)
    })
  })

  describe('logout with unsaved edits (real router, real layout)', () => {
    async function mountApp() {
      const pinia = createPinia()
      setActivePinia(pinia)
      const auth = useAuthStore()
      auth.user = {
        username: 'EEE22222',
        displayName: 'IT',
        roleKey: 'ADMIN',
        roleName: 'IT 管理',
        permissions: ['AI_CONFIG_READ', 'AI_CONFIG_EDIT', 'AI_CONFIG_PUBLISH'],
      }
      const logoutCall = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null })
      const appRouter = createAppRouter(createMemoryHistory())
      await appRouter.push('/setting/prompt-management')
      const wrapper = mount({ render: () => h(RouterView) }, { global: { plugins: [pinia, appRouter] } })
      mounted.push(wrapper)
      await flushPromises()
      await wrapper.find('textarea').setValue('我的未儲存內容')
      return { wrapper, appRouter, auth, logoutCall }
    }

    it('asks once; confirming logs out and lands on /login', async () => {
      const { wrapper, appRouter, auth, logoutCall } = await mountApp()

      await wrapper.find('button.logout').trigger('click')
      await flushPromises()
      expect(wrapper.findAll('[role="dialog"]')).toHaveLength(1)

      await byText(wrapper, '[role="dialog"] button', '放棄修改').trigger('click')
      await flushPromises()

      // the lazy-loaded login route takes a real moment to resolve
      await vi.waitFor(() => expect(logoutCall).toHaveBeenCalledWith('/mock/auth/logout'))
      await flushPromises()
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false) // no second question
      expect(auth.user).toBeNull()
      expect(appRouter.currentRoute.value.path).toBe('/login')
    })

    it('cancelling keeps the user logged in on the page with the edits', async () => {
      const { wrapper, appRouter, auth, logoutCall } = await mountApp()

      await wrapper.find('button.logout').trigger('click')
      await flushPromises()
      await byText(wrapper, '[role="dialog"] button', '取消').trigger('click')
      await flushPromises()

      expect(logoutCall).not.toHaveBeenCalled()
      expect(auth.user).not.toBeNull()
      expect(appRouter.currentRoute.value.path).toBe('/setting/prompt-management')
      expect(textarea(wrapper).value).toBe('我的未儲存內容')
      expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    })
  })

  it('new instruction button is always shown and selects the new config', async () => {
    const w = await mountPage()
    expect(w.find('.pm-confighead h2').text()).toBe('主要系統提示') // a system prompt is selected
    await byText(w, 'button', '新增問題分類').trigger('click')
    const dialog = () => w.find('[role="dialog"]')
    const [keyInput, nameInput] = dialog().findAll('input')
    await keyInput!.setValue('phone-opening')
    await nameInput!.setValue('電話開場白')

    const created = full('instruction.phone-opening', item(9131, 'DRAFT', T.v3))
    created.name = '電話開場白'
    HISTORY['instruction.phone-opening'] = [item(9131, 'DRAFT', T.v3)]
    api.createConfig.mockResolvedValue(created)
    api.listConfigs.mockResolvedValue([
      ...CONFIGS,
      { configKey: 'instruction.phone-opening', configType: 'SYSTEM_INSTRUCTION', name: '電話開場白', published: null, workingCopy: { version: 9131, status: 'DRAFT', scheduledAt: null, updatedBy: 'EEE22222', updatedAt: T.v3 } },
    ])
    api.getVersion.mockImplementation(async (key: string, v: number) =>
      key === 'instruction.phone-opening' ? created : full(key, HISTORY[key]!.find((h) => h.version === v)!),
    )
    await dialog().find('form').trigger('submit')
    await flushPromises()

    expect(dialog().exists()).toBe(false)
    expect(w.find('.pm-confighead h2').text()).toBe('電話開場白')
    expect(pickerButton(w).text()).toContain('電話開場白')
  })

  it('creates a question category after checking the key format', async () => {
    const w = await mountPage()
    await byText(w, 'button', '新增問題分類').trigger('click')
    const dialog = () => w.find('[role="dialog"]')
    const [keyInput, nameInput] = dialog().findAll('input')

    await keyInput!.setValue('Phone Opening')
    await nameInput!.setValue('電話開場白')
    await dialog().find('form').trigger('submit')
    expect(dialog().text()).toContain('Key 只能使用小寫英數與連字號')
    expect(api.createConfig).not.toHaveBeenCalled()

    api.createConfig.mockResolvedValue(full('instruction.phone-opening', item(9131, 'DRAFT', T.v3)))
    await keyInput!.setValue('phone-opening')
    await dialog().find('form').trigger('submit')
    await flushPromises()

    expect(api.createConfig).toHaveBeenCalledWith({
      configKey: 'instruction.phone-opening',
      configType: 'SYSTEM_INSTRUCTION',
      name: '電話開場白',
    })
    expect(dialog().exists()).toBe(false)
  })
})
