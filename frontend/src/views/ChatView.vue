<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/client'
import AnalysisReport from '../components/AnalysisReport.vue'
import { difficultyLabel, genderLabel, tagCode, tagLabel } from '../labels'
import { apiErrorMessage, INSUFFICIENT_FUNDS_MSG } from '../api/errors'
import { newId } from '../id'
import { openHubMenu } from '../hubMenu'
import { llmAllowed, setMeProfile, sttEnabled, ttsEnabled } from '../meProfile'
import {
  analysisPanelOpen,
  closeLeftPanelOnMobile,
  notesPanelOpen,
  openAnalysisPanel,
  openChatSessionUi,
  openClientsPanel,
  openSessionsPanel,
  panelTransitionEnabled,
  resetSessionUi,
  reviewingSessionId,
  sessionsPanelOpen,
  setLiveTimerState,
  clearPendingSessionReview,
  peekPendingSessionReview
} from '../sessionUi'
import type { Analysis, Client, Message, Note, SessionSummary } from '../types'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()

const client = ref<Client | null>(null)
const sessions = ref<SessionSummary[]>([])
const allMessages = ref<Message[]>([])
const sessionMessages = ref<Message[]>([])
const supervisor = ref<Message[]>([])
const notes = ref<Note[]>([])
const draft = ref('')
const supervisorDraft = ref('')
const noteDraft = ref('')
const notesQuery = ref('')
const sessionsQuery = ref('')
const chatInputEl = ref<HTMLTextAreaElement | null>(null)
const noteInputEl = ref<HTMLTextAreaElement | null>(null)
const supervisorInputEl = ref<HTMLTextAreaElement | null>(null)
const busy = ref(false)

async function refreshBalance() {
  try {
    setMeProfile(await api.me())
  } catch {
    /* ignore */
  }
}

function resizeComposer(el: HTMLTextAreaElement | null | undefined) {
  if (!el) return
  el.classList.remove('is-scrollable')
  el.style.height = '0px'
  const maxRaw = getComputedStyle(el).maxHeight
  const max = Number.parseFloat(maxRaw)
  const cap = Number.isFinite(max) && max > 0 ? max : 132
  const contentHeight = el.scrollHeight
  el.style.height = `${Math.min(contentHeight, cap)}px`
  // Show scrollbar only when content exceeds the max height.
  if (contentHeight > cap + 1) {
    el.classList.add('is-scrollable')
  }
}

function resizeAllComposers() {
  resizeComposer(chatInputEl.value)
  resizeComposer(noteInputEl.value)
  resizeComposer(supervisorInputEl.value)
}

watch([draft, noteDraft, supervisorDraft], async () => {
  await nextTick()
  resizeAllComposers()
})
const analyzing = ref(false)
const analyzingStatusIdx = ref(0)
let analyzingStatusTimer: ReturnType<typeof setInterval> | null = null

const ANALYZING_STATUSES = [
  'Читаю транскрипт сеанса…',
  'Сверяю с гештальт-рубрикой…',
  'Отмечаю сильные стороны…',
  'Ищу зоны роста…',
  'Формулирую отчёт…'
]

const analyzingStatus = computed(() => ANALYZING_STATUSES[analyzingStatusIdx.value] ?? ANALYZING_STATUSES[0])

function startAnalyzingStatusCycle() {
  analyzingStatusIdx.value = 0
  if (analyzingStatusTimer) clearInterval(analyzingStatusTimer)
  analyzingStatusTimer = setInterval(() => {
    analyzingStatusIdx.value = (analyzingStatusIdx.value + 1) % ANALYZING_STATUSES.length
  }, 2200)
}

function stopAnalyzingStatusCycle() {
  if (analyzingStatusTimer) {
    clearInterval(analyzingStatusTimer)
    analyzingStatusTimer = null
  }
}
/** Waiting for client (LLM) reply — show typing bubble. */
const awaitingClient = ref(false)
/** Waiting for supervisor (LLM) reply — same typing bubble. */
const awaitingSupervisor = ref(false)
/** Cloud TTS playback for CLIENT bubbles. */
const speakingMessageId = ref<string | null>(null)
const ttsLoadingId = ref<string | null>(null)
let ttsAudio: HTMLAudioElement | null = null
let ttsObjectUrl: string | null = null

function stopTtsPlayback() {
  if (ttsAudio) {
    ttsAudio.pause()
    ttsAudio.onended = null
    ttsAudio.onerror = null
    ttsAudio = null
  }
  if (ttsObjectUrl) {
    URL.revokeObjectURL(ttsObjectUrl)
    ttsObjectUrl = null
  }
  speakingMessageId.value = null
}

async function toggleSpeakClient(m: Message) {
  if (!ttsEnabled.value || m.role !== 'CLIENT' || !m.content?.trim() || !llmAllowed.value) return
  if (speakingMessageId.value === m.id || ttsLoadingId.value === m.id) {
    stopTtsPlayback()
    ttsLoadingId.value = null
    return
  }
  stopTtsPlayback()
  ttsLoadingId.value = m.id
  error.value = ''
  try {
    const blob = await api.speakMessage(m.id)
    await refreshBalance()
    const url = URL.createObjectURL(blob)
    ttsObjectUrl = url
    const audio = new Audio(url)
    ttsAudio = audio
    speakingMessageId.value = m.id
    audio.onended = () => {
      if (speakingMessageId.value === m.id) stopTtsPlayback()
    }
    audio.onerror = () => {
      stopTtsPlayback()
      error.value = 'Не удалось воспроизвести озвучку'
    }
    await audio.play()
  } catch (e) {
    stopTtsPlayback()
    error.value = apiErrorMessage(e, 'Не удалось озвучить сообщение')
  } finally {
    if (ttsLoadingId.value === m.id) ttsLoadingId.value = null
  }
}

/** Therapist STT (MediaRecorder → /api/stt/transcribe → draft). */
const sttRecording = ref(false)
const sttBusy = ref(false)
let sttMediaRecorder: MediaRecorder | null = null
let sttChunks: BlobPart[] = []
let sttStream: MediaStream | null = null

function stopSttTracks() {
  if (sttStream) {
    for (const t of sttStream.getTracks()) t.stop()
    sttStream = null
  }
}

type SttTarget = 'chat' | 'supervisor'
const sttTarget = ref<SttTarget>('chat')

const composerHasText = computed(() => !!draft.value.trim())
const supervisorHasText = computed(() => !!supervisorDraft.value.trim())

/** Telegram-style: mic when empty (+ STT on), send when there is text. */
const composerActionIsMic = computed(() =>
  sttEnabled.value && (
    !composerHasText.value
    || (sttRecording.value && sttTarget.value === 'chat')
    || (sttBusy.value && sttTarget.value === 'chat')
  )
)

