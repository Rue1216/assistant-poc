import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
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
}))
vi.mock('../../api/aiAssistantApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/aiAssistantApi')>()),
  aiAssistantApi: api,
}))
const toast = vi.hoisted(() => vi.fn())
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: toast }) }))

import { ApiError } from '../../../../platform-mock/apiClient'
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
  return { version, status, scheduledAt: null, changeNote: null, basedOnVersion: null, createdBy: 'it.admin', updatedBy: 'it.admin', updatedAt, ...extra }
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
    item(9112, 'PUBLISHED', T.v2, { updatedBy: 'backoffice.admin' }),
    item(9111, 'ARCHIVED', T.v1, { updatedBy: 'system' }),
  ],
  'instruction.customer-overview': [item(9121, 'PUBLISHED', T.v1, { updatedBy: 'system' })],
}

let HISTORY: Record<string, VersionHistoryItem[]> = {}

const CONFIGS: ConfigSummary[] = [
  { configKey: 'system.main', configType: 'SYSTEM_PROMPT', name: '主要系統提示', published: { version: 9101, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'it.admin', updatedAt: T.v1 }, workingCopy: { version: 9102, status: 'DRAFT', scheduledAt: null, updatedBy: 'it.admin', updatedAt: T.v2 } },
  { configKey: 'instruction.contact-prep', configType: 'SYSTEM_INSTRUCTION', name: '聯繫準備', published: { version: 9112, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'backoffice.admin', updatedAt: T.v2 }, workingCopy: { version: 9113, status: 'SCHEDULED', scheduledAt: T.sched, updatedBy: 'it.admin', updatedAt: T.v3 } },
  { configKey: 'instruction.customer-overview', configType: 'SYSTEM_INSTRUCTION', name: '顧客概況', published: { version: 9121, status: 'PUBLISHED', scheduledAt: null, updatedBy: 'system', updatedAt: T.v1 }, workingCopy: null },
]

async function mountPage() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().user = {
    username: 'it.admin',
    displayName: 'IT',
    roleKey: 'IT_ADMIN',
    roleName: 'IT 管理',
    permissions: ['AI_CONFIG_READ', 'AI_CONFIG_EDIT', 'AI_CONFIG_PUBLISH'],
  }
  const wrapper = mount(PromptManagementPage, { global: { plugins: [pinia] } })
  await flushPromises()
  return wrapper
}

const byText = (w: VueWrapper, selector: string, text: string) => w.findAll(selector).find((e) => e.text().includes(text))!
const textarea = (w: VueWrapper) => w.find('textarea').element as HTMLTextAreaElement

