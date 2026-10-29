import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PublishBar from '../PublishBar.vue'

type Props = InstanceType<typeof PublishBar>['$props']

function mountBar(props: Partial<Props> = {}) {
  return mount(PublishBar, {
    props: { status: 'DRAFT', scheduledAt: null, canPublish: true, busy: false, scheduleError: null, ...props },
  })
}
const texts = (w: ReturnType<typeof mountBar>) => w.findAll('button').map((b) => b.text())
const btn = (w: ReturnType<typeof mountBar>, label: string) => w.findAll('button').find((b) => b.text() === label)!

describe('PublishBar', () => {
  it('draft shows 捨棄草稿 儲存 預約… 立即發布 and no 存成草稿 or 發布方式', () => {
    const w = mountBar()
    expect(texts(w)).toEqual(['捨棄草稿', '儲存', '預約…', '立即發布'])
    expect(w.text()).not.toContain('存成草稿')
    expect(w.text()).not.toContain('發布方式')
    expect(w.find('input[type="radio"]').exists()).toBe(false)
  })

  it('draft 儲存 saves KEEP_STATUS and 立即發布 publishes IMMEDIATE', async () => {
    const w = mountBar()
    await btn(w, '儲存').trigger('click')
    expect(w.emitted('save')![0]).toEqual(['KEEP_STATUS'])
    await btn(w, '立即發布').trigger('click')
    expect(w.emitted('publish')![0]![0]).toBe('IMMEDIATE')
  })

  it('draft 捨棄草稿 emits discard', async () => {
    const w = mountBar()
    await btn(w, '捨棄草稿').trigger('click')
    expect(w.emitted('discard')).toHaveLength(1)
  })

  it('預約… then 取消 restores 立即發布', async () => {
    const w = mountBar()
    expect(w.find('input[type="date"]').exists()).toBe(false)
    await btn(w, '預約…').trigger('click')
    expect(w.find('input[type="date"]').exists()).toBe(true)
    expect(w.find('input[type="time"]').exists()).toBe(true)
    expect(texts(w)).toContain('預約發布')
    expect(texts(w)).not.toContain('立即發布')

    await btn(w, '取消').trigger('click')
    expect(w.find('input[type="date"]').exists()).toBe(false)
    expect(w.find('input[type="time"]').exists()).toBe(false)
    expect(texts(w)).toContain('立即發布')
    expect(texts(w)).not.toContain('預約發布')
  })

  /** left-to-right sequence of buttons (by text) and the date/time inputs ('日期時間') */
  const sequence = (w: ReturnType<typeof mountBar>) =>
    w.findAll('.pm-actionbar__buttons button, .pm-actionbar__buttons input[type="date"]').map((n) =>
      n.element.tagName === 'INPUT' ? '日期時間' : n.text(),
    )

  it('keeps the spec 3.1 order while scheduling a draft and for a scheduled copy', async () => {
    const w = mountBar()
    await btn(w, '預約…').trigger('click')
    expect(sequence(w)).toEqual(['捨棄草稿', '儲存', '日期時間', '取消', '預約發布'])

    const s = mountBar({ status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    expect(sequence(s)).toEqual(['取消排程', '日期時間', '儲存', '立即發布', '更新預約'])
  })

  it('預約發布 publishes SCHEDULED with the date and time', async () => {
    const w = mountBar()
    await btn(w, '預約…').trigger('click')
    await w.find('input[type="date"]').setValue('2026-11-01')
    await w.find('input[type="time"]').setValue('10:30')
    await btn(w, '預約發布').trigger('click')
    expect(w.emitted('publish')![0]).toEqual(['SCHEDULED', '2026-11-01', '10:30'])
  })

  it('scheduled shows 取消排程 儲存 立即發布 更新預約 and no 捨棄草稿', () => {
    const w = mountBar({ status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    expect(texts(w)).toEqual(['取消排程', '儲存', '立即發布', '更新預約'])
    expect((w.find('input[type="date"]').element as HTMLInputElement).value).toBe('2026-10-24')
    expect((w.find('input[type="time"]').element as HTMLInputElement).value).toBe('09:00')
  })

  it('取消排程 saves DRAFT and 更新預約 publishes SCHEDULED', async () => {
    const w = mountBar({ status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    await btn(w, '取消排程').trigger('click')
    expect(w.emitted('save')![0]).toEqual(['DRAFT'])
    await btn(w, '更新預約').trigger('click')
    expect(w.emitted('publish')![0]).toEqual(['SCHEDULED', '2026-10-24', '09:00'])
  })

  it('scheduled 立即發布 publishes immediately', async () => {
    const w = mountBar({ status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    await w.find('input[type="date"]').setValue('2026-10-30')
    await btn(w, '立即發布').trigger('click')
    expect(w.emitted('publish')![0]![0]).toBe('IMMEDIATE')
  })

  it('without canPublish there is no 預約… 立即發布 更新預約', () => {
    expect(texts(mountBar({ canPublish: false }))).toEqual(['捨棄草稿', '儲存'])
    const s = mountBar({ canPublish: false, status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    expect(texts(s)).toEqual(['取消排程', '儲存'])
  })

  it('shows the range hint only with a scheduleError', async () => {
    const w = mountBar({ status: 'SCHEDULED', scheduledAt: '2026-10-24T01:00:00Z' })
    expect(w.text()).not.toContain('90 天')
    await w.setProps({ scheduleError: '預約時間需在 1 分鐘後、90 天內' })
    expect(w.text()).toContain('預約時間需在 1 分鐘後、90 天內')
  })

  it('renders the default slot on the left', () => {
    const w = mount(PublishBar, {
      props: { status: 'DRAFT', scheduledAt: null, canPublish: true, busy: false, scheduleError: null },
      slots: { default: '<span class="probe">檢查</span>' },
    })
    expect(w.find('.pm-actionbar .probe').exists()).toBe(true)
  })

  it('disables the buttons while busy', () => {
    for (const b of mountBar({ busy: true }).findAll('button')) expect(b.attributes('disabled')).toBeDefined()
  })
})
