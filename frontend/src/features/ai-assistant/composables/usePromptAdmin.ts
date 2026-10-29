import { computed, reactive, watch, type Ref } from 'vue'
import { ApiError } from '../../../platform-mock/apiClient'
import { useToast } from '../../../platform-mock/useToast'
import { aiAssistantApi, CONFLICT, INPUT_INVALID, OVERWRITE_PENDING, VALIDATION_FAILED } from '../api/aiAssistantApi'
import { diffLines, type DiffLine } from '../markdown/lineDiff'
import { formatTaipei, taipeiDateTimeToIso } from '../taipeiTime'
import type {
  ConfigStatus,
  ConfigSummary,
  ConfigVersion,
  OverwritePendingDetail,
  PublishMode,
  SaveMode,
  ValidationIssue,
  VersionFeedback,
  VersionHistoryItem,
} from '../types'

export const SCHEDULE_ERROR = '預約時間需在 1 分鐘後、90 天內'
/** 05 section 2.1 AI_INTERNAL_ERROR wording, for unexpected client-side failures. */
export const GENERIC_ERROR = '系統發生錯誤，請稍後再試'

export interface PromptForm {
  name: string
  content: string
  changeNote: string
}

/** 'idle' until a check runs (automatic 3.8 check, or a 422 from save / publish); 'checking' while it is in flight. */
export type ValidationState = 'idle' | 'checking' | 'passed' | 'failed'

export type ApplyResult =
  | ({ needsConfirm: true } & OverwritePendingDetail)
  | { needsConfirm: false; draft: ConfigVersion }

const WORKING: ConfigStatus[] = ['DRAFT', 'SCHEDULED']

const noteOrNull = (note: string | null | undefined): string | null => note?.trim() || null

function toIssues(details: unknown[]): ValidationIssue[] {
  return details.flatMap((d) => {
    const o = d as Partial<ValidationIssue> | null
    return o && typeof o.rule === 'string' && typeof o.message === 'string' ? [{ rule: o.rule, message: o.message }] : []
  })
}

function isScheduleError(e: ApiError): boolean {
  return e.details.some((d) => (d as { field?: unknown } | null)?.field === 'scheduledAt')
}

/**
 * State and actions of the prompt management page for the selected config key (02 section 3).
 * Every write goes through `run`, so a second click while a request is in flight does nothing.
 */
