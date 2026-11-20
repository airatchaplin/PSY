import { computed, ref, watch } from 'vue'

export type SessionLeftPanel = 'clients' | 'notes' | 'sessions' | 'analysis' | null

/** Side tabs that should be remembered across chat ↔ other bookmarks. */
type PreferredLeftPanel = 'clients' | 'notes' | 'sessions'

const STORAGE_KEY = 'psy.sessionLeftPanel'
const MOBILE_MQ = '(max-width: 900px)'

function readStoredPanel(): PreferredLeftPanel | null {
  if (typeof sessionStorage === 'undefined') return null
  const v = sessionStorage.getItem(STORAGE_KEY)
  if (v === 'clients' || v === 'notes' || v === 'sessions') return v
  return null
}

function writeStoredPanel(panel: PreferredLeftPanel) {
  if (typeof sessionStorage === 'undefined') return
  sessionStorage.setItem(STORAGE_KEY, panel)
}

/** Which left panel is open in chat session (null = chat on full spread). */
export const sessionLeftPanel = ref<SessionLeftPanel>(readStoredPanel())

/** Skip one persist when opening a panel only as a temporary UI state. */
let skipPersistOnce = false

/** Next mobile chat enter should open a side page (e.g. Home → Чат → Клиенты). */
let openPanelOnNextChatEnter: PreferredLeftPanel | null = null

export function requestChatClientsOnEnter() {
  openPanelOnNextChatEnter = 'clients'
}

export function requestChatPanelOnEnter(panel: PreferredLeftPanel) {
  openPanelOnNextChatEnter = panel
}

/** Closed session currently under review (null = live / all history). */
export const reviewingSessionId = ref<string | null>(null)

const PENDING_REVIEW_KEY = 'psy.pendingSessionReview'

export type PendingSessionReview = { sessionId: string; openAnalysis: boolean }

/** Pending deep-link from Usage — sessionStorage so App route watches cannot wipe it. */
export function requestSessionReview(sessionId: string, openAnalysis = false) {
  if (!sessionId) return
  const payload: PendingSessionReview = { sessionId, openAnalysis }
  try {
    sessionStorage.setItem(PENDING_REVIEW_KEY, JSON.stringify(payload))
  } catch {
    /* ignore quota */
  }
}