const supervisorActionIsMic = computed(() =>
  sttEnabled.value && (
    !supervisorHasText.value
    || (sttRecording.value && sttTarget.value === 'supervisor')
    || (sttBusy.value && sttTarget.value === 'supervisor')
  )
)

const chatSttActive = computed(() =>
  (sttRecording.value || sttBusy.value) && sttTarget.value === 'chat'
)
const supervisorSttActive = computed(() =>
  (sttRecording.value || sttBusy.value) && sttTarget.value === 'supervisor'
)

function onComposerAction(e: Event) {
  if (composerActionIsMic.value) {
    e.preventDefault()
    void toggleSttRecord('chat')
    return
  }
  e.preventDefault()
  void send()
}

function onSupervisorComposerAction(e: Event) {
  if (supervisorActionIsMic.value) {
    e.preventDefault()
    void toggleSttRecord('supervisor')
    return
  }
  e.preventDefault()
  void askSupervisor()
}

async function toggleSttRecord(target: SttTarget = 'chat') {
  if (!sttEnabled.value || !llmAllowed.value || busy.value) return
  if (target === 'chat' && therapyDone.value) return
  if (sttBusy.value) return
  if (sttRecording.value) {
    // Stop whichever recording is active
    sttMediaRecorder?.stop()
    return
  }
  const hasText = target === 'chat' ? composerHasText.value : supervisorHasText.value
  if (hasText) return
  sttTarget.value = target
  error.value = ''
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      error.value = 'Микрофон недоступен в этом браузере'
      return
    }
    sttStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mime = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
      ? 'audio/webm;codecs=opus'
      : MediaRecorder.isTypeSupported('audio/webm')
        ? 'audio/webm'
        : ''
    sttChunks = []
    sttMediaRecorder = mime
      ? new MediaRecorder(sttStream, { mimeType: mime })
      : new MediaRecorder(sttStream)
    sttMediaRecorder.ondataavailable = ev => {
      if (ev.data && ev.data.size > 0) sttChunks.push(ev.data)
    }
    sttMediaRecorder.onerror = () => {
      sttRecording.value = false
      stopSttTracks()
      error.value = 'Ошибка записи микрофона'
    }
    const recordTarget = target
    sttMediaRecorder.onstop = async () => {
      sttRecording.value = false
      stopSttTracks()
      const type = sttMediaRecorder?.mimeType || 'audio/webm'
      sttMediaRecorder = null
      const blob = new Blob(sttChunks, { type })
      sttChunks = []
      if (!blob.size) {
        error.value = 'Пустая запись'
        return
      }
      sttBusy.value = true
      try {
        const res = await api.transcribe(blob, {
          clientId: props.id,
          sessionId: activeSessionId.value || undefined,
          filename: type.includes('ogg') ? 'voice.ogg' : 'voice.webm'
        })
        await refreshBalance()
        const text = (res.text || '').trim()
        if (!text) {
          error.value = 'Не удалось распознать речь'
          return
        }
        // Put transcript in the field, then send immediately (Telegram-style voice).
        if (recordTarget === 'supervisor') {
          supervisorDraft.value = text
          sttBusy.value = false
          await askSupervisor()
        } else {
          draft.value = text
          sttBusy.value = false
          await send()
        }
      } catch (e) {
        error.value = apiErrorMessage(e, 'Не удалось распознать речь')
      } finally {
        sttBusy.value = false
      }
    }
    sttMediaRecorder.start()
    sttRecording.value = true
  } catch (e) {
    stopSttTracks()
    sttRecording.value = false
    error.value = e instanceof Error && /NotAllowed|Permission/i.test(e.message)
      ? 'Нет доступа к микрофону'
      : apiErrorMessage(e, 'Не удалось начать запись')
  }
}
const analysis = ref<Analysis | null>(null)
const error = ref('')
const elapsed = ref(0)
const timerPaused = ref(false)
let timer: number | undefined
const messagesEl = ref<HTMLElement | null>(null)

const supervisorOpen = ref(false)
const notesOnScreen = ref(false)
const pinnedNoteIds = ref<string[]>([])
const supervisorMessagesEl = ref<HTMLElement | null>(null)
/** Mobile: client brief collapsed until tapped. */
const clientInfoOpen = ref(false)

const activeSessionId = computed(() => client.value?.activeSessionId || null)
const therapyDone = computed(() => client.value?.status === 'COMPLETED')
const isReviewing = computed(() => !!reviewingSessionId.value)
const reviewedSession = computed(() =>
  sessions.value.find(s => s.id === reviewingSessionId.value) || null
)
/** Oldest session = №1; list is newest-first. */
const reviewingSessionNumber = computed(() => {
  const id = reviewingSessionId.value
  if (!id) return null
  const idx = sessions.value.findIndex(s => s.id === id)
  if (idx < 0) return null
  return sessions.value.length - idx
})
const pinnedNotes = computed(() =>
  notes.value.filter(n => pinnedNoteIds.value.includes(n.id))
)
const filteredNotes = computed(() => {
  const q = notesQuery.value.trim().toLowerCase()
  if (!q) return notes.value
  return notes.value.filter(n => n.content.toLowerCase().includes(q))
})
const filteredSessions = computed(() => {
  const q = sessionsQuery.value.trim().toLowerCase()
  if (!q) return sessions.value
  return sessions.value.filter(s => {
    const hay = [
      s.preview || '',
      formatSessionDay(s.startedAt),
      formatSessionClock(s.startedAt),
      s.status === 'ACTIVE' ? 'сеанс сейчас пауза' : 'завершён анализ',
      formatDurationLabel(s.elapsedSeconds || 0),
      formatRepliesLabel(s.messageCount),
      `сеанс №${sessionNumber(s.id)}`
    ].join(' ').toLowerCase()
    return hay.includes(q)
  })
})

function sessionNumber(id: string) {
  const idx = sessions.value.findIndex(s => s.id === id)
  if (idx < 0) return 0
  return sessions.value.length - idx
}
const displayMessages = computed(() =>
  isReviewing.value ? sessionMessages.value : allMessages.value
)
/** Hide live transcript until Usage deep-link review is applied. */
const chatHydrating = ref(false)
/** Deep-link asked for analysis panel — keep it open through hydrate. */
const deepLinkAnalysis = ref(false)

function peekDeepLinkSessionId(): string | null {
  const pending = peekPendingSessionReview()
  if (pending?.sessionId) return pending.sessionId
  const q = route.query.session
  return typeof q === 'string' && q ? q : null
}

