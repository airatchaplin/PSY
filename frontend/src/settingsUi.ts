import { ref, watch } from 'vue'

export type SettingsSection = 'profile' | 'themes' | 'topups' | 'usage'

const STORAGE_KEY = 'psy.settingsSection'

export const SETTINGS_SECTIONS: SettingsSection[] = [
  'profile',
  'themes',
  'topups',
  'usage'
]

function readStored(): SettingsSection {
  if (typeof sessionStorage === 'undefined') return 'profile'
  const v = sessionStorage.getItem(STORAGE_KEY)
  return SETTINGS_SECTIONS.includes(v as SettingsSection) ? (v as SettingsSection) : 'profile'
}

export const settingsSection = ref<SettingsSection>(readStored())

watch(settingsSection, section => {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, section)
})

export function setSettingsSection(section: SettingsSection) {
  settingsSection.value = section
}

/** Mobile swipe: -1 previous, +1 next. Returns false at edges. */
export function shiftSettingsMobilePage(delta: -1 | 1): boolean {
  const idx = SETTINGS_SECTIONS.indexOf(settingsSection.value)
  const next = idx + delta
  if (next < 0 || next >= SETTINGS_SECTIONS.length) return false
  settingsSection.value = SETTINGS_SECTIONS[next]
  return true
}

export const settingsNavItems: { id: SettingsSection; label: string; icon: string }[] = [
  { id: 'profile', label: 'Профиль', icon: 'person' },
  { id: 'themes', label: 'Темы', icon: 'palette' },
  { id: 'topups', label: 'Пополнения', icon: 'add_card' },
  { id: 'usage', label: 'Использование', icon: 'monitoring' }
]
