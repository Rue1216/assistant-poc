import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionDiff from '@/views/maintenance/prompt-management/components/version-diff.vue'
import { diffLines } from '@/utils/markdown/line-diff'
import type { ConfigStatus, VersionHistoryItem } from '@/beans/prompt-management-bean'

function item(version: number, status: ConfigStatus, updatedAt: string): VersionHistoryItem {
  return { version, status, scheduledAt: null, changeNote: null, basedOnVersion: null, createdBy: 'a', updatedBy: 'a', updatedAt }
}

function mountDiff(beforeName: string, afterName: string, beforeText: string, afterText: string) {
  return mount(VersionDiff, {
    props: {
      before: item(9111, 'PUBLISHED', '2026-10-19T08:00:00+08:00'),
      after: item(9112, 'DRAFT', '2026-10-19T09:30:00+08:00'),
      nameDiff: diffLines(beforeName, afterName),
      contentDiff: diffLines(beforeText, afterText),
    },
  })
}

describe('VersionDiff', () => {
  it('shows one line when the names are the same', () => {
    const w = mountDiff('聯繫準備', '聯繫準備', 'a', 'b')
    expect(w.text()).toContain('名稱相同')
    expect(w.find('.pm-diff__name').exists()).toBe(false)
  })

  it('shows the name block when the names differ', () => {
    const w = mountDiff('舊名', '新名', 'a', 'a')
    expect(w.text()).not.toContain('名稱相同')
    expect(w.find('.pm-diff__name').text()).toContain('舊名')
    expect(w.find('.pm-diff__name').text()).toContain('新名')
  })

  it('renders the changed middle of paired lines with del / ins', () => {
    const w = mountDiff('n', 'n', '一律忽略，必要時提醒。', '一律忽略，也不要轉述。')
    expect(w.find('del').text()).toBe('必要時提醒')
    expect(w.find('ins').text()).toBe('也不要轉述')
  })

  it('has a header with both versions and the line stats, and no back or apply action', () => {
    const w = mountDiff('n', 'n', 'a', 'b')
    const t = w.text()
    expect(t).toContain('− 目前生效 10/19 08:00')
    expect(t).toContain('＋ 草稿 10/19 09:30')
    expect(t).toContain('＋1 −1 行')
    expect(t).not.toContain('返回')
    expect(t).not.toContain('套用')
  })
})
