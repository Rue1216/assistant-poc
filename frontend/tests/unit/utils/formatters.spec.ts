import { describe, expect, it } from 'vitest'
import { formatAmount, formatPercent, formatPoints, formatWan } from '@/utils/formatters'

describe('format', () => {
  it('rounds amounts to two decimals and drops trailing zeros', () => {
    expect(formatAmount(1000.345)).toBe('1,000.35 元')
    expect(formatAmount(12.304)).toBe('12.3 元')
    expect(formatAmount(12.004)).toBe('12 元')
    expect(formatAmount(8_200_000)).toBe('8,200,000 元')
    expect(formatAmount(-1500)).toBe('-1,500 元')
  })

  it('formats 萬 for cards', () => {
    expect(formatWan(8_200_000)).toBe('820 萬')
    expect(formatWan(24_000_000)).toBe('2,400 萬')
  })

  it('formats percent with two decimals from number or string', () => {
    expect(formatPercent(25)).toBe('25.00%')
    expect(formatPercent('36.6')).toBe('36.60%')
  })

  it('carries sign on percentage points', () => {
    expect(formatPoints(16.6)).toBe('+16.60 個百分點')
    expect(formatPoints(-7.4)).toBe('-7.40 個百分點')
    expect(formatPoints(0)).toBe('0.00 個百分點')
    expect(formatPoints(0.001)).toBe('0.00 個百分點')
    expect(formatPoints(-0.001)).toBe('0.00 個百分點')
  })
})
