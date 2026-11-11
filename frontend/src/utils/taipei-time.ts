/**
 * Asia/Taipei time helpers on dayjs. Taipei is UTC+8 with no daylight saving, so every value is read at a
 * fixed +08:00 offset and the browser's time zone never matters.
 */
import dayjs, { type Dayjs } from 'dayjs'
import customParseFormat from 'dayjs/plugin/customParseFormat'
import utc from 'dayjs/plugin/utc'

dayjs.extend(utc)
dayjs.extend(customParseFormat)

const TAIPEI_OFFSET_MINUTES = 8 * 60
const HOUR_MS = 60 * 60 * 1000
const DATE_FORMAT = 'YYYY-MM-DD'
const TIME_FORMAT = 'HH:mm'

/** The instant as Taipei wall clock; null when `iso` is not a valid date. */
function toTaipei(iso: string | number): Dayjs | null {
  const parsed = dayjs(iso)
  return parsed.isValid() ? parsed.utcOffset(TAIPEI_OFFSET_MINUTES) : null
}

function formatOrInput(iso: string, template: string): string {
  return toTaipei(iso)?.format(template) ?? iso
}

/** Whether `date` (`YYYY-MM-DD`) and `time` (`HH:mm`) are a real calendar date and time. */
export function isTaipeiDateTime(date: string, time: string): boolean {
  return dayjs(`${date} ${time}`, `${DATE_FORMAT} ${TIME_FORMAT}`, true).isValid()
}

/** `'2026-10-24'`, `'09:00'` (Taipei wall time) -> `'2026-10-24T09:00:00+08:00'`. Throws on malformed input. */
export function taipeiDateTimeToIso(date: string, time: string): string {
  if (!isTaipeiDateTime(date, time)) {
    throw new Error(`invalid Taipei date/time: ${date} ${time}`)
  }
  return `${date}T${time}:00+08:00`
}

/** Any ISO 8601 instant -> Taipei wall time as `{ date: 'YYYY-MM-DD', time: 'HH:mm' }` (input values of date/time fields). */
export function isoToTaipeiDateTime(iso: string): { date: string; time: string } {
  const taipei = toTaipei(iso)
  if (!taipei) throw new Error(`invalid ISO date: ${iso}`)
  return { date: taipei.format(DATE_FORMAT), time: taipei.format(TIME_FORMAT) }
}

/** ISO 8601 -> "2026/10/24 09:00" in Asia/Taipei. Returns the input when it is not a valid date. */
export function formatTaipei(iso: string): string {
  return formatOrInput(iso, 'YYYY/MM/DD HH:mm')
}

/** ISO 8601 -> "10/24 09:00" in Asia/Taipei (compact lists and labels). */
export function formatTaipeiShort(iso: string): string {
  return formatOrInput(iso, 'MM/DD HH:mm')
}

/** ISO 8601 -> "10/24 09:00:07" in Asia/Taipei (tells apart entries that share a minute). */
export function formatTaipeiShortSeconds(iso: string): string {
  return formatOrInput(iso, 'MM/DD HH:mm:ss')
}

/** ISO 8601 -> "14:03" in Asia/Taipei. */
export function formatTaipeiTime(iso: string): string {
  return formatOrInput(iso, TIME_FORMAT)
}

/** Whether the instant falls on today's calendar day in Asia/Taipei. */
export function isTaipeiToday(iso: string, nowMs: number = Date.now()): boolean {
  const taipei = toTaipei(iso)
  const today = toTaipei(nowMs)
  return taipei !== null && today !== null && taipei.isSame(today, 'day')
}

/** The current instant as an ISO 8601 string (UTC). */
export function nowIso(): string {
  return dayjs().toISOString()
}

/** The next full hour at least one hour from `nowMs`, as Taipei date and time field values. */
export function nextHourTaipei(nowMs: number = Date.now()): { date: string; time: string } {
  const nextHourMs = Math.ceil(nowMs / HOUR_MS) * HOUR_MS + HOUR_MS
  const taipei = dayjs(nextHourMs).utcOffset(TAIPEI_OFFSET_MINUTES)
  return { date: taipei.format(DATE_FORMAT), time: taipei.format(TIME_FORMAT) }
}
