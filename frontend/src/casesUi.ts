import { ref, watch } from 'vue'

export const casesVersion = ref(0)

/** Banner after importing a case from a share link. */
export const casesShareNotice = ref('')

/** Which cases side tab is shown (Новый / Библиотека). */
export type CasesMobileTab = 'new' | 'library'

const STORAGE_KEY = 'psy.casesMobileTab'
const PENDING_SHARE_KEY = 'psy.pendingShareToken'

function readStoredTab(): CasesMobileTab {
  if (typeof sessionStorage === 'undefined') return 'new'
  const v = sessionStorage.getItem(STORAGE_KEY)
  return v === 'library' ? 'library' : 'new'
}

export const casesMobileTab = ref<CasesMobileTab>(readStoredTab())

watch(casesMobileTab, tab => {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, tab)
})

export function bumpCasesVersion() {
  casesVersion.value += 1
}

export function setCasesMobileTab(tab: CasesMobileTab) {
  casesMobileTab.value = tab
}

export function resetCasesMobileTab() {
  casesMobileTab.value = 'new'
}

export function stashPendingShareToken(token: string) {
  if (typeof sessionStorage === 'undefined') return
  const t = token.trim()
  if (!t) return
  sessionStorage.setItem(PENDING_SHARE_KEY, t)
}

export function takePendingShareToken(): string | null {
  if (typeof sessionStorage === 'undefined') return null
  const t = sessionStorage.getItem(PENDING_SHARE_KEY)
  sessionStorage.removeItem(PENDING_SHARE_KEY)
  return t && t.trim() ? t.trim() : null
}

export function peekShareTokenFromPath(path: string): string | null {
  const m = path.match(/^\/cases\/shared\/([^/?#]+)/)
  if (!m) return null
  try {
    return decodeURIComponent(m[1])
  } catch {
    return m[1]
  }
}
