import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'api-convert-theme'

const mode = ref<ThemeMode>(loadMode())

function loadMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch { /* localStorage unavailable */ }
  return 'system'
}

function systemPrefersDark(): boolean {
  return typeof window !== 'undefined'
    && typeof window.matchMedia === 'function'
    && window.matchMedia('(prefers-color-scheme: dark)').matches
}

const prefersDark = ref(systemPrefersDark())

if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
  const mql = window.matchMedia('(prefers-color-scheme: dark)')
  mql.addEventListener('change', (event) => {
    prefersDark.value = event.matches
  })
}

/**
 * 实际生效的暗色状态：显式 dark → true；显式 light → false；system → 跟随系统。
 * <p>
 * 业务层应当订阅 effectiveDark 并把结果通过 {@code NConfigProvider} 注入到 Naive UI。
 */
export const effectiveDark = computed(() => {
  if (mode.value === 'dark') return true
  if (mode.value === 'light') return false
  return prefersDark.value
})

watch(mode, (next) => {
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch { /* ignore quota errors */ }
  applyToHtml(next === 'dark' || (next === 'system' && prefersDark.value))
}, { immediate: false })

watch(effectiveDark, (dark) => applyToHtml(dark))

function applyToHtml(dark: boolean) {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = dark ? 'dark' : 'light'
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
}

// 启动时立即应用一次（防止刷新时短暂闪烁）
if (typeof document !== 'undefined') {
  applyToHtml(effectiveDark.value)
}

/**
 * 主题组合式函数：暴露当前模式 / 实际生效的暗色状态 / 设置器。
 */
export function useTheme() {
  function setMode(next: ThemeMode) {
    mode.value = next
  }
  function toggle() {
    setMode(effectiveDark.value ? 'light' : 'dark')
  }
  return { mode, effectiveDark, setMode, toggle }
}
