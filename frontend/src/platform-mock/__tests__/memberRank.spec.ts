import { describe, expect, it } from 'vitest'
import { TIERS, tierOf } from '../memberRank'

describe('memberRank', () => {
  it('maps non-member, null and unknown codes to NONE', () => {
    for (const code of ['0', '1', '2', '3', 'Z', null, 'X', 'constructor']) {
      expect(tierOf(code).key, String(code)).toBe('NONE')
    }
  })

  it('maps codes to tiers with thresholds in yuan', () => {
    expect(tierOf('P').key).toBe('NEW_RICH')
    expect(tierOf('Q').key).toBe('NEW_RICH')
    expect(TIERS.NEW_RICH.threshold).toBe(3_000_000)
    expect(TIERS.NEW_RICH.downgradeLine).toBe(3_500_000)
    expect(tierOf('N').key).toBe('ELITE')
    expect(tierOf('O').key).toBe('ELITE')
    expect(TIERS.ELITE.threshold).toBe(10_000_000)
    expect(TIERS.ELITE.downgradeLine).toBe(12_000_000)
    expect(tierOf('M').key).toBe('SUMMIT')
    expect(TIERS.SUMMIT.threshold).toBe(30_000_000)
    expect(TIERS.SUMMIT.downgradeLine).toBe(33_000_000)
    for (const code of ['V', 'K']) expect(tierOf(code).key).toBe('PRIVATE')
    expect(TIERS.PRIVATE.threshold).toBeNull()
    expect(TIERS.PRIVATE.downgradeLine).toBeNull()
    for (const code of ['R', 'W', 'F', 'G', 'H', 'I', 'J']) expect(tierOf(code).key, code).toBe('RELATIONSHIP')
  })

  it('links the next tier and carries names and benefits', () => {
    expect(TIERS.NONE.next).toBe('NEW_RICH')
    expect(TIERS.NEW_RICH.next).toBe('ELITE')
    expect(TIERS.SUMMIT.next).toBe('PRIVATE')
    expect(TIERS.PRIVATE.next).toBeNull()
    expect(TIERS.RELATIONSHIP.next).toBeNull()
    expect(TIERS.NEW_RICH.name).toBe('新富')
    expect(TIERS.PRIVATE.name).toBe('極致／私銀')
    expect(TIERS.NONE.benefits).toEqual([])
    for (const t of [TIERS.NEW_RICH, TIERS.ELITE, TIERS.SUMMIT, TIERS.PRIVATE]) {
      expect(t.benefits.length).toBeGreaterThanOrEqual(3)
    }
  })
})
