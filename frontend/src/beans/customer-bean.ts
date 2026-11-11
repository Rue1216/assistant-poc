// Mirrors the platform customer-360 DTOs; wire names (including the platform's spellings) are kept as-is.

export interface ProfileCard {
  name: string
  age: string
  segement: string
  primaryBranch: string
  financialCounsultant: string
}

export interface CampaignDetail {
  receiptNo: string
  campaignName: string
  campaignDuration: string
  campaignSummary: string
  campaignEnabled: string
  attachment: string[]
}

export interface ManagementCard {
  campaignList: CampaignDetail[]
  recommendedProducts: string[]
  insight: string
}

/** Both lists are ordered [customer, segment average, segment top 10]; ratios are percent-number strings. */
export interface CategoryData {
  ratioList: string[]
  amountList: number[]
}

export interface AumItems {
  demandDeposit: CategoryData
  timeDeposit: CategoryData
  fund: CategoryData
  financialProduct: CategoryData
  insurance: CategoryData
  financialAsset: CategoryData
}

export interface LumItems {
  mtgPurchase: CategoryData
  mtgRevolve: CategoryData
  collateral: CategoryData
  creditLoan: CategoryData
  finAssetLoan: CategoryData
  ccBalance: CategoryData
}

export interface AssetBlock {
  auMData: AumItems
  customerTotal: number
  segmentTotal: number
  topTenTotal: number
}

export interface LiabilityBlock {
  luMData: LumItems
  customerTotal: number
  segmentTotal: number
  topTenTotal: number
}

export interface MemberRankData {
  memberRankCode: string | null
  memberRankName: string | null
  progressPercent: string | null
  segementProgressPercent: string | null
  upgradeOrDownGradeAlert: string | null
  customerAum: string | null
  segmentAverageAum: string | null
}

export interface RadarPoint {
  rank: number
  info?: string
  highlight?: boolean
}

export interface RadarView {
  labels: string[]
  customerData: RadarPoint[]
  segmentData: RadarPoint[]
  /** Only filled for the consumption-behavior view. */
  tagList?: string[]
}

export interface ProductMixData {
  ldr: number
  casaAndWealthAumRatio: number[]
}

export interface ProductMix {
  aumYield: string
  customerData: ProductMixData
  segmentData: ProductMixData
}

export interface CustomerDto {
  dataDate: string
  customerProfile: ProfileCard
  customerManagement: ManagementCard
  aum: AssetBlock
  lum: LiabilityBlock
  memberRank: MemberRankData
  wealthPotential: RadarView
  productMix: ProductMix
  assetProduct: RadarView
  liabilitiesAndFx: RadarView
  accountRelationshipDepth: RadarView
  consumptionBehavior: RadarView
  channelBehavior: RadarView
  digitalBehavior: RadarView
}

export type AlertType = 'MEMBER_DOWNGRADE' | 'LARGE_INFLOW' | 'IDLE_FUNDS' | 'STOP_PROFIT_LOSS' | 'OTHER'

export type FlagType = 'NO_MARKETING' | 'COMPLAINT' | 'HIGH_ASSET' | 'ZONE_CUSTOMER' | 'PROFESSIONAL_INVESTOR'

export interface KeyAlert {
  type: AlertType
  title: string
  detail: string
  date: string
}

export interface CustomFlagging {
  type: FlagType
  label: string
  expiryDate: string | null
}

export interface RealTimeCustomerDto {
  attentionCustomer: boolean
  specialCareCustomer: boolean
  profilePhoto: { tags: string[] }
  customerFlaggingList: CustomFlagging[]
  keyAlerts: { items: KeyAlert[] }
}
