import { shiftAdminMobilePage } from './adminUi'
import {
  casesMobileTab,
  setCasesMobileTab,
  type CasesMobileTab
} from './casesUi'
import {
  progressMobileTab,
  setProgressMobileTab,
  type ProgressMobileTab
} from './progressUi'
import { shiftSettingsMobilePage } from './settingsUi'
import {
  isMobileUi,
  sessionLeftPanel,
  type SessionLeftPanel
} from './sessionUi'

export { shiftAdminMobilePage, shiftSettingsMobilePage }

/** Chat mobile pages left → right (iOS home-screen order). */
export const CHAT_MOBILE_PAGES = ['clients', 'chat', 'notes', 'sessions'] as const
export type ChatMobilePage = (typeof CHAT_MOBILE_PAGES)[number]

export const chatNavItems: { id: ChatMobilePage; label: string; icon: string }[] = [
  { id: 'clients', label: 'Клиенты', icon: 'group' },
  { id: 'chat', label: 'Чат', icon: 'chat' },
  { id: 'notes', label: 'Заметки', icon: 'sticky_note_2' },
  { id: 'sessions', label: 'Сеансы', icon: 'history' }
]

export type MobileSwipeDir = 'left' | 'right' | null

const SWIPE_THRESHOLD_PX = 56
const SWIPE_RATIO = 1.35

export function chatPageFromPanel(panel: SessionLeftPanel): ChatMobilePage {
  if (panel === 'clients' || panel === 'notes' || panel === 'sessions') return panel
  return 'chat'
}

export function setChatMobilePage(page: ChatMobilePage) {
  sessionLeftPanel.value = page === 'chat' ? null : page
}

export function shiftChatMobilePage(delta: -1 | 1): boolean {
  // Clients picker: only the clients page exists.
  if (typeof window !== 'undefined' && window.location.pathname === '/clients') {
    return false
  }
  // Analysis is a special overlay — any horizontal swipe returns to chat.
  if (sessionLeftPanel.value === 'analysis') {
    sessionLeftPanel.value = null
    return true
  }
  const cur = chatPageFromPanel(sessionLeftPanel.value)
  const idx = CHAT_MOBILE_PAGES.indexOf(cur)
  const next = idx + delta
  if (next < 0 || next >= CHAT_MOBILE_PAGES.length) return false
  setChatMobilePage(CHAT_MOBILE_PAGES[next])
  return true
}

export function shiftCasesMobilePage(delta: -1 | 1): boolean {
  const order: CasesMobileTab[] = ['new', 'library']
  const idx = order.indexOf(casesMobileTab.value)
  const next = idx + delta
  if (next < 0 || next >= order.length) return false
  setCasesMobileTab(order[next])
  return true
}

export function shiftProgressMobilePage(delta: -1 | 1): boolean {
  // Left → right: Статистика, then Анализы (matches first-visit default).
  const order: ProgressMobileTab[] = ['stats', 'clients']
  const idx = order.indexOf(progressMobileTab.value)
  const next = idx + delta
  if (next < 0 || next >= order.length) return false
  setProgressMobileTab(order[next])
  return true
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) return false
  return !!target.closest(
    'input, textarea, select, option, [contenteditable="true"], .composer, .notes-composer, .chat-composer'
  )
}

export type MobileSwipeHandlers = {
  /** Finger moved left → go to next page (higher index). */
  onSwipeLeft: () => boolean
  /** Finger moved right → go to previous page. */
  onSwipeRight: () => boolean
  onDir?: (dir: MobileSwipeDir) => void
}

/**
 * iOS-like horizontal page swipe. Ignores mostly-vertical scrolls and form fields.
 * Returns cleanup.
 */
export function bindMobilePageSwipe(
  el: HTMLElement,
  handlers: MobileSwipeHandlers
): () => void {
  let startX = 0
  let startY = 0
  let tracking = false
  let locked: 'h' | 'v' | null = null
  let moved = false

  const onStart = (e: TouchEvent) => {
    if (!isMobileUi() || e.touches.length !== 1) return
    if (isInteractiveTarget(e.target)) return
    const t = e.touches[0]
    startX = t.clientX
    startY = t.clientY
    tracking = true
    locked = null
    moved = false
  }

  const onMove = (e: TouchEvent) => {
    if (!tracking || e.touches.length !== 1) return
    const t = e.touches[0]
    const dx = t.clientX - startX
    const dy = t.clientY - startY
    if (!locked) {
      if (Math.abs(dx) < 10 && Math.abs(dy) < 10) return
      locked = Math.abs(dx) > Math.abs(dy) * SWIPE_RATIO ? 'h' : 'v'
      if (locked === 'v') {
        tracking = false
        return
      }
    }
    if (locked === 'h') {
      moved = true
      // Prevent the page from scrolling sideways / pull-to-refresh fighting us.
      if (e.cancelable) e.preventDefault()
    }
  }

  const onEnd = (e: TouchEvent) => {
    if (!tracking) return
    tracking = false
    if (locked !== 'h' || !moved) {
      locked = null
      return
    }
    const t = e.changedTouches[0]
    const dx = t.clientX - startX
    locked = null
    if (Math.abs(dx) < SWIPE_THRESHOLD_PX) return

    if (dx < 0) {
      handlers.onDir?.('left')
      handlers.onSwipeLeft()
    } else {
      handlers.onDir?.('right')
      handlers.onSwipeRight()
    }
  }

  const onCancel = () => {
    tracking = false
    locked = null
    moved = false
  }

  el.addEventListener('touchstart', onStart, { passive: true })
  el.addEventListener('touchmove', onMove, { passive: false })
  el.addEventListener('touchend', onEnd, { passive: true })
  el.addEventListener('touchcancel', onCancel, { passive: true })

  return () => {
    el.removeEventListener('touchstart', onStart)
    el.removeEventListener('touchmove', onMove)
    el.removeEventListener('touchend', onEnd)
    el.removeEventListener('touchcancel', onCancel)
  }
}
