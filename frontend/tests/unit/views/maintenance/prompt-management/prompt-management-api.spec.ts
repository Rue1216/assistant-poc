import { beforeEach, describe, expect, it, vi } from 'vitest'

const http = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock('@/utils/api-client', () => ({ apiClient: http }))

import { promptManagementApi } from '@/views/maintenance/prompt-management/prompt-management-api'

const BASE = '/ai-assistant/admin/configs/instruction.contact-prep'

describe('promptManagementApi admin functions', () => {
  beforeEach(() => {
    for (const fn of Object.values(http)) fn.mockReset().mockResolvedValue({ data: {} })
  })

  it('reads configs, versions and one version', async () => {
    await promptManagementApi.listConfigs()
    await promptManagementApi.listVersions('instruction.contact-prep')
    await promptManagementApi.getVersion('instruction.contact-prep', 3)
    expect(http.get.mock.calls.map((c) => c[0])).toEqual([
      '/ai-assistant/admin/configs',
      `${BASE}/versions`,
      `${BASE}/versions/3`,
    ])
  })

  it('reads feedback statistics silently', async () => {
    http.get.mockResolvedValue({ data: [{ version: 3, up: 12, down: 3 }] })
    const stats = await promptManagementApi.feedbackStats('instruction.contact-prep')
    expect(stats).toEqual([{ version: 3, up: 12, down: 3 }])
    expect(http.get).toHaveBeenCalledWith(`${BASE}/feedback-stats`, {
      meta: expect.objectContaining({ silentCodes: expect.any(Array) }),
    })
  })

  it('keeps conflict and validation failures silent on save', async () => {
    const body = { name: 'n', content: 'c', changeNote: null, saveMode: 'KEEP_STATUS' as const, docVersion: 4 }
    await promptManagementApi.saveDraft('instruction.contact-prep', body)
    expect(http.put).toHaveBeenCalledWith(`${BASE}/draft`, body, {
      meta: { silentCodes: ['AI_CONFIG_CONFLICT', 'AI_CONFIG_VALIDATION_FAILED'] },
    })
  })

  it('keeps conflict, validation and input errors silent on publish', async () => {
    const body = { publishMode: 'IMMEDIATE' as const, docVersion: 5 }
    await promptManagementApi.publish('instruction.contact-prep', body)
    expect(http.post).toHaveBeenCalledWith(`${BASE}/publish`, body, {
      meta: { silentCodes: ['AI_CONFIG_CONFLICT', 'AI_CONFIG_VALIDATION_FAILED', 'AI_INPUT_INVALID'] },
    })
  })

  it('keeps overwrite-pending and conflict silent on apply', async () => {
    await promptManagementApi.applyVersion('instruction.contact-prep', 2, { workingCopyDocVersion: null })
    expect(http.post).toHaveBeenCalledWith(`${BASE}/versions/2/apply`, { workingCopyDocVersion: null }, {
      meta: { silentCodes: ['AI_CONFIG_CONFLICT', 'AI_CONFIG_OVERWRITE_PENDING'] },
    })
  })

  it('discards with docVersion as a query parameter', async () => {
    await promptManagementApi.discardDraft('instruction.contact-prep', 7)
    expect(http.delete).toHaveBeenCalledWith(`${BASE}/draft`, {
      params: { docVersion: 7 },
      meta: { silentCodes: ['AI_CONFIG_CONFLICT'] },
    })
  })

  it('creates configs and drafts, and validates the draft', async () => {
    const body = { configKey: 'instruction.new-one', configType: 'SYSTEM_INSTRUCTION' as const, name: '新分類' }
    await promptManagementApi.createConfig(body)
    await promptManagementApi.createDraft('instruction.contact-prep')
    await promptManagementApi.validateDraft('instruction.contact-prep')
    expect(http.post.mock.calls[0]).toEqual(['/ai-assistant/admin/configs', body])
    expect(http.post.mock.calls[1]![0]).toBe(`${BASE}/draft`)
    expect(http.post.mock.calls[2]![0]).toBe(`${BASE}/draft/validate`)
  })

  it('keeps a validate conflict silent so only the conflict banner shows', async () => {
    await promptManagementApi.validateDraft('instruction.contact-prep')
    expect(http.post.mock.calls[0]![2]).toEqual({ meta: { silentCodes: ['AI_CONFIG_CONFLICT'] } })
  })
})

describe('promptManagementApi evaluation functions', () => {
  const ADMIN = '/ai-assistant/admin'
  beforeEach(() => {
    for (const fn of Object.values(http)) fn.mockReset().mockResolvedValue({ data: {} })
  })

  it('starts an evaluation with AI_EVAL_RUNNING silent', async () => {
    http.post.mockResolvedValue({ data: { runId: 'r1', status: 'RUNNING', totalCases: 16 } })
    const res = await promptManagementApi.startEvaluation('instruction.contact-prep', 5)
    expect(res.runId).toBe('r1')
    expect(http.post).toHaveBeenCalledWith(`${BASE}/evaluations`, { docVersion: 5 }, {
      meta: { silentCodes: ['AI_EVAL_RUNNING'] },
    })
  })

  it('reads the history, one run and the dataset', async () => {
    await promptManagementApi.listEvaluations('instruction.contact-prep')
    await promptManagementApi.getEvaluation('abc')
    await promptManagementApi.evaluationCases()
    expect(http.get.mock.calls.map((c) => c[0])).toEqual([
      `${BASE}/evaluations`,
      `${ADMIN}/evaluations/abc`,
      `${ADMIN}/evaluation-cases`,
    ])
  })

  it('maps an empty current evaluation (204) to null', async () => {
    http.get.mockResolvedValueOnce({ status: 204, data: '' })
    expect(await promptManagementApi.currentEvaluation()).toBeNull()
    http.get.mockResolvedValueOnce({ status: 200, data: { runId: 'r1' } })
    expect(await promptManagementApi.currentEvaluation()).toEqual({ runId: 'r1' })
    expect(http.get).toHaveBeenCalledWith(`${ADMIN}/evaluations/current`)
  })
})
