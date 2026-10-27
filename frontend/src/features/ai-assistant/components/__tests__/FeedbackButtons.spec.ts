import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import FeedbackButtons from '../FeedbackButtons.vue'

describe('FeedbackButtons', () => {
  it('labels the buttons', () => {
    const w = mount(FeedbackButtons, { props: { rating: null } })
    expect(w.find('[aria-label="有幫助"]').exists()).toBe(true)
    expect(w.find('[aria-label="沒有幫助"]').exists()).toBe(true)
  })

  it('emits UP when nothing is selected', async () => {
    const w = mount(FeedbackButtons, { props: { rating: null } })
    await w.get('[aria-label="有幫助"]').trigger('click')
    expect(w.emitted('rate')).toEqual([['UP']])
  })

  it('emits null when the selected button is clicked again', async () => {
    const w = mount(FeedbackButtons, { props: { rating: 'UP' } })
    await w.get('[aria-label="有幫助"]').trigger('click')
    expect(w.emitted('rate')).toEqual([[null]])
  })

  it('switches to DOWN', async () => {
    const w = mount(FeedbackButtons, { props: { rating: 'UP' } })
    await w.get('[aria-label="沒有幫助"]').trigger('click')
    expect(w.emitted('rate')).toEqual([['DOWN']])
  })

  it('marks only the selected button as pressed', () => {
    const w = mount(FeedbackButtons, { props: { rating: 'DOWN' } })
    expect(w.get('[aria-label="有幫助"]').attributes('aria-pressed')).toBe('false')
    expect(w.get('[aria-label="沒有幫助"]').attributes('aria-pressed')).toBe('true')
  })

  it('draws a filled icon only on the selected button', () => {
    const w = mount(FeedbackButtons, { props: { rating: 'DOWN' } })
    expect(w.get('[aria-label="沒有幫助"] path').attributes('fill')).toBe('currentColor')
    expect(w.get('[aria-label="有幫助"] path').attributes('fill')).toBeUndefined()
  })
})
