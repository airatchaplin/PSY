<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api/client'
import { authenticated, isAdmin } from './auth/session'
import AiWorkingBlock from './components/AiWorkingBlock.vue'
import LoginView from './views/LoginView.vue'
import { apiErrorMessage, INSUFFICIENT_FUNDS_MSG } from './api/errors'
import {
  appBalanceRub,
  balanceIsNegative,
  balanceIsZero,
  balanceRub,
  formatBalanceRub,
  llmAllowed,
  meProfile,
  setMeProfile
} from './meProfile'
import {
  bumpCasesVersion,
  casesMobileTab,
  casesShareNotice,
  peekShareTokenFromPath,
  setCasesMobileTab,
  stashPendingShareToken,
  takePendingShareToken
} from './casesUi'
import { adminSection } from './adminUi'
import { settingsSection } from './settingsUi'
import { progressMobileTab, setProgressMobileTab } from './progressUi'
import { tagCode, tagLabel } from './labels'
import {
  closeHubMenu,
  hubMenuActiveId,
  hubMenuAriaLabel,
  hubMenuItems,
  hubMenuOpen,
  openHubMenu,
  pickHubMenuItem,
  setChatPageSelect
} from './hubMenu'
import {
  bindMobilePageSwipe,
  chatPageFromPanel,
  setChatMobilePage,
  shiftAdminMobilePage,
  shiftCasesMobilePage,
  shiftChatMobilePage,
  shiftProgressMobilePage,
  shiftSettingsMobilePage,
  type ChatMobilePage,
  type MobileSwipeDir
} from './mobilePager'
import {
  anyLeftPanelOpen,
  clearLiveTimerState,
  clientsPanelOpen,
  closeLeftPanelOnMobile,
  isMobileUi,
  liveTimerClientId,
  liveTimerPaused,
  notesPanelOpen,
  openAnalysisPanel,
  openChatSessionUi,
  openClientsPanel,
  panelTransitionEnabled,
  peekPendingSessionReview,
  requestChatPanelOnEnter,
  resetSessionUi,
  reviewingSessionId,
  sessionLeftPanel,
  sessionsPanelOpen,
  startSessionUiViewportWatch,
  stopSessionUiViewportWatch,
  toggleSessionLeftPanel
} from './sessionUi'
import type { Client, Difficulty, GenderPreference, Me } from './types'

type BookmarkId = 'home' | 'clients' | 'cases' | 'progress' | 'settings' | 'admin'

interface BookmarkItem {
  id: BookmarkId
  label: string
  shortLabel?: string
  icon: string
  to: string
  tone: string
  page: number
}

const me = ref<Me | null>(null)
const loading = ref(true)
const error = ref('')
const clients = ref<Client[]>([])
const query = ref('')
const starting = ref(false)
const difficulty = ref<Difficulty>('MEDIUM')
const receptionGender = ref<GenderPreference>('ANY') // Неважно справа, по умолчанию
const receptionAgeAny = ref(true)
const receptionAge = ref(30)
const newReceptionOpen = ref(false)
const route = useRoute()
const router = useRouter()
const lastClientId = ref<string | null>(sessionStorage.getItem('psy.lastClientId'))

const caseMode = ref<'manual' | 'prompt'>('manual')
const caseBusy = ref(false)
const caseError = ref('')
const caseTitle = ref('')
const caseRequest = ref('')
const caseNotes = ref('')
const caseDifficulty = ref<Difficulty>('MEDIUM')
const casePrompt = ref('')

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase()
  if (!q) return clients.value
  return clients.value.filter(c =>
    c.name.toLowerCase().includes(q) || c.presentingRequest.toLowerCase().includes(q)
  )
})

function bookmarkFromPath(path: string): BookmarkId {
  if (path.startsWith('/cases')) return 'cases'
  if (path.startsWith('/progress')) return 'progress'
  if (path.startsWith('/settings')) return 'settings'
  if (path.startsWith('/admin')) return 'admin'
  if (path.startsWith('/clients')) return 'clients'
  return 'home'
}

type LayoutMode = BookmarkId | 'chat'

function layoutModeFromRoute(): LayoutMode {
  if (route.name === 'chat') return 'chat'
  return bookmarkFromPath(route.path)
}

const activeBookmark = computed<BookmarkId>(() => bookmarkFromPath(route.path))

/**
 * Notebook layout mode. On mobile it updates after the leaving page is gone,
 * so progress/cases panes and chat session-mode don't flash mid-transition.
 * Initial load / refresh must sync immediately (no previous page to leave).
 */
const layoutMode = ref<LayoutMode>(layoutModeFromRoute())
const routeAnimating = ref(false)
/** Skip deferred mobile layout on the first route sync after mount/refresh. */
let mobileLayoutReady = false
/** Softer page transition inside chat family (clients list ↔ conversation). */
const softPageTransition = ref(false)

function isChatFamilyMode(mode: LayoutMode) {
  return mode === 'chat' || mode === 'clients'
}

watch(
  () => route.fullPath,
  () => {
    const next = layoutModeFromRoute()
    const withinChat = isChatFamilyMode(layoutMode.value) && isChatFamilyMode(next)
    softPageTransition.value = withinChat

    // clients ↔ chat stays in the same chrome — no veil / deferred layout swap.
    if (!isMobileUi() || !mobileLayoutReady || withinChat) {
      layoutMode.value = next
      routeAnimating.value = false
      mobileLayoutReady = true
      return
    }
    routeAnimating.value = true
  },
  { flush: 'sync' }
)

function onRouteAfterLeave() {
  if (!isMobileUi()) return
  // Apply notebook layout before the entering page mounts (Teleport targets + session-mode).
  layoutMode.value = layoutModeFromRoute()
}

function onRouteAfterEnter() {
  layoutMode.value = layoutModeFromRoute()
  routeAnimating.value = false
  mobileLayoutReady = true
}

