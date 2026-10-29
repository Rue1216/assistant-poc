import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('../../../../platform-mock/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }))

import VersionViewer from '../VersionViewer.vue'
import type { ConfigStatus, ConfigVersion, VersionHistoryItem } from '../../types'

function ver(status: ConfigStatus, extra: Partial<ConfigVersion> = {}): ConfigVersion {
  return {
    configKey: 'instruction.contact-prep',
    configType: 'SYSTEM_INSTRUCTION',
    name: '聯繫準備',
    content: '## 適用問題\n內容',
    exampleQuestions: [],
    status,
    version: 9111,
    basedOnVersion: null,
    changeNote: null,
    createdBy: 'EEE22222',
    createdAt: '2026-10-19T08:00:00+08:00',
    updatedBy: 'system',
    updatedAt: '2026-10-19T08:00:00+08:00',
    scheduledAt: null,
    docVersion: 1,
    ...extra,
  }
}

function item(version: number, status: ConfigStatus, updatedAt: string): VersionHistoryItem {
  return { version, status, scheduledAt: null, changeNote: null, basedOnVersion: null, createdBy: 'system', updatedBy: 'system', updatedAt }
}

const HISTORY = [
  item(9113, 'PUBLISHED', '2026-10-19T09:30:00+08:00'),
  item(9112, 'DRAFT', '2026-10-19T08:30:00+08:00'),
  item(9111, 'ARCHIVED', '2026-10-19T08:00:00+08:00'),
]

function mountViewer(version: ConfigVersion, props: Record<string, unknown> = {}) {
  return mount(VersionViewer, {
    props: { version, versions: HISTORY, canEdit: true, busy: false, conflict: false, action: 'apply', ...props },
  })
}
const meta = (w: ReturnType<typeof mountViewer>) => w.find('.pm-viewer__meta').text()
const btn = (w: ReturnType<typeof mountViewer>, text: string) => w.findAll('button').find((b) => b.text() === text)

describe('VersionViewer', () => {
  it('summarises an archived version in one meta line, with the version that replaced it', () => {
    const w = mountViewer(ver('ARCHIVED', { changeNote: '初始版本' }))
    expect(meta(w)).toBe('已封存・10/19 08:00 system 發布・初始版本・已被 10/19 09:30 的版本取代')
  })

  it('shows the original schedule of a discarded version', () => {
    const w = mountViewer(ver('DISCARDED', { scheduledAt: '2026-10-24T09:00:00+08:00' }))
    expect(meta(w)).toContain('原預約 10/24 09:00')
  })

  it('shows where a version was applied from, or a fallback when the source is gone', () => {
    expect(meta(mountViewer(ver('DRAFT', { basedOnVersion: 9111 })))).toContain('套用自 10/19 08:00 的版本')
    expect(meta(mountViewer(ver('DRAFT', { basedOnVersion: 1 })))).toContain('套用自先前的版本')
  })

  it('edit action: hint, 編輯 emits edit, hidden without canEdit', async () => {
    const w = mountViewer(ver('PUBLISHED'), { action: 'edit' })
    expect(w.text()).toContain('編輯會建立一份草稿，發布前不影響線上。')
    expect(btn(w, '以此版本建立草稿')).toBeUndefined()
    await btn(w, '編輯')!.trigger('click')
    expect(w.emitted('edit')).toHaveLength(1)
    expect(btn(mountViewer(ver('PUBLISHED'), { action: 'edit', canEdit: false }), '編輯')).toBeUndefined()
  })

  it('apply action: 以此版本建立草稿 emits apply with the version, hidden without canEdit', async () => {
    const w = mountViewer(ver('ARCHIVED'))
    expect(btn(w, '編輯')).toBeUndefined()
    await btn(w, '以此版本建立草稿')!.trigger('click')
    expect(w.emitted('apply')).toEqual([[9111]])
    expect(btn(mountViewer(ver('ARCHIVED'), { canEdit: false }), '以此版本建立草稿')).toBeUndefined()
  })

  it('keeps 複製 Markdown and drops the old banner and info cards', () => {
    const w = mountViewer(ver('ARCHIVED'))
    expect(btn(w, '複製 Markdown')).toBeDefined()
    for (const t of ['正在檢視', '原作者', '預約時間', '套用來源']) expect(w.text()).not.toContain(t)
  })

  it('uses the shared sticky action bar (no static override) for both actions', () => {
    for (const action of ['edit', 'apply']) {
      const footer = mountViewer(ver('PUBLISHED'), { action }).find('footer')
      expect(footer.classes()).toContain('pm-actionbar')
      expect(footer.classes()).not.toContain('pm-actionbar--viewer')
    }
  })

  it('shows the conflict banner and emits reload', async () => {
    const w = mountViewer(ver('ARCHIVED'), { conflict: true })
    await btn(w, '重新載入')!.trigger('click')
    expect(w.emitted('reload')).toHaveLength(1)
  })
})
