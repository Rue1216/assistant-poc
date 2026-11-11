/**
 * Asia/Taipei time helpers. Taipei is UTC+8 with no daylight saving, so every conversion is plain
 * UTC arithmetic: no local-time Date API is used and the browser's time zone never matters.
 */

const TAIPEI_OFFSET_MS = 8 * 60 * 60 * 1000
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/
const TIME_RE = /^\d{2}:\d{2}$/

const pad = (n: number): string => String(n).padStart(2, '0')

interface TaipeiParts {
  year: string
  month: string
  day: string
  hour: string
  minute: string
  second: string
}

function taipeiParts(iso: string): TaipeiParts | null {
  const ms = Date.parse(iso)
  if (Number.isNaN(ms)) return null
  const d = new Date(ms + TAIPEI_OFFSET_MS) // read back with getUTC*: Taipei wall clock
  return {
    year: String(d.getUTCFullYear()),
    month: pad(d.getUTCMonth() + 1),
    day: pad(d.getUTCDate()),
    hour: pad(d.getUTCHours()),
    minute: pad(d.getUTCMinutes()),
    second: pad(d.getUTCSeconds()),
  }
}

/** `'2026-10-24'`, `'09:00'` (Taipei wall time) -> `'2026-10-24T09:00:00+08:00'`. Throws on malformed input. */
export function taipeiDateTimeToIso(date: string, time: string): string {
  if (!DATE_RE.test(date) || !TIME_RE.test(time)) {
    throw new Error(`invalid Taipei date/time: ${date} ${time}`)
  }
  return `${date}T${time}:00+08:00`
}

/** Any ISO 8601 instant -> Taipei wall time as `{ date: 'YYYY-MM-DD', time: 'HH:mm' }` (input values of date/time fields). */
export function isoToTaipeiDateTime(iso: string): { date: string; time: string } {
  const p = taipeiParts(iso)
  if (!p) throw new Error(`invalid ISO date: ${iso}`)
  return { date: `${p.year}-${p.month}-${p.day}`, time: `${p.hour}:${p.minute}` }
}

/** ISO 8601 -> "2026/10/24 09:00" in Asia/Taipei. Returns the input when it is not a valid date. */
export function formatTaipei(iso: string): string {
  const p = taipeiParts(iso)
  return p ? `${p.year}/${p.month}/${p.day} ${p.hour}:${p.minute}` : iso
}

/** ISO 8601 -> "10/24 09:00" in Asia/Taipei (compact lists and labels). */
export function formatTaipeiShort(iso: string): string {
  const p = taipeiParts(iso)
  return p ? `${p.month}/${p.day} ${p.hour}:${p.minute}` : iso
}

/** ISO 8601 -> "10/24 09:00:07" in Asia/Taipei (tells apart entries that share a minute). */
export function formatTaipeiShortSeconds(iso: string): string {
  const p = taipeiParts(iso)
  return p ? `${p.month}/${p.day} ${p.hour}:${p.minute}:${p.second}` : iso
}

/** ISO 8601 -> "14:03" in Asia/Taipei. */
export function formatTaipeiTime(iso: string): string {
  const p = taipeiParts(iso)
  return p ? `${p.hour}:${p.minute}` : iso
}

/** The next full hour at least one hour from `nowMs`, as Taipei date and time field values. */
export function nextHourTaipei(nowMs: number = Date.now()): { date: string; time: string } {
  const hour = 60 * 60 * 1000
  const next = Math.ceil(nowMs / hour) * hour + hour
  return isoToTaipeiDateTime(new Date(next).toISOString())
}
