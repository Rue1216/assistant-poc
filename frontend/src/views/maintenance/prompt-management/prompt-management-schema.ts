import { z } from 'zod'
import { isTaipeiDateTime } from '@/utils/taipei-time'
import { SCHEDULE_ERROR } from '@/views/maintenance/prompt-management/prompt-management-constant'

export const KEY_SUFFIX_ERROR = 'Key 只能使用小寫英數與連字號，1–50 字'
export const NAME_ERROR = '請輸入名稱（最多 50 字）'
export const NAME_CHARACTERS_ERROR = '名稱不可包含換行或控制字元'
export const CHANGE_NOTE_ERROR = '修改說明最多 200 字'

/** The part after "instruction." of 02 section 3 configKey `^(system|instruction)\.[a-z0-9-]{1,50}$`. */
const KEY_SUFFIX_PATTERN = /^[a-z0-9-]{1,50}$/
/** Backend ConfigKeys.NAME_PATTERN: no control characters or line separators. */
const NAME_PATTERN = /^[^\p{Cc}\u2028\u2029]*$/u

const nameSchema = z
  .string()
  .trim()
  .min(1, NAME_ERROR)
  .max(50, NAME_ERROR)
  .regex(NAME_PATTERN, NAME_CHARACTERS_ERROR)

/** 新增問題分類 dialog (02 section 3.2); values come out trimmed. */
export const newInstructionSchema = z.object({
  keySuffix: z.string().trim().regex(KEY_SUFFIX_PATTERN, KEY_SUFFIX_ERROR),
  name: nameSchema,
})

/** Name and change note of the working copy (backend SaveDraftRequest). The content is checked by the server. */
export const draftFormSchema = z.object({
  name: nameSchema,
  changeNote: z.string().max(200, CHANGE_NOTE_ERROR),
})

/** Date and time fields of a scheduled publish; the 1 minute to 90 days range is checked by the server. */
export const scheduleSchema = z
  .object({ date: z.string(), time: z.string() })
  .refine((value) => isTaipeiDateTime(value.date, value.time), { message: SCHEDULE_ERROR, path: ['date'] })
