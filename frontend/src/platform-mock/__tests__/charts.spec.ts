import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PairBars from '../pages/customer360/PairBars.vue'
import RadarChart, { radarPoint } from '../pages/customer360/RadarChart.vue'
import RingChart from '../pages/customer360/RingChart.vue'

describe('radarPoint', () => {
  it('puts index 0 straight up and goes clockwise', () => {
    const top = radarPoint(0, 6, 100, 100)
    expect(top.x).toBeCloseTo(0, 6)
    expect(top.y).toBeCloseTo(-100, 6)
    const bottom = radarPoint(3, 6, 50, 100)
    expect(bottom.x).toBeCloseTo(0, 6)
    expect(bottom.y).toBeCloseTo(50, 6)
    expect(radarPoint(1, 4, 100, 100).x).toBeCloseTo(100, 6)
  })
})

describe('RadarChart', () => {
  const labels = ['A', 'B', 'C', 'D', 'E', 'F']

  it('renders customer and segment series with a highlighted dot', () => {
    const w = mount(RadarChart, {
      props: {
        labels,
        customer: [10, 20, 30, 40, 50, 60],
        segment: [50, 50, 50, 50, 50, 50],
        highlight: [false, false, false, false, false, true],
        ariaLabel: 'test',
      },
    })
    expect(w.findAll('polygon.chart__area')).toHaveLength(2)
    expect(w.findAll('circle.is-highlight')).toHaveLength(1)
    expect(w.findAll('circle')).toHaveLength(12)
    expect(w.attributes('aria-label')).toBe('test')
  })

  it('renders a single series without segment', () => {
    const w = mount(RadarChart, { props: { labels, customer: [1, 2, 3, 4, 5, 6], ariaLabel: 'x' } })
    expect(w.findAll('polygon.chart__area')).toHaveLength(1)
    expect(w.findAll('circle.is-highlight')).toHaveLength(0)
  })
})

describe('RingChart', () => {
  it('renders one arc per item', () => {
    const items = ['a', 'b', 'c', 'd', 'e', 'f'].map((label, i) => ({ label, ratio: 10 + i }))
    const w = mount(RingChart, { props: { items, ariaLabel: 'ring' } })
    expect(w.findAll('path.chart__arc')).toHaveLength(6)
    expect(w.findAll('path.chart__arc')[0].attributes('stroke-dasharray')).toBe('25 100')
  })
})

describe('PairBars', () => {
  it('renders two bars per row', () => {
    const rows = [
      { label: '存貸比', customer: 62, segment: 48 },
      { label: '活存比', customer: 29, segment: 45 },
    ]
    const w = mount(PairBars, { props: { rows, ariaLabel: 'bars' } })
    expect(w.findAll('li')).toHaveLength(2)
    expect(w.findAll('.gbars__bar')).toHaveLength(4)
  })
})