export function usePromptAdmin(configKey: Ref<string>) {
  const toast = useToast()

  const state = reactive({
    configs: [] as ConfigSummary[],
    configsLoaded: false,
    loading: false,
    busy: false,
    workingCopy: null as ConfigVersion | null,
    published: null as ConfigVersion | null,
    versions: [] as VersionHistoryItem[],
    /** Thumbs counts by version; empty when the statistics could not be loaded. */
    feedback: {} as Record<number, VersionFeedback>,
    form: { name: '', content: '', changeNote: '' } as PromptForm,
    errors: [] as ValidationIssue[],
    validation: 'idle' as ValidationState,
    /** The content the last check ran on (sizes and counts of the passing list). */
    checkedContent: '',
    /** 409 AI_CONFIG_CONFLICT: the banner offers 重新載入; the form keeps the user's edits until then. */
    conflict: false,
    scheduleError: null as string | null,
    pendingApply: null as { key: string; sourceVersion: number; docVersion: number } | null,
  })

  const dirty = computed(() => {
    const wc = state.workingCopy
    if (!wc) return false
    return (
      state.form.name !== wc.name ||
      state.form.content !== wc.content ||
      noteOrNull(state.form.changeNote) !== noteOrNull(wc.changeNote)
    )
  })

  function resetForm(): void {
    const wc = state.workingCopy
    state.form.name = wc?.name ?? ''
    state.form.content = wc?.content ?? ''
    state.form.changeNote = wc?.changeNote ?? ''
  }

  function resetChecks(): void {
    checkSeq++ // a check started before this reset must not write its result
    state.errors = []
    state.validation = 'idle'
    state.checkedContent = ''
    state.conflict = false
    state.scheduleError = null
    state.pendingApply = null
  }

  async function loadConfigs(): Promise<void> {
    try {
      state.configs = await aiAssistantApi.listConfigs()
    } catch {
      // toast already shown by apiClient
    } finally {
      state.configsLoaded = true
    }
  }

  /** Statistics are optional: any failure gives an empty map and never touches the history. */
  async function fetchFeedback(key: string): Promise<Record<number, VersionFeedback>> {
    try {
      return Object.fromEntries((await aiAssistantApi.feedbackStats(key)).map((f) => [f.version, f]))
    } catch {
      return {}
    }
  }

  let loadSeq = 0
  let checkSeq = 0
  let loadedKey = ''

  function clearSelected(): void {
    state.versions = []
    state.feedback = {}
    state.workingCopy = null
    state.published = null
    resetForm()
  }

  /** Versions, working copy and published version of the selected key; replaces the form. */
  async function loadSelected(): Promise<void> {
    const seq = ++loadSeq
    const key = configKey.value
    resetChecks()
    // another key: never show the previous key's content while loading (a reload keeps it until replaced)
    if (key !== loadedKey) clearSelected()
    loadedKey = key
    if (!key) return
    state.loading = true
    try {
      const feedbackLoad = fetchFeedback(key)
      const versions = await aiAssistantApi.listVersions(key)
      const wcItem = versions.find((v) => WORKING.includes(v.status))
      const pubItem = versions.find((v) => v.status === 'PUBLISHED')
      const [wc, pub] = await Promise.all([
        wcItem ? aiAssistantApi.getVersion(key, wcItem.version) : null,
        pubItem ? aiAssistantApi.getVersion(key, pubItem.version) : null,
      ])
      const feedback = await feedbackLoad
      if (seq !== loadSeq) return
      state.versions = [...versions].sort((a, b) => b.version - a.version)
      state.feedback = feedback
      state.workingCopy = wc
      state.published = pub
      resetForm()
      if (wc) void check(key)
    } catch {
      // toast already shown by apiClient
    } finally {
      if (seq === loadSeq) state.loading = false
    }
  }

  async function reload(): Promise<void> {
    await Promise.all([loadConfigs(), loadSelected()])
  }

  /** Still on the key a write flow started with? Results for another key are dropped. */
  const onKey = (key: string): boolean => key === configKey.value

  /** After a save: the history rows and list badges changed, the form did not. */
  async function refreshLists(key: string): Promise<void> {
    if (!onKey(key)) return
    try {
      const [versions, feedback] = await Promise.all([
        aiAssistantApi.listVersions(key),
        fetchFeedback(key),
        loadConfigs(),
      ])
      if (onKey(key)) {
        state.versions = [...versions].sort((a, b) => b.version - a.version)
        state.feedback = feedback
      }
    } catch {
      // toast already shown by apiClient
    }
  }

  async function reloadIfOn(key: string): Promise<void> {
    if (onKey(key)) await reload()
  }

  async function run<T>(action: () => Promise<T>): Promise<T | null> {
    if (state.busy) return null
    state.busy = true
    try {
      return await action()
    } finally {
      state.busy = false
    }
  }

  /**
   * Conflict and validation failures are silent API codes shown in place; other API errors were
   * toasted by apiClient; anything else gets the generic message. Errors for a key no longer shown are dropped.
   */
  function handleWriteError(e: unknown, key: string): void {
    if (!onKey(key)) return
    if (!(e instanceof ApiError)) {
      toast.show(GENERIC_ERROR)
      return
    }
    if (e.code === CONFLICT) {
      state.conflict = true
    } else if (e.code === VALIDATION_FAILED) {
      checkSeq++ // an in-flight check is older than this result
      state.errors = toIssues(e.details)
      state.validation = 'failed'
      state.checkedContent = state.form.content
    }
  }

  /**
   * Automatic pre-publish check (3.8) of the server's working copy. Not part of run(): it never sets busy,
   * and a result that arrives after a newer check, a reset or a key change is dropped.
   */
  async function check(key: string): Promise<void> {
    if (!state.workingCopy || !onKey(key)) return
    const seq = ++checkSeq
    state.validation = 'checking'
    try {
      const report = await aiAssistantApi.validateDraft(key)
      if (seq !== checkSeq || !onKey(key)) return
      state.errors = report.errors
      state.validation = report.valid ? 'passed' : 'failed'
      state.checkedContent = state.workingCopy?.content ?? ''
    } catch (e) {
      if (seq !== checkSeq) return
      handleWriteError(e, key)
      if (state.validation === 'checking') state.validation = 'idle'
    }
  }

  /**
   * PUT draft for `key`. `refresh: false` when the save is a step of publish, which
   * refresh once at the end. Returns null on failure or when the user moved to another key meanwhile.
   */
  async function doSave(
    key: string,
    mode: SaveMode,
    { announce, refresh }: { announce: boolean; refresh: boolean },
  ): Promise<ConfigVersion | null> {
    const wc = state.workingCopy
    if (!wc || wc.configKey !== key) return null
    const wasScheduled = wc.status === 'SCHEDULED'
    try {
      const saved = await aiAssistantApi.saveDraft(key, {
        name: state.form.name,
        content: state.form.content,
        changeNote: noteOrNull(state.form.changeNote),
        saveMode: mode,
        docVersion: wc.docVersion,
      })
      if (!onKey(key)) return null
      state.workingCopy = saved
      state.conflict = false
      if (announce) {
        toast.show(mode === 'DRAFT' && wasScheduled ? '已存成草稿，排程已取消' : '已儲存', 'info')
      }
      if (refresh) {
        void check(key)
        await refreshLists(key)
      }
      return saved
    } catch (e) {
      handleWriteError(e, key)
      return null
    }
  }

  function save(mode: SaveMode): Promise<ConfigVersion | null> {
    return run(() => doSave(configKey.value, mode, { announce: true, refresh: true }))
  }

  /**
   * Saves (KEEP_STATUS) first, then publishes the same key with the docVersion the save returned
   * (02 section 3.0). Nothing is published if the user moved to another key during the save.
   */
  function publish(mode: PublishMode, date?: string, time?: string): Promise<ConfigVersion | null> {
    return run(async () => {
      const key = configKey.value
      state.scheduleError = null
      let scheduledAt: string | undefined
      if (mode === 'SCHEDULED') {
        try {
          scheduledAt = taipeiDateTimeToIso(date ?? '', time ?? '')
        } catch {
          state.scheduleError = SCHEDULE_ERROR
          return null
        }
      }
      const saved = await doSave(key, 'KEEP_STATUS', { announce: false, refresh: false })
      if (!saved || !onKey(key)) return null
      try {
        const result = await aiAssistantApi.publish(key, {
          publishMode: mode,
          ...(scheduledAt ? { scheduledAt } : {}),
          docVersion: saved.docVersion,
        })
        // the server published it, so say so even if the user has moved to another config
        toast.show(
          mode === 'IMMEDIATE' ? '已發布，立即生效' : `已預約於 ${formatTaipei(result.scheduledAt ?? scheduledAt!)} 發布`,
          'info',
        )
        if (!onKey(key)) return null
        await reload()
        return result
      } catch (e) {
        if (!onKey(key)) return null
        const invalid = e instanceof ApiError && e.code === VALIDATION_FAILED
        if (e instanceof ApiError && e.code === INPUT_INVALID) {
          if (isScheduleError(e)) state.scheduleError = SCHEDULE_ERROR
          else toast.show(e.message)
        } else {
          handleWriteError(e, key)
        }
        // the save step did change the version and the content: re-check it (a 422 already carries its own result)
        if (!invalid) void check(key)
        await refreshLists(key)
        return null
      }
    })
  }

  function discard(): Promise<void> {
    return run(async () => {
      const key = configKey.value
      const wc = state.workingCopy
      if (!wc || wc.configKey !== key) return
      try {
        await aiAssistantApi.discardDraft(key, wc.docVersion)
        toast.show('已捨棄草稿，內容保留在版本歷程中', 'info')
        await reloadIfOn(key)
      } catch (e) {
        handleWriteError(e, key)
      }
    }).then(() => undefined)
  }

  async function afterApply(key: string): Promise<void> {
    toast.show('已套用為新草稿，發布後才會生效', 'info')
    await reloadIfOn(key)
  }

  /**
   * 3.10. The first request never confirms; with a working copy the server answers
   * AI_CONFIG_OVERWRITE_PENDING and the caller asks the user, then calls confirmApply().
   */
  function apply(sourceVersion: number): Promise<ApplyResult | null> {
    return run(async (): Promise<ApplyResult | null> => {
      const key = configKey.value
      state.pendingApply = null
      try {
        const draft = await aiAssistantApi.applyVersion(key, sourceVersion, { workingCopyDocVersion: null })
        await afterApply(key)
        return onKey(key) ? { needsConfirm: false, draft } : null
      } catch (e) {
        if (!onKey(key)) return null
        if (e instanceof ApiError && e.code === OVERWRITE_PENDING) {
          const d = e.details[0] as OverwritePendingDetail
          state.pendingApply = { key, sourceVersion, docVersion: d.docVersion }
          return { needsConfirm: true, status: d.status, scheduledAt: d.scheduledAt, updatedAt: d.updatedAt, docVersion: d.docVersion }
        }
        handleWriteError(e, key)
        return null
      }
    })
  }

  function confirmApply(): Promise<ConfigVersion | null> {
    return run(async () => {
      const pending = state.pendingApply
      state.pendingApply = null
      if (!pending || !onKey(pending.key)) return null
      const key = pending.key
      try {
        const draft = await aiAssistantApi.applyVersion(key, pending.sourceVersion, {
          workingCopyDocVersion: pending.docVersion,
          confirmOverwrite: true,
        })
        await afterApply(key)
        return onKey(key) ? draft : null
      } catch (e) {
        handleWriteError(e, key)
        return null
      }
    })
  }

  function cancelApply(): void {
    state.pendingApply = null
  }

  /** 3.5: a draft based on the published version. */
  function createDraft(): Promise<ConfigVersion | null> {
    return run(async () => {
      const key = configKey.value
      try {
        const draft = await aiAssistantApi.createDraft(key)
        await reloadIfOn(key)
        return onKey(key) ? draft : null
      } catch (e) {
        handleWriteError(e, key)
        return null
      }
    })
  }

  /** 3.2 for a new question category; 409 AI_CONFIG_INVALID_STATE (key exists) was toasted. */
  function createInstruction(keySuffix: string, name: string): Promise<ConfigVersion | null> {
    return run(async () => {
      try {
        const created = await aiAssistantApi.createConfig({
          configKey: `instruction.${keySuffix}`,
          configType: 'SYSTEM_INSTRUCTION',
          name,
        })
        await loadConfigs()
        return created
      } catch (e) {
        if (!(e instanceof ApiError)) toast.show(GENERIC_ERROR)
        return null
      }
    })
  }

  function loadVersion(version: number, key: string = configKey.value): Promise<ConfigVersion> {
    return aiAssistantApi.getVersion(key, version)
  }

  /**
   * Line diff of `name` and `content` from version `a` (before) to `b` (after); computed in the browser.
   * Throws DiffTooLargeError when the changed parts are too long to compare.
   */
  async function compare(a: number, b: number): Promise<{ nameDiff: DiffLine[]; contentDiff: DiffLine[] }> {
    const key = configKey.value
    const [before, after] = await Promise.all([loadVersion(a, key), loadVersion(b, key)])
    return { nameDiff: diffLines(before.name, after.name), contentDiff: diffLines(before.content, after.content) }
  }

  watch(configKey, () => void loadSelected(), { immediate: true })

  return {
    state,
    dirty,
    loadConfigs,
    reload,
    save,
    publish,
    discard,
    apply,
    confirmApply,
    cancelApply,
    createDraft,
    createInstruction,
    loadVersion,
    compare,
  }
}

export type PromptAdmin = ReturnType<typeof usePromptAdmin>