const isCasesPage = computed(() => layoutMode.value === 'cases')
const isHomePage = computed(() => layoutMode.value === 'home')
const isProgressPage = computed(() => layoutMode.value === 'progress')
const isSettingsPage = computed(() => layoutMode.value === 'settings')
const isAdminPage = computed(() => layoutMode.value === 'admin')
const isClientsPicker = computed(() => layoutMode.value === 'clients')
const isChatSession = computed(() => layoutMode.value === 'chat')
/** Chat route or empty client picker — same left tabs (+ новый приём). */
const isChatLayout = computed(() => isChatSession.value || isClientsPicker.value)

function enterChatLayout() {
  if (route.name === 'clients') {
    // Picker has no ChatView teleports; keep Клиенты open so «+» is always reachable.
    // Don't persist — otherwise leaving chat via home→picker overwrites Сеансы/Заметки.
    openClientsPanel({ persist: false })
    return
  }
  // Usage → session/analysis: prime panel before ChatView mounts to avoid flicker.
  const pending = peekPendingSessionReview()
  if (pending?.sessionId) {
    reviewingSessionId.value = pending.sessionId
    panelTransitionEnabled.value = false
    if (pending.openAnalysis) {
      openAnalysisPanel()
    }
    queueMicrotask(() => {
      panelTransitionEnabled.value = true
    })
    return
  }
  openChatSessionUi()
}

/** Чат bookmark always opens Клиенты (search / + / list). */
const clientsLink = computed(() => '/clients')

const allBookmarks = computed<BookmarkItem[]>(() => {
  const items: BookmarkItem[] = [
    { id: 'home', label: 'Главная', shortLabel: 'Дом', icon: 'home', to: '/', tone: 'bookmark-rose', page: 0 },
    { id: 'clients', label: 'Чат', shortLabel: 'Чат', icon: 'chat', to: clientsLink.value, tone: 'bookmark-pink', page: 1 },
    { id: 'cases', label: 'Кейсы', shortLabel: 'Кейсы', icon: 'menu_book', to: '/cases', tone: 'bookmark-yellow', page: 2 },
    { id: 'progress', label: 'Прогресс', shortLabel: 'Прогресс', icon: 'monitoring', to: '/progress', tone: 'bookmark-lilac', page: 3 },
    { id: 'settings', label: 'Профиль', shortLabel: 'Профиль', icon: 'person', to: '/settings', tone: 'bookmark-profile', page: 4 }
  ]
  // Always show for JWT role ADMIN, not only after /api/me.
  if (isAdmin.value || me.value?.admin) {
    items.push({ id: 'admin', label: 'Админ', shortLabel: 'Админ', icon: 'admin_panel_settings', to: '/admin', tone: 'bookmark-admin', page: 5 })
  }
  return items
})

async function loadMe() {
  me.value = await api.me()
  setMeProfile(me.value)
}

/** Refresh balance / LLM gate without blocking the UI. */
async function refreshBalanceQuiet() {
  if (!authenticated.value) return
  try {
    await loadMe()
  } catch {
    /* ignore */
  }
}

/** Prefer shared profile (updated after LLM) for balance display. */
const displayBalance = computed(() => meProfile.value?.balanceRub ?? me.value?.balanceRub ?? balanceRub.value)

function setLastClientId(id: string | null) {
  lastClientId.value = id
  if (id) sessionStorage.setItem('psy.lastClientId', id)
  else sessionStorage.removeItem('psy.lastClientId')
}

function pickValidClientId(list: Client[], preferred: string | null | undefined): string | null {
  if (preferred && list.some(c => c.id === preferred)) return preferred
  return list[0]?.id ?? null
}

async function ensureChatRouteMatchesClients() {
  if (route.name !== 'chat') return
  const routeId = typeof route.params.id === 'string' ? route.params.id : null
  if (routeId && clients.value.some(c => c.id === routeId)) {
    setLastClientId(routeId)
    return
  }
  const fallback = pickValidClientId(clients.value, lastClientId.value)
  setLastClientId(fallback)
  await router.replace(fallback ? `/clients/${fallback}` : '/clients')
}

async function loadClients(opts?: { soft?: boolean }) {
  try {
    const list = await api.clients()
    clients.value = Array.isArray(list) ? list : []
    const resolved = pickValidClientId(clients.value, lastClientId.value)
    if (lastClientId.value !== resolved) setLastClientId(resolved)
    await ensureChatRouteMatchesClients()
  } catch (e) {
    // Soft refresh: keep existing list if a background reload fails (chat still works via its own API).
    if (opts?.soft && clients.value.length) return
    throw e
  }
}

function toggleNewReception() {
  newReceptionOpen.value = !newReceptionOpen.value
  if (newReceptionOpen.value) {
    receptionGender.value = 'ANY'
    receptionAgeAny.value = true
    receptionAge.value = 30
    difficulty.value = 'MEDIUM'
  }
}

function isClientSessionPaused(c: Client) {
  if (!c.activeSessionId) return false
  if (c.id === liveTimerClientId.value) return liveTimerPaused.value
  return !!c.activeSessionTimerPaused
}

function therapyProgressLabel(value: unknown) {
  const n = typeof value === 'number' ? value : Number(value)
  return `${Number.isFinite(n) ? Math.round(n) : 0}%`
}

function clampReceptionAge() {
  if (receptionAge.value < 18) receptionAge.value = 18
  if (receptionAge.value > 80) receptionAge.value = 80
}

async function startReception() {
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  starting.value = true
  error.value = ''
  try {
    if (!receptionAgeAny.value) clampReceptionAge()
    const reception = await api.startReception({
      difficulty: difficulty.value,
      gender: receptionGender.value,
      age: receptionAgeAny.value ? null : receptionAge.value
    })
    setLastClientId(reception.client.id)
    newReceptionOpen.value = false
    await loadClients()
    await loadMe()
    closeLeftPanelOnMobile({ instant: true })
    await router.push(`/clients/${reception.client.id}`)
  } catch (e) {
    error.value = apiErrorMessage(e)
    await loadMe().catch(() => undefined)
  } finally {
    starting.value = false
  }
}

