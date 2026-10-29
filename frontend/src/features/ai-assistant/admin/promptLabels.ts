import { extractExampleQuestions } from '../markdown/exampleQuestions'
import { formatTaipeiShort } from '../taipeiTime'
import type { ConfigStatus, ConfigType, ConfigVersion, VersionHistoryItem } from '../types'

export const STATUS_BADGE: Record<ConfigStatus, string> = {
  DRAFT: 'badge--draft',
  SCHEDULED: 'badge--scheduled',
  PUBLISHED: 'badge--published',
  ARCHIVED: 'badge--archived',
  DISCARDED: 'badge--discarded',
}

/** What the `updatedBy` user last did to a version in this status. */
export const STATUS_VERB: Record<ConfigStatus, string> = {
  DRAFT: '儲存',
  SCHEDULED: '儲存',
  PUBLISHED: '發布',
  ARCHIVED: '發布',
  DISCARDED: '捨棄',
}

export const APPLICABLE: ConfigStatus[] = ['PUBLISHED', 'ARCHIVED', 'DISCARDED']

export const MAX_CONTENT_BYTES = 65_536

/** Headings the pre-publish validation requires (backend ConfigValidator). */
export const REQUIRED_SECTIONS: Record<ConfigType, string[]> = {
  SYSTEM_PROMPT: [],
  SYSTEM_INSTRUCTION: ['## 適用問題'],
}

export function hasSection(content: string, heading: string): boolean {
  return content.split(/\r\n|\r|\n/).some((line) => line.trimEnd() === heading)
}

export function byteLength(text: string): number {
  return new TextEncoder().encode(text).length
}

export function formatKb(bytes: number): string {
  return `${(bytes / 1024).toFixed(1)} KB`
}

/** The passing list of the pre-publish check, describing the rules the content passed. */
export function passedRuleDescriptions(configType: ConfigType, content: string): string[] {
  const rows: string[] = []
  if (configType === 'SYSTEM_INSTRUCTION') {
    rows.push('「適用問題」已填寫', `範例問題 ${extractExampleQuestions(content).length} 題（最多 5 題，每題 40 字內）`)
  }
  rows.push(
    '未偵測到 API key、密碼、未允許的網址、IP、身分證號、手機或顧客編號',
    `內容大小 ${formatKb(byteLength(content))}（上限 64 KB）`,
  )
  return rows
}

/** Version status as shown in lists and the version meta line. */
export const LIST_STATUS: Record<ConfigStatus, string> = {
  DRAFT: '草稿',
  SCHEDULED: '已排程',
  PUBLISHED: '目前生效',
  ARCHIVED: '已封存',
  DISCARDED: '已捨棄',
}

/** Required headings the content lacks, in REQUIRED_SECTIONS order. */
export function missingSections(configType: ConfigType, content: string): string[] {
  return REQUIRED_SECTIONS[configType].filter((heading) => !hasSection(content, heading))
}

/** One-line summary of a version: status, who/when, note, where it came from, and what replaced it. */
export function versionMeta(v: ConfigVersion | VersionHistoryItem, versions: VersionHistoryItem[]): string {
  const parts: string[] = [LIST_STATUS[v.status], `${formatTaipeiShort(v.updatedAt)} ${v.updatedBy} ${STATUS_VERB[v.status]}`]
  if (v.changeNote) parts.push(v.changeNote)
  if (v.basedOnVersion != null) {
    const source = versions.find((x) => x.version === v.basedOnVersion)
    parts.push(source ? `套用自 ${formatTaipeiShort(source.updatedAt)} 的版本` : '套用自先前的版本')
  }
  if (v.status === 'DISCARDED' && v.scheduledAt) parts.push(`原預約 ${formatTaipeiShort(v.scheduledAt)}`)
  if (v.status === 'ARCHIVED') {
    const next = versions
      .filter((x) => x.version > v.version && (x.status === 'PUBLISHED' || x.status === 'ARCHIVED'))
      .sort((a, b) => a.version - b.version)[0]
    if (next) parts.push(`已被 ${formatTaipeiShort(next.updatedAt)} 的版本取代`)
  }
  return parts.join('・')
}
