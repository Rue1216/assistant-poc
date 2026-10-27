import { afterEach, describe, expect, it } from 'vitest'
import { THEME_STORAGE_KEY, useTheme } from '../useTheme'

describe('useTheme', () => {
  afterEach(() => {
    document.documentElement.classList.remove('dark')
    localStorage.clear()
  })

  it('starts from the class applied by the index.html script', () => {
    document.documentElement.classList.add('dark')

    expect(useTheme().dark.value).toBe(true)
  })

  it('toggles html.dark and remembers the choice', () => {
    const { dark, toggle } = useTheme()

    toggle()

    expect(dark.value).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')

    toggle()

    expect(dark.value).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
  })
})
