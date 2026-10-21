import { extractExampleQuestions } from '../markdown/exampleQuestions'
import type { ConfigStatus, ConfigType } from '../types'

/** Version history row labels (02 section 3.0.1). Versions are told apart by time, never by number. */
export const STATUS_LABEL: Record<ConfigStatus, string> = {
  DRAFT: '草稿・編輯中',
  SCHEDULED: '已排程',
  PUBLISHED: '已發布・目前生效',
  ARCHIVED: '已封存',
  DISCARDED: '已捨棄',
}

export const STATUS_SHORT: Record<ConfigStatus, string> = {
  DRAFT: '草稿',
  SCHEDULED: '已排程',
  PUBLISHED: '已發布',
  ARCHIVED: '已封存',
  DISCARDED: '已捨棄',
}

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
  SYSTEM_PROMPT: ['## 角色', '## 安全規則', '## 回答格式'],
  SYSTEM_INSTRUCTION: ['## 適用問題', '## 回答套版', '## 範例問題', '## 注意事項'],
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
  const sections = REQUIRED_SECTIONS[configType].map((h) => h.replace(/^## /, '')).join('、')
  const rows = [`必要章節：${sections}`]
  if (configType === 'SYSTEM_INSTRUCTION') {
    rows.push(`範例問題 ${extractExampleQuestions(content).length} 題（1–5 題，每題 40 字內）`)
  }
  rows.push(
    '未偵測到 API key、密碼、網址、身分證號、手機或顧客編號',
    '未偵測到越權指令（例如「忽略以上規則」）',
    `內容大小 ${formatKb(byteLength(content))}（上限 64 KB）`,
  )
  return rows
}
