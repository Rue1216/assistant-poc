import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VersionHistory from '../VersionHistory.vue'
import type { ConfigStatus, VersionFeedback, VersionHistoryItem } from '../../types'

function item(version: number, status: ConfigStatus, updatedAt: string, extra: Partial<VersionHistoryItem> = {}) {
  return {
    version,
    status,
    scheduledAt: null,
    changeNote: null,
    basedOnVersion: null,
    createdBy: 'it.admin',
    updatedBy: 'it.admin',
    updatedAt,
    ...extra,
  } satisfies VersionHistoryItem
}

const VERSIONS: VersionHistoryItem[] = [
  item(9135, 'ARCHIVED', '2026-10-19T08:00:00+08:00', { updatedBy: 'system', changeNote: '初始版本' }),
  item(9136, 'PUBLISHED', '2026-10-19T09:30:00+08:00', { updatedBy: 'backoffice.admin', changeNote: '新增段落' }),
  item(9137, 'DISCARDED', '2026-10-19T10:40:00+08:00', {
    createdBy: 'backoffice.admin',
    scheduledAt: '2026-10-22T09:00:00+08:00',
    changeNote: '只列最近 3 筆',
  }),
  item(9138, 'SCHEDULED', '2026-10-19T11:00:00+08:00', { scheduledAt: '2026-10-24T09:00:00+08:00' }),
]

function mountHistory(versions = VERSIONS, canEdit = true, feedback: Record<number, VersionFeedback> = {}) {
  return mount(VersionHistory, { props: { versions, activeVersions: [], canEdit, busy: false, feedback } })
}

const rows = (w: ReturnType<typeof mountHistory>) => w.findAll('.pm-ver')
const compareButton = (w: ReturnType<typeof mountHistory>) => w.find('.pm-versions__compare')

