import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import {
  formatTaipei,
  formatTaipeiShort,
  formatTaipeiShortSeconds,
  formatTaipeiTime,
  isTaipeiDateTime,
  isTaipeiToday,
  isoToTaipeiDateTime,
  nextHourTaipei,
  nowIso,
  taipeiDateTimeToIso,
} from '@/utils/taipei-time'

// A browser far from Taipei: none of the conversions may depend on the local zone.
beforeAll(() => {
  vi.stubEnv('TZ', 'America/New_York') // sets process.env.TZ
})
afterAll(() => {
  vi.unstubAllEnvs()
})

describe('taipeiTime', () => {
  it('runs with a non-Taipei local time zone', () => {
    // New York is UTC-4 on this date; proves the TZ override is active for the tests below
    expect(new Date('2026-10-24T00:00:00Z').getTimezoneOffset()).toBe(240)
  })

  it('converts Taipei date and time to ISO with +08:00', () => {
    expect(taipeiDateTimeToIso('2026-10-24', '09:00')).toBe('2026-10-24T09:00:00+08:00')
    expect(taipeiDateTimeToIso('2026-01-01', '00:05')).toBe('2026-01-01T00:05:00+08:00')
  })

  it('rejects malformed date or time', () => {
    expect(() => taipeiDateTimeToIso('2026/10/24', '09:00')).toThrow()
    expect(() => taipeiDateTimeToIso('2026-10-24', '9:00')).toThrow()
    expect(() => taipeiDateTimeToIso('', '')).toThrow()
  })

  it('reads any offset as Taipei wall time', () => {
    expect(isoToTaipeiDateTime('2026-10-24T09:00:00+08:00')).toEqual({ date: '2026-10-24', time: '09:00' })
    expect(isoToTaipeiDateTime('2026-10-24T01:00:00Z')).toEqual({ date: '2026-10-24', time: '09:00' })
    // crosses midnight and a month boundary
    expect(isoToTaipeiDateTime('2026-10-31T16:30:00Z')).toEqual({ date: '2026-11-01', time: '00:30' })
  })

  it('round-trips', () => {
    const iso = taipeiDateTimeToIso('2026-12-31', '23:59')
    expect(isoToTaipeiDateTime(iso)).toEqual({ date: '2026-12-31', time: '23:59' })
    const { date, time } = isoToTaipeiDateTime('2026-03-08T18:45:00Z')
    expect(taipeiDateTimeToIso(date, time)).toBe('2026-03-09T02:45:00+08:00')
  })

  it('formats for display in Taipei time', () => {
    expect(formatTaipei('2026-10-24T01:00:00Z')).toBe('2026/10/24 09:00')
    expect(formatTaipeiShort('2026-10-24T09:00:00+08:00')).toBe('10/24 09:00')
    expect(formatTaipeiTime('2026-10-19T06:03:24Z')).toBe('14:03')
  })

  it('formats with seconds for telling same-minute entries apart', () => {
    expect(formatTaipeiShortSeconds('2026-10-19T06:03:24Z')).toBe('10/19 14:03:24')
    expect(formatTaipeiShortSeconds('2026-10-19T06:03:05.999+00:00')).toBe('10/19 14:03:05')
    expect(formatTaipeiShortSeconds('not-a-date')).toBe('not-a-date')
  })

  it('rejects dates and times that do not exist', () => {
    expect(isTaipeiDateTime('2026-10-24', '09:00')).toBe(true)
    expect(isTaipeiDateTime('2026-02-30', '09:00')).toBe(false)
    expect(isTaipeiDateTime('2026-10-24', '25:00')).toBe(false)
    expect(() => taipeiDateTimeToIso('2026-02-30', '09:00')).toThrow()
  })

  it('tells whether an instant is today in Taipei', () => {
    const now = Date.parse('2026-10-24T15:30:00Z') // 10/24 23:30 in Taipei
    expect(isTaipeiToday('2026-10-23T16:00:00Z', now)).toBe(true) // 10/24 00:00 Taipei
    expect(isTaipeiToday('2026-10-23T15:59:59Z', now)).toBe(false) // 10/23 23:59 Taipei
    expect(isTaipeiToday('2026-10-24T16:00:00Z', now)).toBe(false) // 10/25 00:00 Taipei
    expect(isTaipeiToday('not-a-date', now)).toBe(false)
  })

  it('gives the next full hour at least one hour away, across midnight', () => {
    expect(nextHourTaipei(Date.parse('2026-10-24T01:20:00Z'))).toEqual({ date: '2026-10-24', time: '11:00' })
    expect(nextHourTaipei(Date.parse('2026-10-24T15:00:00Z'))).toEqual({ date: '2026-10-25', time: '00:00' })
  })

  it('returns the current instant as UTC ISO', () => {
    expect(nowIso()).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
  })

  it('returns the input unchanged when it is not a date', () => {
    expect(formatTaipei('not-a-date')).toBe('not-a-date')
    expect(formatTaipeiShort('not-a-date')).toBe('not-a-date')
    expect(formatTaipeiTime('not-a-date')).toBe('not-a-date')
  })
})
