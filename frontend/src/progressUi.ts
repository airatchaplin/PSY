import { ref, watch } from 'vue'

/** Which progress side tab is shown on mobile (Статистика / Анализы). */
export type ProgressMobileTab = 'clients' | 'stats'

const STORAGE_KEY = 'psy.progressMobileTab'

function readStoredTab(): ProgressMobileTab {
  if (typeof sessionStorage === 'undefined') return 'stats'
  const v = sessionStorage.getItem(STORAGE_KEY)
  if (v === 'clients' || v === 'stats') return v
  // First visit: show the progress/stats page.
  return 'stats'
}

export const progressMobileTab = ref<ProgressMobileTab>(readStoredTab())

watch(progressMobileTab, tab => {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, tab)
})

export function setProgressMobileTab(tab: ProgressMobileTab) {
  progressMobileTab.value = tab
}
