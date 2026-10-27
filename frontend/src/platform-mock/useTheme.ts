import { readonly, ref } from 'vue'

/** Must match the inline script in index.html, which applies the saved theme before the app mounts. */
export const THEME_STORAGE_KEY = 'platform-theme'

/** Dark mode = `html.dark`; tokens.css overrides the colour tokens, so components need no dark rules. */
export function useTheme() {
  const root = document.documentElement
  const dark = ref(root.classList.contains('dark'))

  function toggle() {
    dark.value = !dark.value
    root.classList.toggle('dark', dark.value)
    try {
      localStorage.setItem(THEME_STORAGE_KEY, dark.value ? 'dark' : 'light')
    } catch {
      // storage unavailable (private mode etc.): the choice lasts for this page only
    }
  }

  return { dark: readonly(dark), toggle }
}
