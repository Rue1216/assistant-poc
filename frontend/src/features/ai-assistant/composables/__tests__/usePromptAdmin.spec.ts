import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import type { ConfigStatus, ConfigVersion, VersionHistoryItem } from '../../types'

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
const toast = vi.hoisted(() => vi.fn())
vi.mock('../../api/aiAssistantApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/aiAssistantApi')>()),
  aiAssistantApi: api,
}))
vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: toast }) }))

import { ApiError } from '../../../../platform-mock/apiClient'
import { usePromptAdmin } from '../usePromptAdmin'

const KEY = 'instruction.contact-prep'

function item(version: number, status: ConfigStatus, extra: Partial<VersionHistoryItem> = {}): VersionHistoryItem {
  return {
    version,
    status,
    scheduledAt: null,
    changeNote: null,
    basedOnVersion: null,
    createdBy: 'it.admin',
    updatedBy: 'it.admin',
    updatedAt: `2026-10-19T0${version}:00:00+08:00`,
    ...extra,
  }
}

function full(version: number, status: ConfigStatus, extra: Partial<ConfigVersion> = {}): ConfigVersion {
  return {
    configKey: KEY,
    configType: 'SYSTEM_INSTRUCTION',
    name: '聯繫準備',
    content: `## 適用問題\n版本 ${version}`,
    exampleQuestions: [],
    status,
    version,
    basedOnVersion: null,
    changeNote: null,
    createdBy: 'it.admin',
    createdAt: '2026-10-19T08:00:00+08:00',
    updatedBy: 'it.admin',
    updatedAt: '2026-10-19T08:00:00+08:00',
    scheduledAt: null,
    docVersion: 3,
    ...extra,
  }
}

/** Server state: v1 published; optionally a working copy v2. */
function serve(working: ConfigVersion | null) {
  const published = full(1, 'PUBLISHED', { docVersion: 0 })
  const items = [item(1, 'PUBLISHED')]
  if (working) items.unshift(item(working.version, working.status, { scheduledAt: working.scheduledAt }))
  api.listVersions.mockResolvedValue(items)
  api.getVersion.mockImplementation(async (_key: string, v: number) => (v === 1 ? published : working))
}

async function setup(working: ConfigVersion | null = full(2, 'DRAFT', { docVersion: 3 })) {
  serve(working)
  const admin = usePromptAdmin(ref(KEY))
  await flushPromises()
  return admin
}

function apiError(status: number, code: string, details: unknown[] = []): ApiError {
  return new ApiError(status, code, code, 'req', details)
}