function peekDeepLinkWantsAnalysis(): boolean {
  const pending = peekPendingSessionReview()
  if (pending?.openAnalysis) return true
  return typeof route.query.view === 'string' && route.query.view === 'analysis'
}

/** Set review mode synchronously so the first paint never shows the live chat. */
function primeDeepLinkReview(): boolean {
  const sid = peekDeepLinkSessionId()
  if (!sid) {
    chatHydrating.value = false
    deepLinkAnalysis.value = false
    return false
  }
  reviewingSessionId.value = sid
  sessionMessages.value = []
  analysis.value = null
  chatHydrating.value = true
  deepLinkAnalysis.value = peekDeepLinkWantsAnalysis()
  panelTransitionEnabled.value = false
  if (deepLinkAnalysis.value) {
    openAnalysisPanel()
  }
  return true
}

primeDeepLinkReview()
const canAnalyzeReviewed = computed(() =>
  !!reviewedSession.value
  && reviewedSession.value.status === 'CLOSED'
  && !reviewedSession.value.hasAnalysis
)
const canToggleAnalysis = computed(() =>
  !!reviewedSession.value?.hasAnalysis || !!analysis.value
)
const analysisShown = computed(() => analysisPanelOpen.value && !!analysis.value)
const composerPlaceholder = computed(() => {
  if (timerPaused.value) return 'Сеанс на паузе…'
  if (!activeSessionId.value) return 'Напишите — начнётся новый сеанс от вас…'
  return 'Реплика терапевта…'
})

function tick() {
  if (!timerPaused.value) elapsed.value += 1
}

function stopTimerInterval() {
  window.clearInterval(timer)
  timer = undefined
}

function startTimerInterval() {
  stopTimerInterval()
  timer = window.setInterval(tick, 1000)
}

