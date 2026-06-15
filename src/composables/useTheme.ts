import { ref, watch } from 'vue'

type Theme = 'light' | 'dark' | 'auto'

const STORAGE_KEY = 'exam-theme-preference'

function getStoredTheme(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'auto') return stored
  } catch { /* ignore */ }
  return 'auto'
}

function applyTheme(theme: Theme) {
  const root = document.documentElement
  if (theme === 'auto') {
    root.removeAttribute('data-theme')
  } else {
    root.setAttribute('data-theme', theme)
  }
}

const currentTheme = ref<Theme>(getStoredTheme())

// Apply on init
applyTheme(currentTheme.value)

// Watch for changes
watch(currentTheme, (newTheme) => {
  applyTheme(newTheme)
  try { localStorage.setItem(STORAGE_KEY, newTheme) } catch { /* ignore */ }
})

export function useTheme() {
  function toggleTheme() {
    const order: Theme[] = ['light', 'dark', 'auto']
    const idx = order.indexOf(currentTheme.value)
    currentTheme.value = order[(idx + 1) % order.length]
  }

  function setTheme(theme: Theme) {
    currentTheme.value = theme
  }

  function themeLabel(): string {
    switch (currentTheme.value) {
      case 'light': return '浅色'
      case 'dark': return '深色'
      case 'auto': return '跟随系统'
    }
  }

  return { currentTheme, toggleTheme, setTheme, themeLabel }
}