async function openInstructions(w: VueWrapper, name: string) {
  await byText(w, '[role="tab"]', 'System Instructions').trigger('click')
  await flushPromises()
  await byText(w, '.pm-item__main', name).trigger('click')
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
    api.listVersions.mockImplementation(async (key: string) => HISTORY[key])
    api.getVersion.mockImplementation(async (key: string, v: number) => full(key, HISTORY[key]!.find((h) => h.version === v)!))
  })

  it('opens the system prompt draft in the editor with the page header', async () => {
    const w = await mountPage()
    expect(w.find('.breadcrumb').text()).toBe('系統管理/Prompt 管理')
    expect(w.find('.pm-editor__title').text()).toBe('主要系統提示')
    expect(w.find('textarea').exists()).toBe(true)
    expect(w.text()).not.toContain('新增問題分類') // instruction tab only
    expectNoVersionNumbers(w)
  })

  it('shows the published version read-only with 建立草稿 when there is no working copy', async () => {
    const w = await mountPage()
    await openInstructions(w, '顧客概況')

    expect(w.find('textarea').exists()).toBe(false)
    expect(w.find('.pm-viewer').text()).toContain('正在檢視 2026/10/19 08:00 發布的版本（目前生效），內容唯讀。')
    expect(w.find('.pm-viewer .md').text()).toBe('內容：PUBLISHED')
    expectNoVersionNumbers(w)

    api.createDraft.mockResolvedValue(full('instruction.customer-overview', item(9122, 'DRAFT', T.v3)))
    HISTORY['instruction.customer-overview'] = [item(9122, 'DRAFT', T.v3), item(9121, 'PUBLISHED', T.v1, { updatedBy: 'system' })]
    await byText(w, '.pm-actionbar button', '建立草稿').trigger('click')
    await flushPromises()

    expect(api.createDraft).toHaveBeenCalledWith('instruction.customer-overview')
    expect(w.find('textarea').exists()).toBe(true)
  })

  it('asks to confirm applying over a scheduled version with the spec wording, then opens the new draft on the edit tab', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await byText(w, '[role="tab"]', '預覽').trigger('click')
    expect(w.find('textarea').exists()).toBe(false)
    api.applyVersion.mockRejectedValueOnce(
      new ApiError(409, 'AI_CONFIG_OVERWRITE_PENDING', 'x', 'r', [
        { status: 'SCHEDULED', scheduledAt: T.sched, updatedAt: T.v3, docVersion: 7 },
      ]),
    )

    await byText(w, '.pm-ver', '已封存').find('.pm-ver__apply').trigger('click')
    await flushPromises()

    const dialog = w.find('[role="dialog"]')
    expect(dialog.find('.dialog__title').text()).toBe('套用 2026/10/19 08:00 的版本？')
    expect(dialog.find('.dialog__text').text()).toBe(
      '目前有一份預約於 2026/10/24 09:00 發布的版本，套用後排程會取消，該版本會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？',
    )
    expectNoVersionNumbers(w)

    HISTORY['instruction.contact-prep'] = [
      item(9114, 'DRAFT', '2026-10-21T10:00:00+08:00', { basedOnVersion: 9111 }),
      ...HISTORY['instruction.contact-prep']!.slice(1),
    ]
    api.applyVersion.mockResolvedValueOnce(full('instruction.contact-prep', HISTORY['instruction.contact-prep']![0]!))
    await byText(w, '[role="dialog"] button', '確定套用').trigger('click')
    await flushPromises()

    expect(api.applyVersion).toHaveBeenLastCalledWith('instruction.contact-prep', 9111, {
      workingCopyDocVersion: 7,
      confirmOverwrite: true,
    })
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(w.find('.pm-editor__head .badge').text()).toBe('草稿')
    expect(w.find('textarea').exists()).toBe(true) // back on the 編輯 tab
    expect(w.find('.pm-ver__from').text()).toBe('套用自 10/19 08:00 的版本')
    expectNoVersionNumbers(w)
  })

  it('uses the draft wording when the working copy is a draft', async () => {
    const w = await mountPage()
    api.applyVersion.mockRejectedValueOnce(
      new ApiError(409, 'AI_CONFIG_OVERWRITE_PENDING', 'x', 'r', [
        { status: 'DRAFT', scheduledAt: null, updatedAt: T.v2, docVersion: 5 },
      ]),
    )
    await byText(w, '.pm-ver', '已發布').find('.pm-ver__apply').trigger('click')
    await flushPromises()
    expect(w.find('.dialog__text').text()).toBe(
      '目前有一份 2026/10/19 09:30 儲存的草稿，套用後它會被標為已捨棄（仍可在版本歷程中找回）。確定要套用嗎？',
    )
    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    expect(api.applyVersion).toHaveBeenCalledTimes(1)
  })

  it('opens a version read-only and compares two checked versions without showing version numbers', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')

    await byText(w, '.pm-ver', '已封存').find('.pm-ver__main').trigger('click')
    await flushPromises()
    expect(w.find('.pm-viewer').text()).toContain('正在檢視 2026/10/19 08:00 發布的版本（已封存），內容唯讀。')
    expect(w.find('.pm-viewer').text()).toContain('它已被 2026/10/19 09:30 發布的版本取代。')
    expectNoVersionNumbers(w)

    const checks = w.findAll('.pm-ver__check')
    await checks[1]!.setValue(true)
    await checks[2]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    await flushPromises()

    const diff = w.find('.pm-viewer')
    expect(diff.text()).toContain('版本比較')
    expect(diff.find('.pm-dl--del').text()).toContain('內容：ARCHIVED')
    expect(diff.find('.pm-dl--add').text()).toContain('內容：PUBLISHED')
    expect(api.getVersion).toHaveBeenCalledWith('instruction.contact-prep', 9111)
    expect(api.getVersion).toHaveBeenCalledWith('instruction.contact-prep', 9112)
    expectNoVersionNumbers(w)
  })

  it('says so when two versions are too long to compare line by line', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    const lines = (p: string) => Array.from({ length: 2001 }, (_, i) => p + i).join('\n')
    api.getVersion.mockImplementation(async (key: string, v: number) =>
      full(key, HISTORY[key]!.find((h) => h.version === v)!, lines(v === 9111 ? 'a' : 'b')),
    )
    const checks = w.findAll('.pm-ver__check')
    await checks[1]!.setValue(true)
    await checks[2]!.setValue(true)
    await w.find('.pm-versions__compare').trigger('click')
    await flushPromises()
    expect(toast).toHaveBeenCalledWith('內容過長，無法逐行比較')
    expect(w.find('.pm-editor').exists()).toBe(true) // stays on the editor
  })

  it('asks before switching config with unsaved edits; cancel keeps them, confirm switches', async () => {
    const w = await mountPage()
    await openInstructions(w, '聯繫準備')
    await w.find('textarea').setValue('我的未儲存內容')

    await byText(w, '.pm-item__main', '顧客概況').trigger('click')
    await flushPromises()
    const dialog = () => w.find('[role="dialog"]')
    expect(dialog().text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    expect(api.listVersions).not.toHaveBeenCalledWith('instruction.customer-overview')

    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(dialog().exists()).toBe(false)
    expect(textarea(w).value).toBe('我的未儲存內容')
    expect(w.find('.pm-editor__title').text()).toBe('聯繫準備')

    // switching the SP / SI tab is guarded the same way
    await byText(w, '[role="tab"]', 'System Prompt').trigger('click')
    expect(dialog().text()).toContain('有尚未儲存的修改，確定要放棄嗎？')
    await byText(w, '[role="dialog"] button', '取消').trigger('click')
    expect(textarea(w).value).toBe('我的未儲存內容')

    await byText(w, '.pm-item__main', '顧客概況').trigger('click')
    await byText(w, '[role="dialog"] button', '放棄修改').trigger('click')
    await flushPromises()
    expect(api.listVersions).toHaveBeenCalledWith('instruction.customer-overview')
    expect(w.find('.pm-viewer').text()).toContain('顧客概況')
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

  it('filters the list by name or key', async () => {
    const w = await mountPage()
    await byText(w, '[role="tab"]', 'System Instructions').trigger('click')
    const search = w.find('.pm-search input')
    await search.setValue('overview')
    expect(w.findAll('.pm-item__name').map((n) => n.text())).toEqual(['顧客概況'])
    await search.setValue('聯繫')
    expect(w.findAll('.pm-item__name').map((n) => n.text())).toEqual(['聯繫準備'])
  })

  it('creates a question category after checking the key format', async () => {
    const w = await mountPage()
    await byText(w, '[role="tab"]', 'System Instructions').trigger('click')
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