export function peekPendingSessionReview(): PendingSessionReview | null {
  try {
    const raw = sessionStorage.getItem(PENDING_REVIEW_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as PendingSessionReview
    if (!parsed?.sessionId) return null
    return {
      sessionId: String(parsed.sessionId),
      openAnalysis: !!parsed.openAnalysis
    }
  } catch {
    return null
  }
}

export function takePendingSessionReview(): PendingSessionReview | null {
  const pending = peekPendingSessionReview()
  clearPendingSessionReview()
  return pending
}

export function clearPendingSessionReview() {
  try {
    sessionStorage.removeItem(PENDING_REVIEW_KEY)
  } catch {
    /* ignore */
  }
}

/** Live timer pause for the open chat client — keeps Клиенты badges in sync. */
export const liveTimerClientId = ref<string | null>(null)
export const liveTimerPaused = ref(false)

export function setLiveTimerState(clientId: string | null, paused: boolean) {
  liveTimerClientId.value = clientId
  liveTimerPaused.value = paused
}

export function clearLiveTimerState() {
  liveTimerClientId.value = null
  liveTimerPaused.value = false
}

export const notesPanelOpen = computed(() => sessionLeftPanel.value === 'notes')
export const clientsPanelOpen = computed(() => sessionLeftPanel.value === 'clients')
export const sessionsPanelOpen = computed(() => sessionLeftPanel.value === 'sessions')
/** Auto-opened when reviewing a closed session that already has analysis. */
export const analysisPanelOpen = computed(() => sessionLeftPanel.value === 'analysis')
export const anyLeftPanelOpen = computed(() => sessionLeftPanel.value !== null)

watch(
  sessionLeftPanel,
  panel => {
    if (skipPersistOnce) {
      skipPersistOnce = false
      return
    }
    // Remember only real side tabs. Closing (null) or analysis must not wipe preference.
    if (panel === 'clients' || panel === 'notes' || panel === 'sessions') {
      writeStoredPanel(panel)
    }
  },
  { flush: 'sync' }
)

export function isMobileUi() {
  return typeof window !== 'undefined' && window.matchMedia(MOBILE_MQ).matches
}

function preferredLeftPanel(): PreferredLeftPanel {
  return readStoredPanel() ?? 'clients'
}

function ensureDesktopLeftPanel() {
  sessionLeftPanel.value = preferredLeftPanel()
}

export function toggleSessionLeftPanel(panel: Exclude<SessionLeftPanel, null>) {
  sessionLeftPanel.value = sessionLeftPanel.value === panel ? null : panel
}

export function openClientsPanel(options?: { persist?: boolean }) {
  if (options?.persist === false) skipPersistOnce = true
  sessionLeftPanel.value = 'clients'
  // No watch if value unchanged — don't leave the skip flag stuck.
  if (skipPersistOnce) skipPersistOnce = false
}

export function openSessionsPanel() {
  sessionLeftPanel.value = 'sessions'
}

export function openAnalysisPanel() {
  sessionLeftPanel.value = 'analysis'
}

export function closeLeftPanel() {
  sessionLeftPanel.value = null
}

/**
 * When false, left-panel Vue transitions are skipped (pick client → chat).
 * Restored on the next microtask so later open/close still animates.
 */
export const panelTransitionEnabled = ref(true)

/** After picking a client on mobile — close drawer so chat is visible. */
export function closeLeftPanelOnMobile(options?: { instant?: boolean }) {
  if (!isMobileUi()) return
  if (options?.instant) {
    panelTransitionEnabled.value = false
    sessionLeftPanel.value = null
    queueMicrotask(() => {
      panelTransitionEnabled.value = true
    })
    return
  }
  sessionLeftPanel.value = null
}

/** Leave chat: keep left-tab memory; only clear review focus. */
export function resetSessionUi() {
  reviewingSessionId.value = null
  clearPendingSessionReview()
  clearLiveTimerState()
}

/**
 * Enter chat:
 * - mobile: close drawer so the conversation is visible (preference kept),
 *   unless Home→Чат asked for the Клиенты page
 * - desktop: restore last left tab (or open Клиенты)
 */
export function openChatSessionUi() {
  // Do not clear reviewingSessionId here — App.vue calls this on route enter and would
  // wipe a Usage deep-link / session review that ChatView just applied.
  // Also do not steal the analysis panel after a Usage deep-link opened it.
  const pending = peekPendingSessionReview()
  if (pending?.openAnalysis || (sessionLeftPanel.value === 'analysis' && reviewingSessionId.value)) {
    openPanelOnNextChatEnter = null
    if (pending?.sessionId) reviewingSessionId.value = pending.sessionId
    if (pending?.openAnalysis) openAnalysisPanel()
    return
  }
  if (pending?.sessionId) {
    // Session review without analysis: keep chat visible (mobile) / sessions (desktop).
    reviewingSessionId.value = pending.sessionId
    openPanelOnNextChatEnter = null
    if (isMobileUi()) {
      closeLeftPanelOnMobile({ instant: true })
      return
    }
    openSessionsPanel()
    return
  }
  if (isMobileUi()) {
    if (openPanelOnNextChatEnter) {
      sessionLeftPanel.value = openPanelOnNextChatEnter
      openPanelOnNextChatEnter = null
      return
    }
    // Instant close — panel leave + chat mount on the same frame felt like micro-lag.
    closeLeftPanelOnMobile({ instant: true })
    return
  }
  openPanelOnNextChatEnter = null
  ensureDesktopLeftPanel()
}

/** Keep chat layout sane when crossing the mobile/desktop breakpoint. */
export function syncSessionUiForViewport() {
  if (isMobileUi()) {
    // Drawer must not cover the conversation after a desktop→mobile switch.
    sessionLeftPanel.value = null
    return
  }
  ensureDesktopLeftPanel()
}

let viewportMq: MediaQueryList | null = null
let viewportListener: (() => void) | null = null

export function startSessionUiViewportWatch() {
  if (typeof window === 'undefined' || viewportMq) return
  viewportMq = window.matchMedia(MOBILE_MQ)
  viewportListener = () => syncSessionUiForViewport()
  viewportMq.addEventListener('change', viewportListener)
  syncSessionUiForViewport()
}

export function stopSessionUiViewportWatch() {
  if (viewportMq && viewportListener) {
    viewportMq.removeEventListener('change', viewportListener)
  }
  viewportMq = null
  viewportListener = null
}
