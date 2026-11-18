import { ref, watch } from 'vue'

export type AdminSection = 'connection' | 'balance' | 'models' | 'usage' | 'prompts' | 'users'

const STORAGE_KEY = 'psy.adminSection'

const SECTIONS: AdminSection[] = ['connection', 'balance', 'models', 'usage', 'prompts', 'users']

function readStored(): AdminSection {
  if (typeof sessionStorage === 'undefined') return 'connection'
  const v = sessionStorage.getItem(STORAGE_KEY)
  return SECTIONS.includes(v as AdminSection) ? (v as AdminSection) : 'connection'
}

export const adminSection = ref<AdminSection>(readStored())

watch(adminSection, section => {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, section)
})

export function setAdminSection(section: AdminSection) {
  adminSection.value = section
}

export const adminNavItems: { id: AdminSection; label: string; icon: string }[] = [
  { id: 'connection', label: 'Подключение', icon: 'link' },
  { id: 'balance', label: 'Баланс', icon: 'account_balance_wallet' },
  { id: 'models', label: 'Модели', icon: 'psychology' },
  { id: 'usage', label: 'Использование', icon: 'monitoring' },
  { id: 'prompts', label: 'Промпты', icon: 'description' },
  { id: 'users', label: 'Пользователи', icon: 'group' }
]
