import { describe, expect, it } from 'vitest'
import {
  CHANGE_NOTE_ERROR,
  KEY_SUFFIX_ERROR,
  NAME_CHARACTERS_ERROR,
  NAME_ERROR,
  draftFormSchema,
  newInstructionSchema,
  scheduleSchema,
} from '@/views/maintenance/prompt-management/prompt-management-schema'
import { SCHEDULE_ERROR } from '@/views/maintenance/prompt-management/prompt-management-constant'

function messages(result: { success: boolean; error?: { issues: { message: string }[] } }): string[] {
  return result.success ? [] : result.error!.issues.map((issue) => issue.message)
}

describe('newInstructionSchema', () => {
  it('trims and accepts a valid key suffix and name', () => {
    expect(newInstructionSchema.parse({ keySuffix: ' phone-opening ', name: ' 電話開場白 ' })).toEqual({
      keySuffix: 'phone-opening',
      name: '電話開場白',
    })
  })

  it.each(['Phone', 'phone opening', 'phone_opening', '', 'a'.repeat(51)])('rejects the key suffix %j', (keySuffix) => {
    expect(messages(newInstructionSchema.safeParse({ keySuffix, name: '名稱' }))).toEqual([KEY_SUFFIX_ERROR])
  })

  it('rejects a blank or over-long name', () => {
    expect(messages(newInstructionSchema.safeParse({ keySuffix: 'a', name: '   ' }))).toEqual([NAME_ERROR])
    expect(messages(newInstructionSchema.safeParse({ keySuffix: 'a', name: '名'.repeat(51) }))).toEqual([NAME_ERROR])
    expect(newInstructionSchema.safeParse({ keySuffix: 'a', name: '名'.repeat(50) }).success).toBe(true)
  })

  it('rejects control characters and line separators in the name, like the backend NAME_PATTERN', () => {
    for (const name of ['甲\n乙', '甲\t乙', `甲${String.fromCharCode(0x2028)}乙`]) {
      expect(messages(newInstructionSchema.safeParse({ keySuffix: 'a', name }))).toEqual([NAME_CHARACTERS_ERROR])
    }
  })
})

describe('draftFormSchema', () => {
  it('accepts an empty change note and limits it to 200 characters', () => {
    expect(draftFormSchema.safeParse({ name: '聯繫準備', changeNote: '' }).success).toBe(true)
    expect(messages(draftFormSchema.safeParse({ name: '聯繫準備', changeNote: '字'.repeat(201) }))).toEqual([
      CHANGE_NOTE_ERROR,
    ])
  })

  it('requires a name', () => {
    expect(messages(draftFormSchema.safeParse({ name: '', changeNote: '' }))).toEqual([NAME_ERROR])
  })
})

describe('scheduleSchema', () => {
  it('accepts a real date and time', () => {
    expect(scheduleSchema.safeParse({ date: '2026-11-01', time: '10:30' }).success).toBe(true)
  })

  it.each([
    ['', ''],
    ['2026-11-01', ''],
    ['2026-02-30', '10:00'],
    ['2026-11-01', '24:00'],
  ])('rejects %j %j with the schedule message on the date field', (date, time) => {
    const result = scheduleSchema.safeParse({ date, time })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0]).toMatchObject({ message: SCHEDULE_ERROR, path: ['date'] })
  })
})