function formatTime(sec: number) {
  const m = Math.floor(sec / 60).toString().padStart(2, '0')
  const s = (sec % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

function formatDurationLabel(sec: number) {
  const total = Math.max(0, Math.floor(sec || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  if (h > 0) return `${h} ч ${m} мин`
  if (m > 0) return `${m} мин${s ? ` ${s} с` : ''}`
  return `${s} с`
}

function formatRepliesLabel(count: number) {
  const n = Math.max(0, count || 0)
  const mod10 = n % 10
  const mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return `${n} реплика`
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${n} реплики`
  return `${n} реплик`
}

function formatSessionDate(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function formatSessionDay(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  }).replace(/\sг\.?$/, '')
}

function formatSessionClock(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })
}

function formatNoteDate(iso: string) {
  return formatSessionDate(iso)
}

function toggleSupervisor() {
  supervisorOpen.value = !supervisorOpen.value
}

async function scrollDown() {
  await nextTick()
  if (messagesEl.value) {
    messagesEl.value.scrollTop = messagesEl.value.scrollHeight
  }
}

async function loadSessions() {
  sessions.value = await api.sessions(props.id)
}

async function loadAnalysisFor(sessionId: string | null, options?: { force?: boolean }) {
  analysis.value = null
  if (!sessionId) return
  const meta = sessions.value.find(s => s.id === sessionId)
  if (!options?.force && meta && !meta.hasAnalysis) return
  try {
    analysis.value = await api.getSessionAnalysis(sessionId)
    if (analysis.value) {
      const idx = sessions.value.findIndex(s => s.id === sessionId)
      if (idx >= 0) {
        sessions.value[idx] = { ...sessions.value[idx], hasAnalysis: true }
      }
    }
  } catch {
    analysis.value = null
  }
}

async function openSessionReview(sessionId: string, options?: { keepAnalysisPanel?: boolean }) {
  error.value = ''
  stopTimerInterval()
  reviewingSessionId.value = sessionId
  const keepAnalysis = !!options?.keepAnalysisPanel || deepLinkAnalysis.value
  if (keepAnalysis) {
    openAnalysisPanel()
  } else {
    // Desktop: keep sessions list open. Mobile: close drawer so session chat is visible.
    closeLeftPanelOnMobile()
    if (typeof window !== 'undefined' && !window.matchMedia('(max-width: 900px)').matches) {
      openSessionsPanel()
    }
  }
  busy.value = true
  try {
    sessionMessages.value = await api.sessionMessages(sessionId)
    const meta = sessions.value.find(s => s.id === sessionId)
    if (meta?.hasAnalysis || keepAnalysis) {
      await loadAnalysisFor(sessionId, { force: keepAnalysis })
    } else {
      analysis.value = null
    }
    await scrollDown()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function showAnalysis(options?: { force?: boolean }) {
  if (!reviewingSessionId.value) return
  if (!analysis.value) {
    await loadAnalysisFor(reviewingSessionId.value, { force: options?.force || reviewedSession.value?.hasAnalysis })
  }
  if (analysis.value) openAnalysisPanel()
}

function hideAnalysis() {
  closeLeftPanelOnMobile()
  if (typeof window !== 'undefined' && !window.matchMedia('(max-width: 900px)').matches) {
    openSessionsPanel()
  }
}

async function backToLive() {
  reviewingSessionId.value = null
  sessionMessages.value = []
  analysis.value = null
  closeLeftPanelOnMobile()
  if (analysisPanelOpen.value) {
    if (typeof window !== 'undefined' && !window.matchMedia('(max-width: 900px)').matches) {
      openSessionsPanel()
    }
  }
  if (client.value?.activeSessionId) {
    try {
      client.value = await api.client(props.id)
    } catch { /* keep cached */ }
    if (client.value) applyTimerFromClient(client.value)
  }
  await scrollDown()
}

function applyTimerFromClient(c: Client) {
  if (c.activeSessionId) {
    elapsed.value = c.activeSessionElapsedSeconds ?? 0
    timerPaused.value = !!c.activeSessionTimerPaused
    setLiveTimerState(c.id, timerPaused.value)
    if (timerPaused.value || isReviewing.value) stopTimerInterval()
    else startTimerInterval()
  } else {
    stopTimerInterval()
    timerPaused.value = false
    elapsed.value = 0
    setLiveTimerState(c.id, false)
  }
}

async function syncTimerToBackend() {
  const sid = client.value?.activeSessionId
  if (!sid || timerPaused.value) return
  try {
    await api.updateTimer(sid, { elapsedSeconds: elapsed.value, paused: false })
  } catch {
    /* ignore sync errors on navigate */
  }
}

function isClientMissingError(e: unknown) {
  const msg = String(e)
  return msg.includes('"status":404') || msg.includes('Клиент не найден') || msg.includes('Not Found')
}

async function loadAll() {
  error.value = ''
  try {
    client.value = await api.client(props.id)
    allMessages.value = await api.messages(props.id)
    supervisor.value = await api.supervisorMessages(props.id)
    notes.value = await api.notes(props.id)
    await loadSessions()
    if (reviewingSessionId.value) {
      const still = sessions.value.find(s => s.id === reviewingSessionId.value)
      if (still) {
        sessionMessages.value = await api.sessionMessages(still.id)
        if (still.hasAnalysis) {
          await loadAnalysisFor(still.id)
        } else {
          analysis.value = null
        }
        if (analysisPanelOpen.value && !analysis.value) {
          openSessionsPanel()
        }
      } else {
        reviewingSessionId.value = null
        analysis.value = null
        openSessionsPanel()
      }
    }
    sessionStorage.setItem('psy.lastClientId', props.id)
    await scrollDown()
    applyTimerFromClient(client.value)
  } catch (e) {
    client.value = null
    allMessages.value = []
    supervisor.value = []
    notes.value = []
    sessions.value = []
    if (isClientMissingError(e)) {
      sessionStorage.removeItem('psy.lastClientId')
      await router.replace('/clients')
      return
    }
    error.value = apiErrorMessage(e)
  }
}

async function startSession() {
  if (therapyDone.value) {
    error.value = 'Клиент завершил терапию — повторный сеанс не нужен.'
    return
  }
  if (activeSessionId.value) {
    await backToLive()
    return
  }
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  busy.value = true
  awaitingClient.value = true
  error.value = ''
  try {
    await backToLive()
    await nextTick()
    await scrollDown()
    await api.continueSession(props.id)
    await loadAll()
    await scrollDown()
    await refreshBalance()
  } catch (e) {
    error.value = apiErrorMessage(e)
    await refreshBalance()
  } finally {
    awaitingClient.value = false
    busy.value = false
  }
}

function isSessionPaused(s: SessionSummary) {
  if (s.id === activeSessionId.value) return timerPaused.value
  return !!s.timerPaused
}

function patchActiveSessionTimer(paused: boolean) {
  const sid = activeSessionId.value
  if (!sid) return
  const idx = sessions.value.findIndex(s => s.id === sid)
  if (idx < 0) return
  sessions.value[idx] = {
    ...sessions.value[idx],
    timerPaused: paused,
    elapsedSeconds: elapsed.value
  }
}

async function pauseTimer() {
  if (!activeSessionId.value || timerPaused.value) return
  timerPaused.value = true
  stopTimerInterval()
  patchActiveSessionTimer(true)
  setLiveTimerState(props.id, true)
  try {
    await api.updateTimer(activeSessionId.value, { elapsedSeconds: elapsed.value, paused: true })
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

async function resumeTimer() {
  if (!activeSessionId.value || !timerPaused.value) return
  timerPaused.value = false
  startTimerInterval()
  patchActiveSessionTimer(false)
  setLiveTimerState(props.id, false)
  try {
    await api.updateTimer(activeSessionId.value, { elapsedSeconds: elapsed.value, paused: false })
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
}

async function endSession() {
  if (!activeSessionId.value) return
  busy.value = true
  try {
    const closedId = activeSessionId.value
    stopTimerInterval()
    await api.endSession(closedId, elapsed.value)
    await loadAll()
    await openSessionReview(closedId)
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

async function send() {
  if (busy.value || !draft.value.trim() || therapyDone.value || isReviewing.value) return
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  if (activeSessionId.value && timerPaused.value) {
    error.value = 'Снимите паузу кнопкой «Продолжить», чтобы продолжить разговор.'
    return
  }
  busy.value = true
  awaitingClient.value = true
  error.value = ''
  const content = draft.value.trim()
  draft.value = ''
  await nextTick()
  resizeComposer(chatInputEl.value)
  const hadSession = !!activeSessionId.value
  allMessages.value.push({
    id: newId(),
    sessionId: activeSessionId.value,
    clientId: props.id,
    role: 'THERAPIST',
    content,
    createdAt: new Date().toISOString()
  })
  await scrollDown()
  await nextTick()
  await scrollDown()
  try {
    const reply = hadSession
      ? await api.sendMessage(activeSessionId.value!, content)
      : await api.sendToClient(props.id, content)
    if (!hadSession) {
      client.value = await api.client(props.id)
      applyTimerFromClient(client.value)
      await loadSessions()
      allMessages.value = await api.messages(props.id)
    } else {
      allMessages.value.push(reply)
    }
    await scrollDown()
    await refreshBalance()
  } catch (e) {
    error.value = apiErrorMessage(e)
    await refreshBalance()
  } finally {
    awaitingClient.value = false
    busy.value = false
  }
}

async function askSupervisor() {
  if (busy.value || !supervisorDraft.value.trim() || awaitingSupervisor.value) return
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  busy.value = true
  awaitingSupervisor.value = true
  try {
    const content = supervisorDraft.value.trim()
    supervisorDraft.value = ''
    await nextTick()
    resizeComposer(supervisorInputEl.value)
    supervisor.value.push({
      id: newId(),
      clientId: props.id,
      role: 'THERAPIST',
      content,
      createdAt: new Date().toISOString()
    })
    await nextTick()
    if (supervisorMessagesEl.value) {
      supervisorMessagesEl.value.scrollTop = supervisorMessagesEl.value.scrollHeight
    }
    const reply = await api.askSupervisor(props.id, content)
    supervisor.value.push(reply)
    await nextTick()
    if (supervisorMessagesEl.value) {
      supervisorMessagesEl.value.scrollTop = supervisorMessagesEl.value.scrollHeight
    }
    await refreshBalance()
  } catch (e) {
    error.value = apiErrorMessage(e)
    await refreshBalance()
  } finally {
    awaitingSupervisor.value = false
    busy.value = false
  }
}

async function saveNote() {
  if (!noteDraft.value.trim()) return
  const sid = isReviewing.value
    ? reviewingSessionId.value || undefined
    : (activeSessionId.value || undefined)
  const note = await api.createNote(props.id, noteDraft.value.trim(), sid)
  notes.value.unshift(note)
  noteDraft.value = ''
  await nextTick()
  resizeComposer(noteInputEl.value)
}

function pinNote(id: string) {
  if (!pinnedNoteIds.value.includes(id)) {
    pinnedNoteIds.value = [...pinnedNoteIds.value, id]
  }
  notesOnScreen.value = true
}

function unpinNote(id: string) {
  pinnedNoteIds.value = pinnedNoteIds.value.filter(x => x !== id)
  if (!pinnedNoteIds.value.length) notesOnScreen.value = false
}

function toggleNotesOnScreen() {
  if (notesOnScreen.value) {
    notesOnScreen.value = false
    return
  }
  if (!pinnedNoteIds.value.length && notes.value.length) {
    pinnedNoteIds.value = notes.value.map(n => n.id)
  }
  if (pinnedNoteIds.value.length) {
    notesOnScreen.value = true
  }
}

async function runSessionAnalysis() {
  const sid = reviewingSessionId.value
  if (!sid || !canAnalyzeReviewed.value || analyzing.value) return
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  busy.value = true
  analyzing.value = true
  error.value = ''
  analysis.value = null
  openAnalysisPanel()
  startAnalyzingStatusCycle()
  try {
    analysis.value = await api.analyzeSession(sid)
    await loadSessions()
    const idx = sessions.value.findIndex(s => s.id === sid)
    if (idx >= 0) {
      sessions.value[idx] = { ...sessions.value[idx], hasAnalysis: true }
    }
    openAnalysisPanel()
    await refreshBalance()
  } catch (e) {
    error.value = apiErrorMessage(e)
    await refreshBalance()
  } finally {
    stopAnalyzingStatusCycle()
    analyzing.value = false
    busy.value = false
  }
}

let deepLinkInFlight: Promise<boolean> | null = null

async function applyUsageDeepLink() {
  if (deepLinkInFlight) return deepLinkInFlight
  deepLinkInFlight = (async () => {
    const pending = peekPendingSessionReview()
    const querySession = typeof route.query.session === 'string' ? route.query.session : ''
    const sessionId = pending?.sessionId || querySession
    const wantAnalysis =
      !!pending?.openAnalysis
      || (typeof route.query.view === 'string' && route.query.view === 'analysis')
    if (!sessionId) {
      chatHydrating.value = false
      deepLinkAnalysis.value = false
      panelTransitionEnabled.value = true
      return false
    }

    // Wait until sessions are loaded; keep pending for a later retry.
    if (!sessions.value.length) return false

    const meta = sessions.value.find(s => s.id === sessionId)
    if (!meta) {
      // Unknown id for this client — drop pending so we don't loop.
      clearPendingSessionReview()
      reviewingSessionId.value = null
      sessionMessages.value = []
      chatHydrating.value = false
      deepLinkAnalysis.value = false
      panelTransitionEnabled.value = true
      if (route.query.session || route.query.view) {
        await router.replace({ path: route.path, query: {} })
      }
      return false
    }

    // Keep review id set before fetch so the live transcript never paints.
    reviewingSessionId.value = sessionId
    deepLinkAnalysis.value = wantAnalysis
    await openSessionReview(sessionId, { keepAnalysisPanel: wantAnalysis })
    if (wantAnalysis) {
      await showAnalysis({ force: true })
      openAnalysisPanel()
    }

    clearPendingSessionReview()
    chatHydrating.value = false
    deepLinkAnalysis.value = false
    queueMicrotask(() => {
      panelTransitionEnabled.value = true
    })
    if (route.query.session || route.query.view) {
      // Query-only cleanup must not remount ChatView (App.vue keys chat by path).
      await router.replace({ path: route.path, query: {} })
    }
    return true
  })()
  try {
    return await deepLinkInFlight
  } finally {
    deepLinkInFlight = null
  }
}

onMounted(async () => {
  const primed = primeDeepLinkReview()
  // Skip default layout open when Usage deep-link already primed analysis/session.
  if (!primed) {
    openChatSessionUi()
  }
  await loadAll()
  await applyUsageDeepLink()
  // App.vue route watch may run around mount — retry once after layout settles.
  await nextTick()
  if (peekPendingSessionReview() || route.query.session) {
    await applyUsageDeepLink()
  }
  chatHydrating.value = false
  deepLinkAnalysis.value = false
  queueMicrotask(() => {
    panelTransitionEnabled.value = true
  })
  resizeAllComposers()
})

watch(notesPanelOpen, async open => {
  if (!open) return
  await nextTick()
  resizeComposer(noteInputEl.value)
})
onUnmounted(() => {
  stopTtsPlayback()
  ttsLoadingId.value = null
  if (sttMediaRecorder && sttRecording.value) {
    try { sttMediaRecorder.stop() } catch { /* ignore */ }
  }
  stopSttTracks()
  sttRecording.value = false
  sttBusy.value = false
  void syncTimerToBackend()
  stopTimerInterval()
  stopAnalyzingStatusCycle()
  // Keep pending review if we're navigating into chat with a deep-link still queued.
  if (!peekPendingSessionReview()) {
    resetSessionUi()
  } else {
    clearLiveTimerState()
  }
})
watch(() => props.id, async (_next, prev) => {
  if (prev) await syncTimerToBackend()
  stopTimerInterval()
  pinnedNoteIds.value = []
  notesOnScreen.value = false
  analysis.value = null
  elapsed.value = 0
  timerPaused.value = false
  supervisorOpen.value = false
  clientInfoOpen.value = false
  if (!primeDeepLinkReview()) {
    reviewingSessionId.value = null
    sessionMessages.value = []
  }
  await loadAll()
  await applyUsageDeepLink()
  chatHydrating.value = false
})

watch(
  () => [route.query.session, route.query.view, sessions.value.length] as const,
  async () => {
    if (!peekPendingSessionReview() && !route.query.session) return
    if (!reviewingSessionId.value) primeDeepLinkReview()
    await applyUsageDeepLink()
  }
)

function toggleClientInfo() {
  clientInfoOpen.value = !clientInfoOpen.value
}
</script>

<template>
  <Teleport defer to="#session-notes-slot">
    <Transition :name="panelTransitionEnabled ? 'notes-panel' : undefined">
      <div v-if="client && notesPanelOpen" class="session-left-panel notes-left-panel">
        <div class="notes-search-row">
          <button
            class="icon-btn secondary panel-back-clients"
            type="button"
            title="К клиентам"
            aria-label="К клиентам"
            @click="openClientsPanel()"
          >
            <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
          </button>
          <div class="search-bar">
            <span class="icon">search</span>
            <input v-model="notesQuery" placeholder="Поиск по заметкам" />
          </div>
        </div>

        <div class="notes-section-head">
          <div class="section-label left-scroll-label">Заметки · {{ client.name }}</div>
          <span class="notes-count">{{ filteredNotes.length }}</span>
        </div>
        <div class="notes-list notes-list-left">
          <div
            v-for="n in filteredNotes"
            :key="n.id"
            class="note-card"
            :class="{ pinned: pinnedNoteIds.includes(n.id) }"
          >
            <button
              v-if="!pinnedNoteIds.includes(n.id)"
              class="note-action"
              type="button"
              title="На экран"
              aria-label="На экран"
              @click="pinNote(n.id)"
            >
              <span class="material-symbols-outlined" aria-hidden="true">keep</span>
            </button>
            <button
              v-else
              class="note-action active"
              type="button"
              title="Убрать"
              aria-label="Убрать"
              @click="unpinNote(n.id)"
            >
              <span class="material-symbols-outlined" aria-hidden="true">keep_off</span>
            </button>
            <div class="note-date">{{ formatNoteDate(n.createdAt) }}</div>
            <div class="note-card-body">{{ n.content }}</div>
          </div>
          <div v-if="!notes.length" class="notes-empty">
            <span class="material-symbols-outlined" aria-hidden="true">sticky_note_2</span>
            <p>Пока нет заметок</p>
          </div>
          <div v-else-if="!filteredNotes.length" class="notes-empty">
            <span class="material-symbols-outlined" aria-hidden="true">search_off</span>
            <p>Ничего не найдено</p>
          </div>
        </div>

        <div class="notes-composer">
          <textarea
            ref="noteInputEl"
            class="notes-composer-input"
            rows="1"
            v-model="noteDraft"
            placeholder="Гипотеза, фигура, что заметили…"
            @input="resizeComposer(noteInputEl)"
            @keydown.enter.exact.prevent="saveNote"
          />
          <button
            class="notes-composer-save"
            type="button"
            title="Сохранить"
            aria-label="Сохранить"
            :disabled="!noteDraft.trim()"
            @click="saveNote"
          >
            <span class="material-symbols-outlined" aria-hidden="true">add</span>
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>

  <Teleport defer to="#session-sessions-slot">
    <Transition :name="panelTransitionEnabled ? 'notes-panel' : undefined">
      <div v-if="client && sessionsPanelOpen" class="session-left-panel sessions-left-panel">
        <div class="sessions-search-row">
          <button
            class="icon-btn secondary panel-back-clients"
            type="button"
            title="К клиентам"
            aria-label="К клиентам"
            @click="openClientsPanel()"
          >
            <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
          </button>
          <div class="search-bar">
            <span class="icon">search</span>
            <input v-model="sessionsQuery" placeholder="Поиск по сеансам" />
          </div>
          <button
            v-if="isReviewing"
            class="icon-btn secondary sessions-back-live"
            type="button"
            title="К текущему"
            aria-label="К текущему"
            @click="backToLive"
          >
            <span class="material-symbols-outlined" aria-hidden="true">undo</span>
          </button>
        </div>

        <div class="notes-section-head sessions-section-head">
          <div class="section-label left-scroll-label">Сеансы · {{ client.name }}</div>
          <span class="notes-count sessions-count">{{ filteredSessions.length }}</span>
        </div>

        <div class="notes-list notes-list-left sessions-cards">
          <button
            v-for="s in filteredSessions"
            :key="s.id"
            type="button"
            class="session-card"
            :class="{
              active: reviewingSessionId === s.id || (!isReviewing && s.status === 'ACTIVE'),
              live: s.status === 'ACTIVE' && !isSessionPaused(s),
              paused: s.status === 'ACTIVE' && isSessionPaused(s)
            }"
            @click="s.status === 'ACTIVE' ? backToLive() : openSessionReview(s.id)"
          >
            <div class="session-card-top">
              <span class="session-entry-num">Сеанс №{{ sessionNumber(s.id) }}</span>
              <div class="session-card-badges">
                <span
                  v-if="s.status === 'ACTIVE' && isSessionPaused(s)"
                  class="session-entry-badge paused"
                >Пауза</span>
                <span
                  v-else-if="s.status === 'ACTIVE'"
                  class="session-entry-badge live"
                >Сейчас</span>
                <span
                  v-else-if="s.hasAnalysis"
                  class="session-entry-badge analysis"
                >Анализ</span>
                <span v-else class="session-entry-badge">Завершён</span>
              </div>
            </div>
            <div class="session-entry-when">
              <span class="session-entry-day">{{ formatSessionDay(s.startedAt) }}</span>
              <span class="session-entry-clock">{{ formatSessionClock(s.startedAt) }}</span>
            </div>
            <div class="session-entry-meta">
              <span class="session-meta-chip">
                <span class="material-symbols-outlined" aria-hidden="true">timer</span>
                {{ formatDurationLabel(s.elapsedSeconds || 0) }}
              </span>
              <span class="session-meta-chip">
                <span class="material-symbols-outlined" aria-hidden="true">chat_bubble</span>
                {{ formatRepliesLabel(s.messageCount) }}
              </span>
            </div>
            <p v-if="s.preview" class="session-entry-preview">{{ s.preview }}</p>
          </button>

          <div v-if="!sessions.length" class="notes-empty sessions-empty">
            <span class="material-symbols-outlined" aria-hidden="true">history</span>
            <p>Пока нет сеансов</p>
          </div>
          <div v-else-if="!filteredSessions.length" class="notes-empty sessions-empty">
            <span class="material-symbols-outlined" aria-hidden="true">search_off</span>
            <p>Ничего не найдено</p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <Teleport defer to="#session-analysis-slot">
    <Transition :name="panelTransitionEnabled ? 'notes-panel' : undefined">
      <div v-if="client && analysisPanelOpen" class="session-left-panel analysis-left-panel">
        <div class="book-header">
          <div>
            <div class="left-panel-title">
              {{ reviewingSessionNumber != null
                ? `Анализ сеанса №${reviewingSessionNumber}`
                : 'Анализ сеанса' }}
            </div>
          </div>
          <button
            class="icon-btn secondary"
            type="button"
            title="Скрыть"
            aria-label="Скрыть"
            :disabled="analyzing"
            @click="hideAnalysis"
          >
            <span class="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>
        <div class="analysis-left-body" :class="{ 'is-generating': analyzing }">
          <div v-if="analyzing" class="analysis-generating" aria-live="polite" aria-busy="true">
            <div class="analysis-generating-orb" aria-hidden="true">
              <span class="material-symbols-outlined analysis-generating-icon">auto_awesome</span>
              <span class="analysis-generating-ring"></span>
              <span class="analysis-generating-ring delay"></span>
            </div>
            <p class="analysis-generating-title">ИИ готовит анализ</p>
            <p class="analysis-generating-status" :key="analyzingStatus">{{ analyzingStatus }}</p>
            <div class="analysis-generating-skeleton" aria-hidden="true">
              <span class="skel skel-wide"></span>
              <span class="skel"></span>
              <span class="skel skel-mid"></span>
              <span class="skel"></span>
              <span class="skel skel-short"></span>
            </div>
          </div>
          <AnalysisReport v-else-if="analysis" :analysis="analysis" />
          <p v-else-if="chatHydrating" class="analysis-left-empty">Открываем анализ…</p>
          <p v-else class="analysis-left-empty">
            Анализ появится после завершения сеанса — нажмите «Получить анализ».
          </p>
        </div>
      </div>
    </Transition>
  </Teleport>

  <div v-if="!client" class="session-layout">Загрузка чата…</div>
  <div v-else class="session-layout">
    <div class="session-bar">
      <div class="session-bar-info" :class="{ open: clientInfoOpen }">
        <button
          type="button"
          class="session-bar-summary"
          :aria-expanded="clientInfoOpen"
          aria-controls="session-client-details"
          @click="toggleClientInfo"
        >
          <div class="session-bar-summary-text">
            <h2 class="page-title">
              {{ client.name }}, {{ client.age }}<template v-if="genderLabel(client.gender)"> · {{ genderLabel(client.gender) }}</template>
            </h2>
            <span
              v-if="activeSessionId && !isReviewing"
              class="timer-pill"
              :class="{ paused: timerPaused }"
            >{{ formatTime(elapsed) }}{{ timerPaused ? ' · пауза' : '' }}</span>
          </div>
          <span class="material-symbols-outlined session-bar-chevron" aria-hidden="true">expand_more</span>
        </button>
        <div id="session-client-details" class="session-bar-details">
          <p class="page-sub">{{ client.presentingRequest }}</p>
          <div class="tags">
            <span class="tag" v-for="t in client.behaviorTags" :key="tagCode(t)">{{ tagLabel(t) }}</span>
            <span class="tag">{{ difficultyLabel(client.difficulty, client.difficultyLabelRu) }}</span>
            <span v-if="client.therapyProgress != null" class="tag">прогресс {{ client.therapyProgress }}%</span>
            <span v-if="therapyDone" class="tag active-session">терапия завершена</span>
          </div>
        </div>
      </div>
      <div class="session-bar-toolbar">
        <div class="session-bar-actions session-bar-actions-main">
          <template v-if="!isReviewing && activeSessionId">
            <button
              v-if="!timerPaused"
              class="toolbar-btn secondary session-action-btn"
              type="button"
              title="Пауза"
              aria-label="Пауза"
              :disabled="busy"
              @click="pauseTimer"
            >
              <span class="material-symbols-outlined" aria-hidden="true">pause</span>
              <span class="label-full">Пауза</span>
            </button>
            <button
              v-else
              class="toolbar-btn session-action-btn"
              type="button"
              title="Продолжить"
              aria-label="Продолжить"
              :disabled="busy"
              @click="resumeTimer"
            >
              <span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>
              <span class="label-full">Продолжить</span>
            </button>
            <button
              class="toolbar-btn danger session-action-btn"
              type="button"
              title="Завершить"
              aria-label="Завершить"
              :disabled="busy"
              @click="endSession"
            >
              <span class="material-symbols-outlined" aria-hidden="true">stop</span>
              <span class="label-full">Завершить</span>
            </button>
          </template>
          <button
            v-if="!isReviewing && !activeSessionId && !therapyDone"
            class="toolbar-btn"
            type="button"
            title="Клиент напишет первым"
            :disabled="busy || !llmAllowed"
            @click="startSession"
          >
            <span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>
            Новый сеанс
          </button>
          <button
            v-if="isReviewing"
            class="toolbar-btn secondary"
            type="button"
            @click="backToLive"
          >
            <span class="material-symbols-outlined" aria-hidden="true">undo</span>
            К чату
          </button>
          <button
            v-if="isReviewing && canAnalyzeReviewed"
            class="toolbar-btn"
            type="button"
            :class="{ analyzing: analyzing }"
            :disabled="busy || !llmAllowed"
            :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
            @click="runSessionAnalysis"
          >
            <span
              class="material-symbols-outlined"
              :class="{ 'spin-slow': analyzing }"
              aria-hidden="true"
            >{{ analyzing ? 'progress_activity' : 'analytics' }}</span>
            {{ analyzing ? 'Анализируем…' : 'Получить анализ' }}
          </button>
          <button
            v-else-if="isReviewing && canToggleAnalysis"
            class="toolbar-btn"
            type="button"
            :class="{ secondary: !analysisShown }"
            :aria-pressed="analysisShown"
            :disabled="busy"
            @click="analysisShown ? hideAnalysis() : showAnalysis()"
          >
            <span class="material-symbols-outlined" aria-hidden="true">analytics</span>
            {{ analysisShown ? 'Скрыть' : 'Анализ' }}
          </button>
        </div>
        <div class="session-bar-actions session-bar-actions-side">
          <button
            class="toolbar-btn session-action-btn"
            type="button"
            title="Заметки"
            aria-label="Заметки"
            :class="{ secondary: !notesOnScreen }"
            :aria-pressed="notesOnScreen"
            :disabled="!notes.length && !pinnedNoteIds.length"
            @click="toggleNotesOnScreen"
          >
            <span class="material-symbols-outlined" aria-hidden="true">sticky_note_2</span>
            <span class="label-full">Заметки</span>
          </button>
          <button
            class="toolbar-btn session-action-btn"
            type="button"
            title="Супервизор"
            aria-label="Супервизор"
            :class="{ secondary: !supervisorOpen }"
            :aria-pressed="supervisorOpen"
            @click="toggleSupervisor"
          >
            <span class="material-symbols-outlined" aria-hidden="true">support_agent</span>
            <span class="label-full">Супервизор</span>
          </button>
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
        </div>
      </div>
    </div>

    <p v-if="therapyDone && !isReviewing" class="pane-sub session-notice">
      Клиент чувствует, что терапия помогла — повторный сеанс не требуется.
    </p>
    <p v-if="error" class="error-text session-notice">{{ error }}</p>

    <Transition name="notes-board">
      <div v-if="notesOnScreen && pinnedNotes.length" class="notes-board">
        <TransitionGroup name="sticky" tag="div" class="notes-board-grid">
          <div
            v-for="(n, i) in pinnedNotes"
            :key="n.id"
            class="note-sticky-wrap"
            :style="{ '--stick-delay': `${Math.min(i, 6) * 55}ms` }"
          >
            <div class="note-sticky" :class="`tilt-${i % 3}`">
              <button class="note-close" type="button" @click="unpinNote(n.id)" aria-label="Убрать">
                <span class="material-symbols-outlined" aria-hidden="true">close</span>
              </button>
              <div class="note-date">{{ formatNoteDate(n.createdAt) }}</div>
              {{ n.content }}
            </div>
          </div>
        </TransitionGroup>
      </div>
    </Transition>

    <div class="session-split" :class="{ 'with-supervisor': supervisorOpen }">
      <section class="chat chat-full">
        <div class="pane-head">
          <div class="pane-title">
            {{ isReviewing
              ? (reviewingSessionNumber != null ? `Чат сеанса №${reviewingSessionNumber}` : 'Чат сеанса')
              : 'Чат с клиентом' }}
          </div>
        </div>
        <div class="messages messages-full" ref="messagesEl">
          <p v-if="chatHydrating" class="empty-hint">Открываем сеанс…</p>
          <template v-else>
            <div v-for="m in displayMessages" :key="m.id" class="bubble" :class="m.role">
              <div class="bubble-main">
                <strong v-if="m.role !== 'SYSTEM'">{{ m.role === 'CLIENT' ? client.name : 'Вы' }}: </strong>{{ m.content }}
              </div>
              <button
                v-if="ttsEnabled && m.role === 'CLIENT' && m.content?.trim()"
                type="button"
                class="bubble-tts"
                :class="{
                  active: speakingMessageId === m.id,
                  loading: ttsLoadingId === m.id
                }"
                :disabled="!!ttsLoadingId && ttsLoadingId !== m.id"
                :title="speakingMessageId === m.id || ttsLoadingId === m.id ? 'Остановить' : 'Озвучить'"
                :aria-label="speakingMessageId === m.id || ttsLoadingId === m.id ? 'Остановить озвучку' : 'Озвучить сообщение клиента'"
                @click.stop="toggleSpeakClient(m)"
              >
                <span class="material-symbols-outlined" aria-hidden="true">
                  {{
                    ttsLoadingId === m.id
                      ? 'progress_activity'
                      : speakingMessageId === m.id
                        ? 'stop_circle'
                        : 'volume_up'
                  }}
                </span>
              </button>
            </div>
            <div
              v-if="awaitingClient && client"
              class="bubble CLIENT typing-bubble"
              aria-live="polite"
              aria-label="Клиент печатает"
            >
              <strong>{{ client.name }}: </strong>
              <span class="typing-dots" aria-hidden="true">
                <span></span><span></span><span></span>
              </span>
            </div>
            <p v-if="!displayMessages.length && !awaitingClient" class="empty-hint">Пока нет сообщений в этом сеансе.</p>
          </template>
        </div>
        <div v-if="!isReviewing && (!therapyDone || activeSessionId)" class="composer chat-composer">
          <textarea
            ref="chatInputEl"
            class="composer-input"
            rows="1"
            v-model="draft"
            enterkeyhint="send"
            :disabled="busy || (!!activeSessionId && timerPaused) || therapyDone || !llmAllowed || sttBusy"
            :placeholder="llmAllowed ? composerPlaceholder : INSUFFICIENT_FUNDS_MSG"
            @input="resizeComposer(chatInputEl)"
            @keydown.enter.exact.prevent="send"
          />
          <button
            class="composer-send"
            type="button"
            :class="{
              'is-mic': composerActionIsMic && !chatSttActive,
              recording: chatSttActive && sttRecording,
              busy: chatSttActive && sttBusy
            }"
            :title="composerActionIsMic
              ? (chatSttActive && sttRecording ? 'Стоп и отправить' : chatSttActive && sttBusy ? 'Распознаём…' : 'Голосовой ввод')
              : 'Отправить'"
            :aria-label="composerActionIsMic
              ? (chatSttActive && sttRecording ? 'Остановить и отправить' : 'Записать голос')
              : 'Отправить'"
            :disabled="busy || (!!activeSessionId && timerPaused) || therapyDone || !llmAllowed
              || (sttBusy && sttTarget === 'chat')
              || (!composerActionIsMic && !composerHasText)
              || (sttRecording && sttTarget === 'supervisor')"
            @pointerdown.prevent="onComposerAction"
          >
            <span class="material-symbols-outlined" aria-hidden="true">
              {{
                chatSttActive && sttBusy
                  ? 'progress_activity'
                  : chatSttActive && sttRecording
                    ? 'stop_circle'
                    : composerActionIsMic
                      ? 'mic'
                      : 'send'
              }}
            </span>
          </button>
        </div>
      </section>

      <aside v-if="supervisorOpen" class="supervisor-side">
        <div class="pane-head">
          <div class="pane-title">Супервизор</div>
          <p class="pane-sub">Спросите о процессе, формулировках, границах.</p>
        </div>
        <div class="messages supervisor-side-messages" ref="supervisorMessagesEl">
          <div v-for="m in supervisor" :key="m.id" class="bubble" :class="m.role">
            <strong>{{ m.role === 'SUPERVISOR' ? 'Супервизор' : 'Вы' }}: </strong>{{ m.content }}
          </div>
          <div
            v-if="awaitingSupervisor"
            class="bubble SUPERVISOR typing-bubble"
            aria-live="polite"
            aria-label="Супервизор печатает"
          >
            <strong>Супервизор: </strong>
            <span class="typing-dots" aria-hidden="true">
              <span></span><span></span><span></span>
            </span>
          </div>
          <p v-if="!supervisor.length && !awaitingSupervisor" class="empty-hint">Пока нет вопросов.</p>
        </div>
        <div class="composer chat-composer supervisor-composer">
          <textarea
            ref="supervisorInputEl"
            class="composer-input"
            rows="1"
            v-model="supervisorDraft"
            enterkeyhint="send"
            :disabled="busy || !llmAllowed || (sttBusy && sttTarget === 'supervisor')"
            :placeholder="llmAllowed ? 'Спросить супервизора…' : INSUFFICIENT_FUNDS_MSG"
            @input="resizeComposer(supervisorInputEl)"
            @keydown.enter.exact.prevent="askSupervisor"
          />
          <button
            class="composer-send"
            type="button"
            :class="{
              'is-mic': supervisorActionIsMic && !supervisorSttActive,
              recording: supervisorSttActive && sttRecording,
              busy: supervisorSttActive && sttBusy
            }"
            :title="supervisorActionIsMic
              ? (supervisorSttActive && sttRecording ? 'Стоп и отправить' : supervisorSttActive && sttBusy ? 'Распознаём…' : 'Голосовой ввод')
              : 'Спросить'"
            :aria-label="supervisorActionIsMic
              ? (supervisorSttActive && sttRecording ? 'Остановить и отправить' : 'Записать голос')
              : 'Спросить'"
            :disabled="busy || !llmAllowed
              || (sttBusy && sttTarget === 'supervisor')
              || (!supervisorActionIsMic && !supervisorHasText)
              || (sttRecording && sttTarget === 'chat')"
            @pointerdown.prevent="onSupervisorComposerAction"
          >
            <span class="material-symbols-outlined" aria-hidden="true">
              {{
                supervisorSttActive && sttBusy
                  ? 'progress_activity'
                  : supervisorSttActive && sttRecording
                    ? 'stop_circle'
                    : supervisorActionIsMic
                      ? 'mic'
                      : 'send'
              }}
            </span>
          </button>
        </div>
      </aside>
    </div>
  </div>
</template>