describe('usePromptAdmin', () => {
  beforeEach(() => {
    for (const fn of Object.values(api)) fn.mockReset()
    toast.mockReset()
    api.listConfigs.mockResolvedValue([])
  })

  it('loads the working copy and the published version into state and the form', async () => {
    const admin = await setup()
    expect(api.listVersions).toHaveBeenCalledWith(KEY)
    expect(admin.state.workingCopy?.version).toBe(2)
    expect(admin.state.published?.version).toBe(1)
    expect(admin.state.form.content).toBe('## 適用問題\n版本 2')
    expect(admin.dirty.value).toBe(false)
    admin.state.form.content += '\n改了'
    expect(admin.dirty.value).toBe(true)
  })

  it('save KEEP_STATUS calls PUT draft with saveMode KEEP_STATUS', async () => {
    const admin = await setup()
    admin.state.form.name = '新名稱'
    admin.state.form.changeNote = '調整'
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { name: '新名稱', changeNote: '調整', docVersion: 4 }))

    await admin.save('KEEP_STATUS')

    expect(api.saveDraft).toHaveBeenCalledWith(KEY, {
      name: '新名稱',
      content: '## 適用問題\n版本 2',
      changeNote: '調整',
      saveMode: 'KEEP_STATUS',
      docVersion: 3,
    })
    expect(admin.state.workingCopy?.docVersion).toBe(4)
    expect(admin.dirty.value).toBe(false)
  })

  it('save DRAFT on scheduled sends saveMode DRAFT', async () => {
    const admin = await setup(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00', docVersion: 5 }))
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { docVersion: 6 }))

    await admin.save('DRAFT')

    expect(api.saveDraft.mock.calls[0]![1]).toMatchObject({ saveMode: 'DRAFT', docVersion: 5, changeNote: null })
    expect(admin.state.workingCopy?.status).toBe('DRAFT')
  })

  it('publish saves first then publishes with returned docVersion', async () => {
    const admin = await setup()
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { docVersion: 7 }))
    api.publish.mockResolvedValue(full(2, 'PUBLISHED', { docVersion: 8 }))

    await admin.publish('IMMEDIATE')

    expect(api.saveDraft.mock.calls[0]![1]).toMatchObject({ saveMode: 'KEEP_STATUS', docVersion: 3 })
    expect(api.publish).toHaveBeenCalledWith(KEY, { publishMode: 'IMMEDIATE', docVersion: 7 })
    expect(api.saveDraft.mock.invocationCallOrder[0]).toBeLessThan(api.publish.mock.invocationCallOrder[0]!)
  })

  it('scheduled publish sends scheduledAt with +08:00', async () => {
    const admin = await setup()
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { docVersion: 7 }))
    api.publish.mockResolvedValue(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00' }))

    await admin.publish('SCHEDULED', '2026-10-24', '09:00')

    expect(api.publish).toHaveBeenCalledWith(KEY, {
      publishMode: 'SCHEDULED',
      scheduledAt: '2026-10-24T09:00:00+08:00',
      docVersion: 7,
    })
  })

  it('does not publish when the save fails', async () => {
    const admin = await setup()
    api.saveDraft.mockRejectedValue(apiError(409, 'AI_CONFIG_CONFLICT'))
    await admin.publish('IMMEDIATE')
    expect(api.publish).not.toHaveBeenCalled()
  })

  it('shows an out-of-range schedule under the inputs and toasts other input errors', async () => {
    const admin = await setup()
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { docVersion: 7 }))
    api.publish.mockRejectedValueOnce(apiError(400, 'AI_INPUT_INVALID', [{ field: 'scheduledAt' }]))

    await admin.publish('SCHEDULED', '2026-10-21', '09:00')
    expect(admin.state.scheduleError).toBe('預約時間需在 1 分鐘後、90 天內')
    expect(toast).not.toHaveBeenCalled()

    api.publish.mockRejectedValueOnce(apiError(400, 'AI_INPUT_INVALID', [{ field: 'docVersion' }]))
    await admin.publish('IMMEDIATE')
    expect(admin.state.scheduleError).toBeNull()
    expect(toast).toHaveBeenCalledWith('AI_INPUT_INVALID')
  })

  it('requires a date and time for scheduled publishing without calling the API', async () => {
    const admin = await setup()
    await admin.publish('SCHEDULED', '', '')
    expect(admin.state.scheduleError).toBe('預約時間需在 1 分鐘後、90 天內')
    expect(api.saveDraft).not.toHaveBeenCalled()
  })

  it('apply without working copy returns the new draft', async () => {
    const admin = await setup(null)
    const draft = full(3, 'DRAFT', { basedOnVersion: 1 })
    api.applyVersion.mockResolvedValue(draft)

    const result = await admin.apply(1)

    expect(api.applyVersion).toHaveBeenCalledWith(KEY, 1, { workingCopyDocVersion: null })
    expect(result).toEqual({ needsConfirm: false, draft })
  })

  it('apply over scheduled asks for confirmation then resends with flag and docVersion', async () => {
    const admin = await setup(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00', docVersion: 5 }))
    const detail = {
      status: 'SCHEDULED',
      scheduledAt: '2026-10-24T09:00:00+08:00',
      updatedAt: '2026-10-20T14:32:00+08:00',
      docVersion: 7,
    }
    api.applyVersion.mockRejectedValueOnce(apiError(409, 'AI_CONFIG_OVERWRITE_PENDING', [detail]))

    const first = await admin.apply(1)
    expect(first).toEqual({ needsConfirm: true, ...detail })
    expect(api.applyVersion).toHaveBeenCalledTimes(1)

    const draft = full(3, 'DRAFT', { basedOnVersion: 1 })
    api.applyVersion.mockResolvedValueOnce(draft)
    const confirmed = await admin.confirmApply()

    expect(api.applyVersion).toHaveBeenLastCalledWith(KEY, 1, { workingCopyDocVersion: 7, confirmOverwrite: true })
    const body = api.applyVersion.mock.calls[1]![2]
    expect(body.confirmOverwrite).toBe(true)
    expect(body.workingCopyDocVersion).toBe(detail.docVersion)
    expect(confirmed).toEqual(draft)
    expect(admin.state.pendingApply).toBeNull()
  })

  it('cancelling a pending apply sends nothing more', async () => {
    const admin = await setup()
    api.applyVersion.mockRejectedValueOnce(
      apiError(409, 'AI_CONFIG_OVERWRITE_PENDING', [
        { status: 'DRAFT', scheduledAt: null, updatedAt: '2026-10-20T10:00:00+08:00', docVersion: 3 },
      ]),
    )
    await admin.apply(1)
    admin.cancelApply()
    expect(await admin.confirmApply()).toBeNull()
    expect(api.applyVersion).toHaveBeenCalledTimes(1)
  })

  it('compare fetches both versions and diffs name and content', async () => {
    const admin = await setup()
    api.getVersion.mockImplementation(async (_k: string, v: number) =>
      v === 1 ? full(1, 'ARCHIVED', { name: '舊名', content: 'a\nb' }) : full(2, 'PUBLISHED', { name: '新名', content: 'a\nc' }),
    )

    const result = await admin.compare(1, 2)

    expect(api.getVersion).toHaveBeenCalledWith(KEY, 1)
    expect(api.getVersion).toHaveBeenCalledWith(KEY, 2)
    expect(result.nameDiff).toEqual([
      { kind: 'removed', text: '舊名' },
      { kind: 'added', text: '新名' },
    ])
    expect(result.contentDiff).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'removed', text: 'b' },
      { kind: 'added', text: 'c' },
    ])
  })

  it('validation 422 exposes details as errors', async () => {
    const admin = await setup(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00' }))
    const details = [
      { rule: 'REQUIRED_SECTION', message: '缺少必要章節「## 回答套版」' },
      { rule: 'FORBIDDEN_CONTENT', message: '第 12 行疑似包含身分證號，請移除' },
    ]
    api.saveDraft.mockRejectedValue(apiError(422, 'AI_CONFIG_VALIDATION_FAILED', details))

    await admin.save('KEEP_STATUS')

    expect(admin.state.errors).toEqual(details)
    expect(admin.state.validation).toBe('failed')
  })

  it('a successful save of a scheduled version replaces earlier 422 errors with a pass', async () => {
    const admin = await setup(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00' }))
    api.saveDraft.mockRejectedValueOnce(apiError(422, 'AI_CONFIG_VALIDATION_FAILED', [{ rule: 'NOT_EMPTY', message: '內容不可為空' }]))
    await admin.save('KEEP_STATUS')
    expect(admin.state.validation).toBe('failed')

    api.saveDraft.mockResolvedValueOnce(full(2, 'SCHEDULED', { scheduledAt: '2026-10-24T09:00:00+08:00', docVersion: 4 }))
    await admin.save('KEEP_STATUS')
    expect(admin.state.validation).toBe('passed')
    expect(admin.state.errors).toEqual([])
  })

  it('validate saves unsaved edits first, then reports a pass', async () => {
    const admin = await setup()
    admin.state.form.content = '## 適用問題\n新內容'
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { content: '## 適用問題\n新內容', docVersion: 4 }))
    api.validateDraft.mockResolvedValue({ valid: true, errors: [] })

    await admin.validate()

    expect(api.saveDraft.mock.invocationCallOrder[0]).toBeLessThan(api.validateDraft.mock.invocationCallOrder[0]!)
    expect(admin.state.validation).toBe('passed')
    expect(admin.state.checkedContent).toBe('## 適用問題\n新內容')
  })

  it('validate without edits does not save', async () => {
    const admin = await setup()
    api.validateDraft.mockResolvedValue({ valid: false, errors: [{ rule: 'NOT_EMPTY', message: '內容不可為空' }] })
    await admin.validate()
    expect(api.saveDraft).not.toHaveBeenCalled()
    expect(admin.state.validation).toBe('failed')
    expect(admin.state.errors).toHaveLength(1)
  })

  it('conflict 409 offers reload', async () => {
    const admin = await setup()
    admin.state.form.content = '我未儲存的內容'
    api.saveDraft.mockRejectedValue(apiError(409, 'AI_CONFIG_CONFLICT'))

    await admin.save('KEEP_STATUS')

    expect(admin.state.conflict).toBe(true)
    expect(admin.state.form.content).toBe('我未儲存的內容') // kept until the user reloads

    serve(full(2, 'DRAFT', { content: '別人的內容', docVersion: 9 }))
    await admin.reload()

    expect(api.listConfigs).toHaveBeenCalled()
    expect(admin.state.conflict).toBe(false)
    expect(admin.state.form.content).toBe('別人的內容')
    expect(admin.state.workingCopy?.docVersion).toBe(9)
  })

  it('discard sends the docVersion and reloads', async () => {
    const admin = await setup()
    api.discardDraft.mockResolvedValue(undefined)
    serve(null)
    await admin.discard()
    expect(api.discardDraft).toHaveBeenCalledWith(KEY, 3)
    expect(admin.state.workingCopy).toBeNull()
  })

  it('ignores a second write while one is in flight', async () => {
    const admin = await setup()
    let resolve!: (v: ConfigVersion) => void
    api.saveDraft.mockReturnValue(new Promise<ConfigVersion>((r) => (resolve = r)))

    const first = admin.save('KEEP_STATUS')
    expect(admin.state.busy).toBe(true)
    await admin.save('KEEP_STATUS')
    await admin.publish('IMMEDIATE')
    resolve(full(2, 'DRAFT', { docVersion: 4 }))
    await first

    expect(api.saveDraft).toHaveBeenCalledTimes(1)
    expect(api.publish).not.toHaveBeenCalled()
    expect(admin.state.busy).toBe(false)
  })

  it('ignores a late response for a previously selected key', async () => {
    serve(full(2, 'DRAFT'))
    const key = ref(KEY)
    let release!: () => void
    const gate = new Promise<void>((r) => (release = r))
    api.listVersions.mockImplementationOnce(async () => {
      await gate
      return [item(5, 'DRAFT')]
    })
    const admin = usePromptAdmin(key)
    key.value = 'system.main'
    await flushPromises()
    release()
    await flushPromises()

    expect(api.listVersions).toHaveBeenLastCalledWith('system.main')
    expect(admin.state.versions.map((v) => v.version)).toEqual([2, 1])
  })

  it('never publishes to another key when the config is switched while the save is pending', async () => {
    const keyA = KEY
    const keyB = 'instruction.customer-overview'
    const wcA = full(2, 'DRAFT', { docVersion: 3 })
    const wcB = full(6, 'DRAFT', { configKey: keyB, name: '顧客概況', content: 'B 的內容', docVersion: 11 })
    api.listVersions.mockImplementation(async (k: string) => [item(k === keyA ? 2 : 6, 'DRAFT')])
    api.getVersion.mockImplementation(async (k: string) => (k === keyA ? wcA : wcB))
    const key = ref(keyA)
    const admin = usePromptAdmin(key)
    await flushPromises()

    let resolveSave!: (v: ConfigVersion) => void
    api.saveDraft.mockReturnValue(new Promise<ConfigVersion>((r) => (resolveSave = r)))
    const publishing = admin.publish('IMMEDIATE')
    expect(api.saveDraft).toHaveBeenCalledWith(keyA, expect.objectContaining({ docVersion: 3 }))

    key.value = keyB
    await flushPromises()
    expect(admin.state.workingCopy?.configKey).toBe(keyB)

    resolveSave(full(2, 'DRAFT', { docVersion: 4, content: 'A 已儲存' }))
    await publishing
    await flushPromises()

    expect(api.publish).not.toHaveBeenCalled()
    expect(admin.state.workingCopy).toEqual(wcB) // B untouched
    expect(admin.state.form.content).toBe('B 的內容')
    expect(admin.state.validation).toBe('idle')
    expect(api.listVersions).not.toHaveBeenCalledWith(keyA, expect.anything())
    expect(api.listVersions).toHaveBeenLastCalledWith(keyB)
  })

  it('drops a conflict from the previous key after a switch', async () => {
    serve(full(2, 'DRAFT'))
    const keyRef = ref(KEY)
    const a = usePromptAdmin(keyRef)
    await flushPromises()
    let rejectSave!: (e: unknown) => void
    api.saveDraft.mockReturnValue(new Promise((_, r) => (rejectSave = r)))
    const saving = a.save('KEEP_STATUS')
    keyRef.value = 'system.main'
    await flushPromises()
    rejectSave(apiError(409, 'AI_CONFIG_CONFLICT'))
    await saving
    expect(a.state.conflict).toBe(false)
  })

  it('toasts a generic message for an unexpected (non-API) error in a write', async () => {
    const admin = await setup()
    api.discardDraft.mockRejectedValue(new TypeError('boom'))
    await admin.discard()
    expect(toast).toHaveBeenCalledWith('系統發生錯誤，請稍後再試')
  })

  it('refreshes the history once after publish, not inside the save step', async () => {
    const admin = await setup()
    api.saveDraft.mockResolvedValue(full(2, 'DRAFT', { docVersion: 7 }))
    api.publish.mockResolvedValue(full(2, 'PUBLISHED'))
    api.listVersions.mockClear()
    await admin.publish('IMMEDIATE')
    expect(api.listVersions).toHaveBeenCalledTimes(1)
  })

  it('creates a new instruction with the instruction. prefix', async () => {
    const admin = await setup()
    api.createConfig.mockResolvedValue(full(1, 'DRAFT', { configKey: 'instruction.phone-opening' }))
    const created = await admin.createInstruction('phone-opening', '電話開場白')
    expect(api.createConfig).toHaveBeenCalledWith({
      configKey: 'instruction.phone-opening',
      configType: 'SYSTEM_INSTRUCTION',
      name: '電話開場白',
    })
    expect(created?.configKey).toBe('instruction.phone-opening')
  })
})