describe('VersionHistory', () => {
  it('labels versions by time and never shows version numbers', () => {
    const w = mountHistory()
    const text = w.text()
    expect(text).toContain('10/19 09:30')
    expect(text).toContain('backoffice.admin 發布')
    expect(text).toContain('it.admin 儲存')
    expect(text).toContain('預約 10/24 09:00 發布')
    expect(text).not.toMatch(/\bv\d+\b/i)
    expect(text).not.toMatch(/版本\s*\d/)
    expect(text).not.toMatch(/第\s*\d+\s*版/)
  })

  it('puts no version number in any text or attribute, including discarded rows', async () => {
    const w = mountHistory()
    await w.find('.pm-versions__toggle input').setValue(true)
    await w.findAll('.pm-ver__check')[0]!.setValue(true)
    expect(w.text()).not.toMatch(/913[5-9]/)
    for (const el of w.findAll('*')) {
      for (const attr of Array.from(el.element.attributes)) expect(attr.value, attr.name).not.toMatch(/913[5-9]/)
    }
  })

  it('lists newest first with status labels', () => {
    const w = mountHistory() // VERSIONS is given oldest first
    expect(rows(w).map((r) => r.find('.pm-ver__status').text())).toEqual(['已排程', '已發布・目前生效', '已封存'])
  })

  it('enables compare only when exactly two versions are selected', async () => {
    const w = mountHistory()
    const checks = w.findAll('.pm-ver__check')
    expect(compareButton(w).attributes('disabled')).toBeDefined()
    await checks[0]!.setValue(true)
    expect(compareButton(w).attributes('disabled')).toBeDefined()
    await checks[2]!.setValue(true)
    expect(compareButton(w).attributes('disabled')).toBeUndefined()
    await checks[1]!.setValue(true)
    expect(compareButton(w).attributes('disabled')).toBeDefined()
    await checks[1]!.setValue(false)

    await compareButton(w).trigger('click')
    expect(w.emitted('compare')).toEqual([[9135, 9138]]) // older first
  })

  it('offers apply only on published, archived and discarded versions', async () => {
    const w = mountHistory()
    await w.find('.pm-versions__toggle input').setValue(true)
    const withApply = rows(w)
      .filter((r) => r.find('.pm-ver__apply').exists())
      .map((r) => r.find('.pm-ver__status').text())
    expect(withApply).toEqual(['已捨棄', '已發布・目前生效', '已封存'])

    await rows(w)[2]!.find('.pm-ver__apply').trigger('click')
    expect(w.emitted('apply')).toEqual([[9136]])
  })

  it('offers no apply without edit permission', () => {
    expect(mountHistory(VERSIONS, false).find('.pm-ver__apply').exists()).toBe(false)
  })

  it('hides discarded versions until "顯示已捨棄的版本" is toggled; discarded rows show creator and 原預約 time', async () => {
    const w = mountHistory()
    expect(w.text()).not.toContain('只列最近 3 筆')
    expect(rows(w).some((r) => r.classes('is-discarded'))).toBe(false)
    const toggle = w.find('.pm-versions__toggle')
    expect(toggle.text()).toBe('顯示已捨棄的版本（1）')

    await toggle.find('input').setValue(true)

    const discarded = rows(w).find((r) => r.classes('is-discarded'))!
    expect(discarded.text()).toContain('it.admin 捨棄')
    expect(discarded.text()).toContain('原作者 backoffice.admin')
    expect(discarded.text()).toContain('原預約 10/22 09:00')
    expect(discarded.text()).toContain('只列最近 3 筆')
  })

  it('shows applied-from label when basedOnVersion is set', () => {
    const versions = [item(9139, 'DRAFT', '2026-10-20T09:12:00+08:00', { basedOnVersion: 9135 }), ...VERSIONS.slice(0, 2)]
    const w = mountHistory(versions)
    expect(rows(w)[0]!.find('.pm-ver__from').text()).toBe('套用自 10/19 08:00 的版本')
    expect(rows(w)[1]!.find('.pm-ver__from').exists()).toBe(false)
  })

  it('shows seconds only on rows that share a minute', () => {
    const versions = [
      item(9140, 'DRAFT', '2026-10-19T12:00:41+08:00'),
      item(9139, 'PUBLISHED', '2026-10-19T12:00:07+08:00'),
      item(9138, 'ARCHIVED', '2026-10-19T11:00:00+08:00'),
    ]
    const w = mountHistory(versions)
    const times = rows(w).map((r) => r.find('.pm-ver__time').text())
    expect(times).toEqual(['10/19 12:00:41', '10/19 12:00:07', '10/19 11:00'])
    expect(w.findAll('.pm-ver__check')[0]!.attributes('aria-label')).toContain('10/19 12:00:41')
    expect(w.findAll('.pm-ver__check')[2]!.attributes('aria-label')).toContain('10/19 11:00')
    expect(w.findAll('.pm-ver__check')[2]!.attributes('aria-label')).not.toContain('11:00:')
  })

  it('counts hidden discarded rows only once they are shown', async () => {
    const versions = [
      item(9140, 'PUBLISHED', '2026-10-19T12:00:41+08:00'),
      item(9139, 'DISCARDED', '2026-10-19T12:00:07+08:00'),
    ]
    const w = mountHistory(versions)
    expect(rows(w).map((r) => r.find('.pm-ver__time').text())).toEqual(['10/19 12:00'])
    await w.find('.pm-versions__toggle input').setValue(true)
    expect(rows(w).map((r) => r.find('.pm-ver__time').text())).toEqual(['10/19 12:00:41', '10/19 12:00:07'])
  })

  it('opens a version when its row is clicked', async () => {
    const w = mountHistory()
    await rows(w)[1]!.find('.pm-ver__main').trigger('click')
    expect(w.emitted('view')).toEqual([[9136]])
  })

  describe('feedback statistics', () => {
    const fb = (version: number, up: number, down: number) => ({ version, up, down })

    it('shows counts and percentage on published and archived rows only', () => {
      const w = mountHistory(VERSIONS, true, {
        9135: fb(9135, 1, 1),
        9136: fb(9136, 12, 3),
        9137: fb(9137, 5, 5),
        9138: fb(9138, 4, 0),
      })
      const text = rows(w).map((r) => r.text())
      const published = text.find((t) => t.includes('已發布'))!
      expect(published).toContain('12')
      expect(published).toContain('（80%）')
      expect(text.find((t) => t.includes('已封存'))).toContain('（50%）')
      expect(w.text()).not.toContain('👍')
      expect(w.text()).not.toContain('👎')
      expect(w.text()).not.toContain('（100%）')
      expect(w.text()).not.toContain('（0%）')
      const labels = w.findAll('.pm-ver__feedback').map((f) => f.attributes('aria-label'))
      expect(labels).toEqual(['讚 12、倒讚 3（80%）', '讚 1、倒讚 1（50%）'])
      expect(w.findAll('.pm-ver__feedback svg')).toHaveLength(4)
      expect(w.text()).toContain('回饋統計涵蓋 180 天內的回答')
    })

    it('shows nothing without statistics, footnote included', () => {
      const w = mountHistory()
      expect(w.text()).not.toContain('👍')
      expect(w.find('.pm-ver__feedback').exists()).toBe(false)
      expect(w.text()).not.toContain('回饋統計涵蓋')
    })

    it('hides the footnote when the only statistics belong to a draft', () => {
      const w = mountHistory([item(1, 'DRAFT', '2026-10-19T08:00:00+08:00')], true, { 1: fb(1, 2, 2) })
      expect(w.text()).not.toContain('回饋統計涵蓋')
    })
  })
})