function resetCaseForm() {
  caseTitle.value = ''
  caseRequest.value = ''
  caseNotes.value = ''
  caseDifficulty.value = 'MEDIUM'
  casePrompt.value = ''
  caseError.value = ''
}

async function createCaseManual() {
  if (!caseTitle.value.trim() || !caseRequest.value.trim()) {
    caseError.value = 'Укажите название и запрос.'
    return
  }
  caseBusy.value = true
  caseError.value = ''
  try {
    await api.createCase({
      title: caseTitle.value.trim(),
      presentingRequest: caseRequest.value.trim(),
      difficulty: caseDifficulty.value,
      notes: caseNotes.value.trim() || undefined
    })
    resetCaseForm()
    bumpCasesVersion()
  } catch (e) {
    caseError.value = String(e)
  } finally {
    caseBusy.value = false
  }
}

async function generateCaseFromPrompt() {
  if (!llmAllowed.value) {
    caseError.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  if (!casePrompt.value.trim()) {
    caseError.value = 'Опишите, какой кейс нужен.'
    return
  }
  caseBusy.value = true
  caseError.value = ''
  try {
    await api.generateCase(casePrompt.value.trim())
    casePrompt.value = ''
    bumpCasesVersion()
    await loadMe()
  } catch (e) {
    caseError.value = apiErrorMessage(e)
    await loadMe().catch(() => undefined)
  } finally {
    caseBusy.value = false
  }
}

async function generateCaseAuto() {
  if (!llmAllowed.value) {
    caseError.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  caseBusy.value = true
  caseError.value = ''
  try {
    await api.generateCase()
    bumpCasesVersion()
    await loadMe()
  } catch (e) {
    caseError.value = apiErrorMessage(e)
    await loadMe().catch(() => undefined)
  } finally {
    caseBusy.value = false
  }
}

async function consumePendingShareLink() {
  const fromPath = peekShareTokenFromPath(route.path)
  if (fromPath) stashPendingShareToken(fromPath)
  const token = takePendingShareToken()
  if (!token) return
  try {
    const result = await api.importSharedCase(token)
    bumpCasesVersion()
    setCasesMobileTab('library')
    casesShareNotice.value = result.alreadyHad
      ? `Кейс «${result.caseItem.title}» уже есть в вашей библиотеке`
      : `Кейс «${result.caseItem.title}» добавлен в библиотеку`
    await router.replace('/cases')
  } catch (e) {
    casesShareNotice.value = e instanceof Error ? e.message : String(e)
    await router.replace('/cases')
  }
}

async function bootstrapSession() {
  loading.value = true
  error.value = ''
  try {
    await loadMe()
    if (me.value && !me.value.disclaimerAccepted) {
      me.value = await api.acceptDisclaimer()
    }
    await loadClients()
    await consumePendingShareLink()
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
  if (route.name === 'chat' || route.name === 'clients') {
    enterChatLayout()
  }
}

async function onLoginSuccess() {
  await bootstrapSession()
  bindNotebookSwipe()
}

const notebookEl = ref<HTMLElement | null>(null)
const mobileSwipeDir = ref<MobileSwipeDir>(null)
const mobileUi = ref(isMobileUi())
let unbindMobileSwipe: (() => void) | null = null
let unbindMobileMq: (() => void) | null = null

const chatMobilePage = computed<ChatMobilePage>(() => chatPageFromPanel(sessionLeftPanel.value))

/** Mobile: hide main tab bar inside chat / notes / sessions (keep on clients list). */
const hideMobileBottomBar = computed(() => {
  if (!mobileUi.value || !isChatLayout.value) return false
  if (isClientsPicker.value) return false
  const page = chatMobilePage.value
  return page === 'chat' || page === 'notes' || page === 'sessions' || sessionLeftPanel.value === 'analysis'
})

function selectChatMobilePage(id: string) {
  const page = id as ChatMobilePage
  if (!isClientsPicker.value) {
    setChatMobilePage(page)
    return
  }
  if (page === 'clients') {
    openClientsPanel({ persist: false })
    return
  }
  const target = lastClientId.value || clients.value[0]?.id
  if (!target) return
  if (page === 'chat') {
    router.push(`/clients/${target}`)
    return
  }
  requestChatPanelOnEnter(page)
  router.push(`/clients/${target}`)
}

function flashSwipeDir(dir: MobileSwipeDir) {
  mobileSwipeDir.value = dir
  window.setTimeout(() => {
    if (mobileSwipeDir.value === dir) mobileSwipeDir.value = null
  }, 320)
}

function onMobileSwipeLeft() {
  if (isChatLayout.value) return shiftChatMobilePage(1)
  if (isCasesPage.value) return shiftCasesMobilePage(1)
  if (isProgressPage.value) return shiftProgressMobilePage(1)
  if (isAdminPage.value) return shiftAdminMobilePage(1)
  if (isSettingsPage.value) return shiftSettingsMobilePage(1)
  return false
}

function onMobileSwipeRight() {
  if (isChatLayout.value) return shiftChatMobilePage(-1)
  if (isCasesPage.value) return shiftCasesMobilePage(-1)
  if (isProgressPage.value) return shiftProgressMobilePage(-1)
  if (isAdminPage.value) return shiftAdminMobilePage(-1)
  if (isSettingsPage.value) return shiftSettingsMobilePage(-1)
  return false
}

function bindNotebookSwipe() {
  unbindMobileSwipe?.()
  unbindMobileSwipe = null
  const el = notebookEl.value
  if (!el) return
  unbindMobileSwipe = bindMobilePageSwipe(el, {
    onSwipeLeft: () => {
      const ok = onMobileSwipeLeft()
      if (ok) flashSwipeDir('left')
      return ok
    },
    onSwipeRight: () => {
      const ok = onMobileSwipeRight()
      if (ok) flashSwipeDir('right')
      return ok
    }
  })
}

onMounted(async () => {
  setChatPageSelect(selectChatMobilePage)
  // Hard refresh: align notebook chrome to the real URL before children Teleport.
  layoutMode.value = layoutModeFromRoute()
  routeAnimating.value = false
  mobileLayoutReady = true

  startSessionUiViewportWatch()
  const mq = window.matchMedia('(max-width: 900px)')
  const syncMq = () => { mobileUi.value = mq.matches }
  syncMq()
  mq.addEventListener('change', syncMq)
  unbindMobileMq = () => mq.removeEventListener('change', syncMq)

  const shareToken = peekShareTokenFromPath(window.location.pathname)
  if (shareToken) stashPendingShareToken(shareToken)
  if (!authenticated.value) {
    loading.value = false
    return
  }
  await bootstrapSession()
  bindNotebookSwipe()
})

onUnmounted(() => {
  stopSessionUiViewportWatch()
  unbindMobileSwipe?.()
  unbindMobileSwipe = null
  unbindMobileMq?.()
  unbindMobileMq = null
})

watch(notebookEl, () => bindNotebookSwipe())

watch(
  () => route.name,
  async (name, prev) => {
    const chatLayout = name === 'chat' || name === 'clients'
    const prevChatLayout = prev === 'chat' || prev === 'clients'
    if (chatLayout && !prevChatLayout) {
      enterChatLayout()
      try { await loadClients({ soft: true }) } catch { /* ignore */ }
    } else if (chatLayout && name !== prev) {
      // clients ↔ chat: layout only — ChatView.loadAll fetches the conversation.
      enterChatLayout()
    } else if (!chatLayout) {
      // Keep a just-queued Usage deep-link (sessionStorage) when leaving chat → settings/etc.
      // Actually clear review UI state, but do not drop a pending review set for the next enter.
      if (peekPendingSessionReview()) {
        reviewingSessionId.value = null
        clearLiveTimerState()
      } else {
        resetSessionUi()
      }
      newReceptionOpen.value = false
    }
  }
)

watch(
  () => route.fullPath,
  async (path, prevPath) => {
    const routeId = typeof route.params.id === 'string' ? route.params.id : null
    // Remember only IDs that belong to the loaded client list (avoid stale cross-user IDs).
    if (routeId && clients.value.some(c => c.id === routeId)) {
      setLastClientId(routeId)
    }
    // Skip list reload when staying inside /clients — avoids jank opening a chat.
    const withinClients =
      path.startsWith('/clients') && typeof prevPath === 'string' && prevPath.startsWith('/clients')
    if (me.value && !withinClients) {
      try { await loadClients({ soft: true }) } catch { /* ignore */ }
    }
  }
)

watch(clientsPanelOpen, async open => {
  if (!open) return
  // Stale search can empty the session clients list while data exists.
  if (clients.value.length && !filtered.value.length) {
    query.value = ''
  }
  try { await loadClients({ soft: true }) } catch { /* ignore */ }
})

// Balance gate: re-check on every main / side tab change (not only full page reload).
watch(activeBookmark, (id, prev) => {
  if (id === prev) return
  void refreshBalanceQuiet()
})
watch(progressMobileTab, (tab, prev) => {
  if (tab === prev) return
  void refreshBalanceQuiet()
})
watch(casesMobileTab, (tab, prev) => {
  if (tab === prev) return
  void refreshBalanceQuiet()
})
watch(settingsSection, (section, prev) => {
  if (section === prev) return
  void refreshBalanceQuiet()
})
watch(adminSection, (section, prev) => {
  if (section === prev) return
  void refreshBalanceQuiet()
})
watch(sessionLeftPanel, (panel, prev) => {
  if (panel === prev) return
  void refreshBalanceQuiet()
})
</script>

<template>
  <div class="app-shell notebook-shell">
    <div v-if="!authenticated" class="notebook-stage auth-stage">
      <LoginView @success="onLoginSuccess" />
    </div>

    <div v-else-if="loading" class="notebook-stage">
      <div class="notebook single"><div class="page-right">Загрузка тетради…</div></div>
    </div>

    <div
      v-else
      class="notebook-stage"
      :class="{
        'mobile-tab-bar-hidden': hideMobileBottomBar,
        'has-balance-banner': !llmAllowed
      }"
    >
      <div
        class="bookmarks-bar"
        aria-label="Разделы тетради"
        :class="{ 'is-hidden': hideMobileBottomBar }"
        :aria-hidden="hideMobileBottomBar ? 'true' : undefined"
      >
        <nav class="bookmarks bookmarks-right" aria-label="Разделы">
          <RouterLink
            v-for="b in allBookmarks"
            :key="b.id"
            :to="b.to"
            class="bookmark"
            :class="[b.tone, { active: activeBookmark === b.id }]"
            :tabindex="hideMobileBottomBar ? -1 : undefined"
          >
            <span class="material-symbols-outlined bookmark-icon" aria-hidden="true">{{ b.icon }}</span>
            <span class="bookmark-label label-full">{{ b.label }}</span>
            <span class="bookmark-label label-short">{{ b.shortLabel || b.label }}</span>
          </RouterLink>
        </nav>
      </div>

      <div
        v-if="!llmAllowed"
        class="balance-block-banner balance-block-banner-global"
        :class="balanceIsZero ? 'is-warning' : 'is-danger'"
        role="status"
      >
        <span class="material-symbols-outlined balance-block-icon" aria-hidden="true">account_balance_wallet</span>
        <div class="balance-block-copy">
          <div class="balance-block-title">
            {{ balanceIsZero ? 'Баланс исчерпан' : 'Недостаточно средств' }}
            <strong class="balance-block-amount">{{ formatBalanceRub(displayBalance) }}</strong>
          </div>
          <p class="balance-block-text">
            Функции с ИИ недоступны — попросите администратора пополнить счёт.
          </p>
        </div>
      </div>

      <div class="notebook-frame">
      <div
        ref="notebookEl"
        class="notebook"
        :class="{
          'session-mode': isChatLayout,
          'clients-picker-page': isClientsPicker,
          'notes-open': isChatLayout && anyLeftPanelOpen,
          'chat-spread': isChatLayout && !anyLeftPanelOpen,
          'home-page': isHomePage,
          'progress-page': isProgressPage,
          'settings-page': isSettingsPage,
          'admin-page': isAdminPage,
          'progress-tab-clients': isProgressPage && progressMobileTab === 'clients',
          'progress-tab-stats': isProgressPage && progressMobileTab === 'stats',
          'cases-page': isCasesPage,
          'cases-tab-new': isCasesPage && casesMobileTab === 'new',
          'cases-tab-library': isCasesPage && casesMobileTab === 'library',
          'route-animating': routeAnimating,
          'swipe-dir-left': mobileSwipeDir === 'left',
          'swipe-dir-right': mobileSwipeDir === 'right',
          'mobile-tab-bar-hidden': hideMobileBottomBar
        }"
      >
        <Teleport to="body">
          <div
            v-if="hubMenuOpen"
            class="hub-section-menu-backdrop"
            aria-hidden="true"
            @click="closeHubMenu"
          />
          <div
            v-if="hubMenuOpen"
            class="hub-section-menu"
            role="menu"
            :aria-label="hubMenuAriaLabel"
          >
            <button
              v-for="item in hubMenuItems"
              :key="item.id"
              type="button"
              role="menuitem"
              class="list-item hub-section-menu-item"
              :class="{ active: hubMenuActiveId === item.id }"
              @click="pickHubMenuItem(item.id)"
            >
              <span class="material-symbols-outlined" aria-hidden="true">{{ item.icon }}</span>
              <strong>{{ item.label }}</strong>
            </button>
          </div>
        </Teleport>

        <div
          v-if="isCasesPage"
          class="cases-side-shell"
          role="tablist"
          aria-label="Разделы кейсов"
        >
          <div class="side-bookmark-rail">
            <button
              type="button"
              role="tab"
              class="side-bookmark side-bookmark-cases-new"
              :class="{ active: casesMobileTab === 'new' }"
              :aria-selected="casesMobileTab === 'new'"
              @click="setCasesMobileTab('new')"
            >
              <span class="side-bookmark-label">Новый</span>
            </button>
            <button
              type="button"
              role="tab"
              class="side-bookmark side-bookmark-cases-library"
              :class="{ active: casesMobileTab === 'library' }"
              :aria-selected="casesMobileTab === 'library'"
              @click="setCasesMobileTab('library')"
            >
              <span class="side-bookmark-label">Библиотека</span>
            </button>
          </div>
        </div>

        <div
          v-if="isProgressPage"
          class="progress-side-shell"
          role="tablist"
          aria-label="Разделы прогресса"
        >
          <div class="side-bookmark-rail">
            <button
              type="button"
              role="tab"
              class="side-bookmark side-bookmark-progress-clients"
              :class="{ active: progressMobileTab === 'clients' }"
              :aria-selected="progressMobileTab === 'clients'"
              @click="setProgressMobileTab('clients')"
            >
              <span class="side-bookmark-label">Анализы</span>
            </button>
            <button
              type="button"
              role="tab"
              class="side-bookmark side-bookmark-progress-stats"
              :class="{ active: progressMobileTab === 'stats' }"
              :aria-selected="progressMobileTab === 'stats'"
              @click="setProgressMobileTab('stats')"
            >
              <span class="side-bookmark-label">Статистика</span>
            </button>
          </div>
        </div>

        <div class="page-left">
          <div
            class="hub-left progress-hub-left"
            :class="{ 'hub-active': isProgressPage }"
            :aria-hidden="!isProgressPage"
            :inert="isProgressPage ? undefined : true"
          >
            <div id="progress-recent-slot"></div>
          </div>

          <div
            class="hub-left settings-hub-left"
            :class="{ 'hub-active': isSettingsPage }"
            :aria-hidden="!isSettingsPage"
            :inert="isSettingsPage ? undefined : true"
          >
            <div id="settings-nav-slot"></div>
            <div class="settings-left-meta" v-if="me">
              <strong>{{ me.displayName }}</strong>
              <span v-if="me.email" class="muted">{{ me.email }}</span>
              <span
                v-if="me.admin"
                class="muted"
              >
                Баланс приложения:
                {{
                  (appBalanceRub ?? me.appBalanceRub) == null
                    ? '—'
                    : formatBalanceRub(appBalanceRub ?? me.appBalanceRub)
                }}
              </span>
              <span
                class="muted"
                :class="{
                  'balance-negative': balanceIsNegative,
                  'balance-warning': balanceIsZero
                }"
              >
                {{ me.admin ? 'Личный баланс' : 'Баланс' }}: {{ formatBalanceRub(displayBalance) }}
              </span>
            </div>
          </div>

          <div
            class="hub-left admin-hub-left"
            :class="{ 'hub-active': isAdminPage }"
            :aria-hidden="!isAdminPage"
            :inert="isAdminPage ? undefined : true"
          >
            <div id="admin-nav-slot"></div>
          </div>

          <div
            class="hub-left home-clients-hub"
            :class="{ 'hub-active': isHomePage }"
            :aria-hidden="!isHomePage"
            :inert="isHomePage ? undefined : true"
          >
            <div class="clients-search-row">
              <button
                type="button"
                class="hub-section-menu-btn"
                aria-haspopup="menu"
                aria-label="Разделы чата"
                title="Разделы"
                @click="openHubMenu('chat')"
              >
                <span class="material-symbols-outlined" aria-hidden="true">more_vert</span>
              </button>
              <div class="search-bar">
                <span class="icon">search</span>
                <input v-model="query" placeholder="Поиск по клиентам" />
              </div>
              <button
                class="fab mini"
                type="button"
                title="Новый приём"
                :class="{ open: newReceptionOpen }"
                :disabled="starting"
                :aria-expanded="newReceptionOpen"
                @click="toggleNewReception"
              >{{ newReceptionOpen ? '×' : '+' }}</button>
            </div>

            <div class="new-reception-collapse" :class="{ open: newReceptionOpen }">
              <div class="new-reception-collapse-inner">
                <div
                  class="new-reception-panel"
                  :class="{ 'is-working': starting }"
                  :aria-hidden="!newReceptionOpen"
                  :inert="newReceptionOpen ? undefined : true"
                >
                  <span class="new-reception-label">Создание нового клиента</span>

                  <AiWorkingBlock v-if="starting" compact />

                  <template v-else>
                    <div class="case-start-field">
                      <span class="section-label">Сложность</span>
                      <div class="case-start-seg" role="group" aria-label="Сложность">
                        <button type="button" :class="{ active: difficulty === 'EASY' }" @click="difficulty = 'EASY'">Лёгкий</button>
                        <button type="button" :class="{ active: difficulty === 'MEDIUM' }" @click="difficulty = 'MEDIUM'">Средний</button>
                        <button type="button" :class="{ active: difficulty === 'HARD' }" @click="difficulty = 'HARD'">Сложный</button>
                      </div>
                    </div>

                    <div class="case-start-field">
                      <span class="section-label">Пол</span>
                      <div class="case-start-seg" role="group" aria-label="Пол">
                        <button type="button" :class="{ active: receptionGender === 'MALE' }" @click="receptionGender = 'MALE'">М</button>
                        <button type="button" :class="{ active: receptionGender === 'FEMALE' }" @click="receptionGender = 'FEMALE'">Ж</button>
                        <button type="button" :class="{ active: receptionGender === 'ANY' }" @click="receptionGender = 'ANY'">Неважно</button>
                      </div>
                    </div>

                    <div class="case-start-field">
                      <span class="section-label">Возраст (18–80)</span>
                      <div class="case-start-seg" role="group" aria-label="Возраст режим">
                        <button type="button" :class="{ active: !receptionAgeAny }" @click="receptionAgeAny = false">Указать</button>
                        <button type="button" :class="{ active: receptionAgeAny }" @click="receptionAgeAny = true">Неважно</button>
                      </div>
                      <input
                        v-model.number="receptionAge"
                        class="field case-start-age-input"
                        type="number"
                        min="18"
                        max="80"
                        inputmode="numeric"
                        title="Возраст"
                        :disabled="receptionAgeAny"
                        @blur="clampReceptionAge"
                      />
                    </div>
                  </template>

                  <div class="case-start-actions new-reception-actions">
                    <button
                      type="button"
                      class="toolbar-btn"
                      :class="{ analyzing: starting }"
                      :disabled="starting || !llmAllowed"
                      :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
                      @click="startReception"
                    >
                      <span
                        class="material-symbols-outlined"
                        :class="{ 'spin-slow': starting }"
                        aria-hidden="true"
                      >{{ starting ? 'progress_activity' : 'play_arrow' }}</span>
                      <span>{{ starting ? 'Создаём…' : 'Начать' }}</span>
                    </button>
                  </div>
                  <p v-if="!llmAllowed" class="error-text">{{ INSUFFICIENT_FUNDS_MSG }}</p>
                  <p v-if="error" class="error-text">{{ error }}</p>
                </div>
              </div>
            </div>

            <div class="notes-section-head clients-section-head">
              <div class="section-label left-scroll-label">Клиенты</div>
              <span class="notes-count clients-count">{{ filtered.length }}</span>
            </div>

            <div class="segmented-list">
              <p v-if="!filtered.length && query.trim()" class="empty-hint">Никого не нашли по этому запросу.</p>
              <p v-else-if="!filtered.length" class="empty-hint">
                Здесь появятся ваши клиенты.<br />
                Нажмите «+», чтобы начать новый приём, или откройте раздел «Кейсы»
                и запустите готовый сценарий из библиотеки.
              </p>
              <RouterLink
                v-for="c in filtered"
                :key="c.id"
                class="list-item"
                :class="{ active: route.params.id === c.id }"
                :to="`/clients/${c.id}`"
                @click="closeLeftPanelOnMobile({ instant: true })"
              >
                <div class="list-item-top">
                  <div class="list-item-title"><strong>{{ c.name }}</strong>, {{ c.age }}</div>
                  <div class="list-item-badges">
                    <span class="session-entry-badge progress-badge">{{ therapyProgressLabel(c.therapyProgress) }}</span>
                    <span
                      v-if="c.activeSessionId && isClientSessionPaused(c)"
                      class="session-entry-badge paused"
                    >Пауза</span>
                    <span
                      v-else-if="c.activeSessionId"
                      class="session-entry-badge live"
                    >Сеанс</span>
                  </div>
                </div>
                <div class="muted">{{ c.presentingRequest }}</div>
                <div class="tags">
                  <span class="tag" v-for="t in c.behaviorTags" :key="tagCode(t)">{{ tagLabel(t) }}</span>
                </div>
              </RouterLink>
            </div>
          </div>

          <div
            class="hub-left cases-create-left"
            :class="{ 'hub-active': isCasesPage }"
            :aria-hidden="!isCasesPage"
            :inert="isCasesPage ? undefined : true"
          >
            <header class="page-head compact hub-sticky-head cases-hub-head">
              <div class="hub-sticky-head-row">
                <button
                  type="button"
                  class="hub-section-menu-btn"
                  aria-haspopup="menu"
                  aria-label="Разделы кейсов"
                  title="Разделы"
                  @click="openHubMenu('cases')"
                >
                  <span class="material-symbols-outlined" aria-hidden="true">more_vert</span>
                </button>
                <div class="hub-sticky-head-copy">
                  <div class="section-label">Кейсы</div>
                  <h2 class="page-title">Новый кейс</h2>
                  <p class="page-sub">
                    Опишите сценарий сами или попросите ИИ собрать заготовку по короткому промпту —
                    потом его можно править и запускать в приём.
                  </p>
                </div>
              </div>
            </header>

            <div class="button-group mode-switch">
              <button type="button" :class="caseMode === 'manual' ? '' : 'secondary'" @click="caseMode = 'manual'">Вручную</button>
              <button type="button" :class="caseMode === 'prompt' ? '' : 'secondary'" @click="caseMode = 'prompt'">Промпт</button>
            </div>

            <template v-if="caseMode === 'manual'">
              <div class="field-block">
                <label class="section-label">Название</label>
                <input class="field" v-model="caseTitle" placeholder="Например: Тревога перед выступлениями" />
              </div>
              <div class="field-block">
                <label class="section-label">Запрос клиента</label>
                <textarea class="field" rows="4" v-model="caseRequest" placeholder="От первого лица…" />
              </div>
              <div class="field-block">
                <label class="section-label">Сложность</label>
                <div class="button-group">
                  <button type="button" :class="caseDifficulty === 'EASY' ? '' : 'secondary'" @click="caseDifficulty = 'EASY'">Лёгкий</button>
                  <button type="button" :class="caseDifficulty === 'MEDIUM' ? '' : 'secondary'" @click="caseDifficulty = 'MEDIUM'">Средний</button>
                  <button type="button" :class="caseDifficulty === 'HARD' ? '' : 'secondary'" @click="caseDifficulty = 'HARD'">Сложный</button>
                </div>
              </div>
              <div class="field-block">
                <label class="section-label">Заметки (опционально)</label>
                <textarea class="field" rows="2" v-model="caseNotes" placeholder="Фокус для терапевта…" />
              </div>
              <button
                class="btn-text btn-block"
                type="button"
                :disabled="caseBusy"
                @click="createCaseManual"
              >
                <span class="material-symbols-outlined" aria-hidden="true">
                  {{ caseBusy ? 'hourglass_empty' : 'save' }}
                </span>
                {{ caseBusy ? 'Сохранение…' : 'Сохранить кейс' }}
              </button>
            </template>

            <template v-else>
              <div class="field-block">
                <label class="section-label">Промпт для ИИ</label>
                <textarea class="field" rows="5" v-model="casePrompt" placeholder="Опишите тему, контекст, желаемые тоны поведения…" />
              </div>
              <button
                class="btn-text btn-block"
                type="button"
                :disabled="caseBusy || !llmAllowed"
                :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
                @click="generateCaseFromPrompt"
              >
                <span class="material-symbols-outlined" aria-hidden="true">
                  {{ caseBusy ? 'hourglass_empty' : 'auto_awesome' }}
                </span>
                {{ caseBusy ? 'Генерация…' : 'Сгенерировать по описанию' }}
              </button>
            </template>

            <div class="stack-divider">
              <div class="section-label">Или автоматически</div>
              <button
                class="btn-text secondary btn-block"
                type="button"
                :disabled="caseBusy || !llmAllowed"
                :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
                @click="generateCaseAuto"
              >
                <span class="material-symbols-outlined" aria-hidden="true">
                  {{ caseBusy ? 'hourglass_empty' : 'add' }}
                </span>
                {{ caseBusy ? 'Генерация…' : 'Сгенерировать новый кейс' }}
              </button>
            </div>
            <p v-if="caseError" class="error-text">{{ caseError }}</p>
          </div>

          <div
            id="session-left"
            class="session-left-overlay"
            :class="{ active: isChatLayout }"
          >
            <!--
              Keep shell + Teleport targets mounted (v-show, not v-if).
              Mobile defers layoutMode until after-leave; ChatView mounts in the
              same tick and must find #session-*-slot or Vue throws emitsOptions.
            -->
            <div v-show="isChatLayout" class="notes-left-shell">
              <div class="side-bookmark-rail">
                <button
                  type="button"
                  class="side-bookmark side-bookmark-clients"
                  :class="{ active: clientsPanelOpen }"
                  @click="toggleSessionLeftPanel('clients')"
                >
                  <span class="side-bookmark-label">Клиенты</span>
                </button>
                <button
                  type="button"
                  class="side-bookmark side-bookmark-notes"
                  :class="{ active: notesPanelOpen }"
                  :disabled="isClientsPicker"
                  @click="toggleSessionLeftPanel('notes')"
                >
                  <span class="side-bookmark-label">Заметки</span>
                </button>
                <button
                  type="button"
                  class="side-bookmark side-bookmark-sessions"
                  :class="{ active: sessionsPanelOpen }"
                  :disabled="isClientsPicker"
                  @click="toggleSessionLeftPanel('sessions')"
                >
                  <span class="side-bookmark-label">Сеансы</span>
                </button>
              </div>

              <Transition :name="panelTransitionEnabled ? 'notes-panel' : undefined">
                <div v-if="clientsPanelOpen" class="session-left-panel clients-left-panel">
                  <div class="clients-search-row">
                    <button
                      type="button"
                      class="hub-section-menu-btn"
                      aria-haspopup="menu"
                      aria-label="Разделы чата"
                      title="Разделы"
                      @click="openHubMenu('chat')"
                    >
                      <span class="material-symbols-outlined" aria-hidden="true">more_vert</span>
                    </button>
                    <div class="search-bar">
                      <span class="icon">search</span>
                      <input v-model="query" placeholder="Поиск по клиентам" />
                    </div>
                    <button
                      class="fab mini"
                      type="button"
                      title="Новый приём"
                      :class="{ open: newReceptionOpen }"
                      :disabled="starting"
                      :aria-expanded="newReceptionOpen"
                      @click="toggleNewReception"
                    >{{ newReceptionOpen ? '×' : '+' }}</button>
                  </div>

                  <div class="new-reception-collapse" :class="{ open: newReceptionOpen }">
                    <div class="new-reception-collapse-inner">
                      <div
                        class="new-reception-panel"
                        :class="{ 'is-working': starting }"
                        :aria-hidden="!newReceptionOpen"
                        :inert="newReceptionOpen ? undefined : true"
                      >
                        <span class="new-reception-label">Создание нового клиента</span>

                        <AiWorkingBlock v-if="starting" compact />

                        <template v-else>
                          <div class="case-start-field">
                            <span class="section-label">Сложность</span>
                            <div class="case-start-seg" role="group" aria-label="Сложность">
                              <button type="button" :class="{ active: difficulty === 'EASY' }" @click="difficulty = 'EASY'">Лёгкий</button>
                              <button type="button" :class="{ active: difficulty === 'MEDIUM' }" @click="difficulty = 'MEDIUM'">Средний</button>
                              <button type="button" :class="{ active: difficulty === 'HARD' }" @click="difficulty = 'HARD'">Сложный</button>
                            </div>
                          </div>

                          <div class="case-start-field">
                            <span class="section-label">Пол</span>
                            <div class="case-start-seg" role="group" aria-label="Пол">
                              <button type="button" :class="{ active: receptionGender === 'MALE' }" @click="receptionGender = 'MALE'">М</button>
                              <button type="button" :class="{ active: receptionGender === 'FEMALE' }" @click="receptionGender = 'FEMALE'">Ж</button>
                              <button type="button" :class="{ active: receptionGender === 'ANY' }" @click="receptionGender = 'ANY'">Неважно</button>
                            </div>
                          </div>

                          <div class="case-start-field">
                            <span class="section-label">Возраст (18–80)</span>
                            <div class="case-start-seg" role="group" aria-label="Возраст режим">
                              <button type="button" :class="{ active: !receptionAgeAny }" @click="receptionAgeAny = false">Указать</button>
                              <button type="button" :class="{ active: receptionAgeAny }" @click="receptionAgeAny = true">Неважно</button>
                            </div>
                            <input
                              v-model.number="receptionAge"
                              class="field case-start-age-input"
                              type="number"
                              min="18"
                              max="80"
                              inputmode="numeric"
                              title="Возраст"
                              :disabled="receptionAgeAny"
                              @blur="clampReceptionAge"
                            />
                          </div>
                        </template>

                        <div class="case-start-actions new-reception-actions">
                          <button
                            type="button"
                            class="toolbar-btn"
                            :class="{ analyzing: starting }"
                            :disabled="starting || !llmAllowed"
                            :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
                            @click="startReception"
                          >
                            <span
                              class="material-symbols-outlined"
                              :class="{ 'spin-slow': starting }"
                              aria-hidden="true"
                            >{{ starting ? 'progress_activity' : 'play_arrow' }}</span>
                            <span>{{ starting ? 'Создаём…' : 'Начать' }}</span>
                          </button>
                        </div>
                        <p v-if="error" class="error-text">{{ error }}</p>
                      </div>
                    </div>
                  </div>

                  <div class="notes-section-head clients-section-head">
                    <div class="section-label left-scroll-label">Клиенты</div>
                    <span class="notes-count clients-count">{{ filtered.length }}</span>
                  </div>

                  <div class="segmented-list notes-list-left">
                    <p v-if="!filtered.length && query.trim()" class="empty-hint">Никого не нашли по этому запросу.</p>
                    <p v-else-if="!filtered.length" class="empty-hint">
                      Здесь появятся ваши клиенты.<br />
                      Нажмите «+», чтобы начать новый приём, или откройте раздел «Кейсы»
                      и запустите готовый сценарий из библиотеки.
                    </p>
                    <RouterLink
                      v-for="c in filtered"
                      :key="c.id"
                      class="list-item"
                      :class="{ active: route.params.id === c.id }"
                      :to="`/clients/${c.id}`"
                      @click="closeLeftPanelOnMobile({ instant: true })"
                    >
                      <div class="list-item-top">
                        <div class="list-item-title"><strong>{{ c.name }}</strong>, {{ c.age }}</div>
                        <div class="list-item-badges">
                          <span class="session-entry-badge progress-badge">{{ therapyProgressLabel(c.therapyProgress) }}</span>
                          <span
                            v-if="c.activeSessionId && isClientSessionPaused(c)"
                            class="session-entry-badge paused"
                          >Пауза</span>
                          <span
                            v-else-if="c.activeSessionId"
                            class="session-entry-badge live"
                          >Сеанс</span>
                        </div>
                      </div>
                      <div class="muted">{{ c.presentingRequest }}</div>
                      <div class="tags">
                        <span class="tag" v-for="t in c.behaviorTags" :key="tagCode(t)">{{ tagLabel(t) }}</span>
                      </div>
                    </RouterLink>
                  </div>
                </div>
              </Transition>

              <div id="session-notes-slot"></div>
              <div id="session-sessions-slot"></div>
              <div id="session-analysis-slot"></div>
            </div>
          </div>
        </div>

        <div class="notebook-spine" aria-hidden="true"></div>

        <div class="page-right page-transition-stage">
          <div
            v-if="!llmAllowed"
            class="balance-block-banner balance-block-banner-page"
            :class="balanceIsZero ? 'is-warning' : 'is-danger'"
            role="status"
          >
            <span class="material-symbols-outlined balance-block-icon" aria-hidden="true">account_balance_wallet</span>
            <div class="balance-block-copy">
              <div class="balance-block-title">
                {{ balanceIsZero ? 'Баланс исчерпан' : 'Недостаточно средств' }}
                <strong class="balance-block-amount">{{ formatBalanceRub(displayBalance) }}</strong>
              </div>
              <p class="balance-block-text">
                Функции с ИИ недоступны — попросите администратора пополнить счёт.
              </p>
            </div>
          </div>
          <RouterView v-slot="{ Component, route: r }">
            <Transition
              :name="softPageTransition ? 'page-fade-soft' : 'page-fade'"
              :mode="softPageTransition ? undefined : 'out-in'"
              @after-leave="onRouteAfterLeave"
              @after-enter="onRouteAfterEnter"
            >
              <div
                :key="r.name === 'chat' ? r.path : String(r.name || r.path)"
                class="flip-sheet"
              >
                <component :is="Component" />
              </div>
            </Transition>
          </RouterView>
        </div>
      </div>
      </div>
    </div>
  </div>
</template>
