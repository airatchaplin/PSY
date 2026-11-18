import { computed, ref } from 'vue'

export type ThemeId =
  | 'rose'
  | 'masculine'
  | 'feminine'
  | 'white'
  | 'black'
  | 'ios-light'
  | 'ios-dark'

/** Stored preference: follow OS, or a fixed theme. */
export type ThemePreference = 'system' | ThemeId

export interface ThemeOption {
  id: ThemeId
  label: string
  description: string
  swatches: [string, string, string]
}

const STORAGE_KEY = 'psy.theme'
const LIGHT_SYSTEM_THEME: ThemeId = 'ios-light'
const DARK_SYSTEM_THEME: ThemeId = 'ios-dark'

export const THEMES: ThemeOption[] = [
  {
    id: 'ios-light',
    label: 'Светлая',
    description: 'Системный шрифт, #F2F2F7 и синий акцент как в последнем iOS',
    swatches: ['#007AFF', '#F2F2F7', '#FFFFFF']
  },
  {
    id: 'ios-dark',
    label: 'Тёмная',
    description: 'OLED-чёрный, серые уровни iOS и системный шрифт',
    swatches: ['#0A84FF', '#1C1C1E', '#000000']
  },
  {
    id: 'masculine',
    label: 'Графит',
    description: 'Строгий графит, Fraunces и чёткие анимации',
    swatches: ['#2f3e46', '#52796f', '#e8eef2']
  },
  {
    id: 'feminine',
    label: 'Персик',
    description: 'Персик, мягкая антиква и плавные переходы',
    swatches: ['#c45c7a', '#f3c4b4', '#fff6f2']
  },
  {
    id: 'white',
    label: 'Белая',
    description: 'Минимализм DM Sans и короткие переходы',
    swatches: ['#2d3436', '#dfe6e9', '#ffffff']
  },
  {
    id: 'black',
    label: 'Чёрная',
    description: 'Чёрный металл, серебро и мягкий motion',
    swatches: ['#0a0b0d', '#c5ccd4', '#14171b']
  },
  {
    id: 'rose',
    label: 'Пыльная роза',
    description: 'Пыльная роза, Roboto Flex и текущий ритм',
    swatches: ['#8e3a74', '#f5e8d4', '#fffafc']
  }
]

export const themePreference = ref<ThemePreference>('system')
export const currentTheme = ref<ThemeId>(LIGHT_SYSTEM_THEME)

export const isSystemTheme = computed(() => themePreference.value === 'system')

function isThemeId(v: string | null | undefined): v is ThemeId {
  return !!v && THEMES.some(t => t.id === v)
}

function isThemePreference(v: string | null | undefined): v is ThemePreference {
  return v === 'system' || isThemeId(v)
}

export function prefersDarkScheme(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function resolveTheme(preference: ThemePreference): ThemeId {
  if (preference === 'system') {
    return prefersDarkScheme() ? DARK_SYSTEM_THEME : LIGHT_SYSTEM_THEME
  }
  return preference
}

export function readStoredPreference(): ThemePreference {
  if (typeof localStorage === 'undefined') return 'system'
  const raw = localStorage.getItem(STORAGE_KEY)
  if (isThemePreference(raw)) return raw
  return 'system'
}

function persistPreference(preference: ThemePreference) {
  if (typeof localStorage !== 'undefined') {
    localStorage.setItem(STORAGE_KEY, preference)
  }
}

function paintTheme(id: ThemeId) {
  currentTheme.value = id
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = id
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    const dark = id === 'ios-dark' || id === 'black'
    meta.setAttribute('content', dark ? '#000000' : id === 'ios-light' ? '#F2F2F7' : '#8e3a74')
  }
  document.documentElement.style.colorScheme = id === 'ios-dark' || id === 'black' ? 'dark' : 'light'
}

export function applyPreference(preference: ThemePreference) {
  themePreference.value = preference
  persistPreference(preference)
  paintTheme(resolveTheme(preference))
}

let mediaListenerBound = false

function bindSystemListener() {
  if (mediaListenerBound || typeof window === 'undefined' || !window.matchMedia) return
  mediaListenerBound = true
  const mq = window.matchMedia('(prefers-color-scheme: dark)')
  const onChange = () => {
    if (themePreference.value === 'system') {
      paintTheme(resolveTheme('system'))
    }
  }
  if (typeof mq.addEventListener === 'function') {
    mq.addEventListener('change', onChange)
  } else if (typeof mq.addListener === 'function') {
    mq.addListener(onChange)
  }
}

export function initTheme() {
  applyPreference(readStoredPreference())
  bindSystemListener()
}

export function setTheme(id: ThemeId) {
  applyPreference(id)
}

export function setSystemTheme() {
  applyPreference('system')
}
