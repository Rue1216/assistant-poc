// Wealth-member rank codes mapped to tiers; values match the backend MemberRankTable.
// 示意，待對照平台權益文案 (benefit texts are illustrative until checked against the platform wording).

export type TierKey = 'NONE' | 'NEW_RICH' | 'ELITE' | 'SUMMIT' | 'PRIVATE' | 'RELATIONSHIP'

export interface Tier {
  key: TierKey
  name: string
  /** Entry threshold in yuan, or null when not defined. */
  threshold: number | null
  /** Downgrade-protection line in yuan, or null when not defined. */
  downgradeLine: number | null
  benefits: string[]
  /** The next tier up, or null for the top tier and for relationship accounts. */
  next: TierKey | null
}

const wan = (n: number) => n * 10_000

export const TIERS: Record<TierKey, Tier> = {
  NONE: { key: 'NONE', name: '非理財會員', threshold: null, downgradeLine: null, benefits: [], next: 'NEW_RICH' },
  NEW_RICH: {
    key: 'NEW_RICH',
    name: '新富',
    threshold: wan(300),
    downgradeLine: wan(350),
    benefits: ['專屬理專服務', '跨行轉帳免費 10 次／月', '理財講座優先報名'],
    next: 'ELITE',
  },
  ELITE: {
    key: 'ELITE',
    name: '菁英',
    threshold: wan(1000),
    downgradeLine: wan(1200),
    benefits: ['基金申購手續費 5 折', '機場貴賓室 4 次／年', '年度健康檢查'],
    next: 'SUMMIT',
  },
  SUMMIT: {
    key: 'SUMMIT',
    name: '登峰',
    threshold: wan(3000),
    downgradeLine: wan(3300),
    benefits: ['專屬財富管理團隊', '機場貴賓室 8 次／年', '年度健康檢查（含眷屬 1 名）'],
    next: 'PRIVATE',
  },
  PRIVATE: {
    key: 'PRIVATE',
    name: '極致／私銀',
    threshold: null,
    downgradeLine: null,
    benefits: ['私人銀行家服務', '家族傳承諮詢', '機場接送 4 次／年'],
    next: null,
  },
  RELATIONSHIP: {
    key: 'RELATIONSHIP',
    name: '關係戶／親子戶',
    threshold: null,
    downgradeLine: null,
    benefits: [],
    next: null,
  },
}

const CODES: Record<string, TierKey> = {
  '0': 'NONE',
  '1': 'NONE',
  '2': 'NONE',
  '3': 'NONE',
  Z: 'NONE',
  P: 'NEW_RICH',
  Q: 'NEW_RICH',
  N: 'ELITE',
  O: 'ELITE',
  M: 'SUMMIT',
  V: 'PRIVATE',
  K: 'PRIVATE',
  R: 'RELATIONSHIP',
  W: 'RELATIONSHIP',
  F: 'RELATIONSHIP',
  G: 'RELATIONSHIP',
  H: 'RELATIONSHIP',
  I: 'RELATIONSHIP',
  J: 'RELATIONSHIP',
}

/** Null or unknown codes map to NONE. */
export function tierOf(code: string | null): Tier {
  const key = code !== null && Object.hasOwn(CODES, code) ? CODES[code] : 'NONE'
  return TIERS[key]
}
