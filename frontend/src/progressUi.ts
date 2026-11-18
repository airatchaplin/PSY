import { ref, watch } from 'vue'

/** Which progress side tab is shown on mobile (Клиенты / Статистика). */
export type ProgressMobileTab = 'clients' | 'stats'

const STORAGE_KEY = 'psy.progressMobileTab'

function readStoredTab(): ProgressMobileTab {
  if (typeof sessionStorage === 'undefined') return 'clients'
  const v = sessionStorage.getItem(STORAGE_KEY)
  return v === 'stats' ? 'stats' : 'clients'
}

export const progressMobileTab = ref<ProgressMobileTab>(readStoredTab())

watch(progressMobileTab, tab => {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, tab)
})

export function setProgressMobileTab(tab: ProgressMobileTab) {
  progressMobileTab.value = tab
}
