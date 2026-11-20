<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { adminNavItems, adminSection, setAdminSection, type AdminSection } from '../adminUi'
import { api } from '../api/client'
import AiWorkingBlock from '../components/AiWorkingBlock.vue'
import NiceDateTime from '../components/NiceDateTime.vue'
import NiceSelect from '../components/NiceSelect.vue'
import { formatRub } from '../formatRub'
import { fillUsageChartSeries } from '../usageChartSeries'
import {
  buildGeneralUsageCards,
  canExpandGeneral,
  generalItemUnit
} from '../usageGeneral'
import { usageSourceLabel } from '../labels'
import { appBalanceRub, meProfile, setMeProfile, usersBalanceTotalRub } from '../meProfile'
import type {
  AdminModelOption,
  AdminOverview,
  AdminUsageChartMetric,
  AdminUsageStats,
  AppUser,
  BalanceTopUp,
  PromptDetail,
  PromptSummary
} from '../types'

const TOP_UP_PRESETS = [100, 500, 1000] as const
type TopUpPreset = (typeof TOP_UP_PRESETS)[number] | 'custom'

const overview = ref<AdminOverview | null>(null)
const models = ref<AdminModelOption[]>([])
const usage = ref<AdminUsageStats | null>(null)
/** Stable usage→therapist options; not wiped when filtering by a single user (byUser is empty then). */
const usageUserDirectory = ref<{ value: string; label: string; hint?: string }[]>([])
const prompts = ref<PromptSummary[]>([])
const users = ref<AppUser[]>([])
const topUpHistory = ref<BalanceTopUp[]>([])
const selectedPromptId = ref('')
const promptDetail = ref<PromptDetail | null>(null)
const modelDraft = ref('')
const modelFilter = ref('')
const baseUrlDraft = ref('')
const apiKeyDraft = ref('')
const embeddingModelDraft = ref('')
const loading = ref(true)
const usageLoading = ref(false)
const chartMetric = ref<AdminUsageChartMetric>('cost')
const filterFrom = ref('')
const filterTo = ref('')
const filterModel = ref('')
const filterSource = ref('')
const filterUser = ref('')
const usageFiltersReady = ref(false)
let usageRequestId = 0
const savingModel = ref(false)
const savingConnection = ref(false)
const savingPrompt = ref(false)
const savingUser = ref(false)
const topUpFor = ref<AppUser | null>(null)
const topUpPreset = ref<TopUpPreset>(500)
const topUpAmount = ref('500')
const toppingUp = ref(false)
const topUpError = ref('')
const topUpAmountInput = ref<HTMLInputElement | null>(null)
const error = ref('')
const notice = ref('')
const expandedClientKey = ref<string | null>(null)
const expandedGeneralKey = ref<string | null>(null)

const usageByClientCards = computed(() =>
  (usage.value?.byClient || []).filter(row => !!row.clientId)
)

const usageByGeneralCards = computed(() => buildGeneralUsageCards(usage.value))

const newUser = ref({
  username: '',
  password: '',
  email: '',
  displayName: '',
  admin: false
})

const sectionTitles: Record<AdminSection, { title: string; sub: string }> = {
  connection: {
    title: 'Подключение',
    sub: 'Base URL и API key RouterAI без перезапуска.'
  },
  balance: {
    title: 'Баланс',
    sub: 'Остаток и лимит ключа RouterAI.'
  },
  models: {
    title: 'Модели',
    sub: 'Выбор модели из каталога RouterAI.'
  },
  usage: {
    title: 'Использование',
    sub: 'Расходы, токены и запросы по логам PSY.'
  },
  prompts: {
    title: 'Промпты',
    sub: 'Редактирование system-промптов в MinIO.'
  },
  users: {
    title: 'Пользователи',
    sub: 'Учётки, роли и пополнение внутреннего баланса (₽).'
  }
}

const head = computed(() => sectionTitles[adminSection.value])

const appBalanceDisplay = computed(() => {
  if (overview.value?.balanceRub != null) return overview.value.balanceRub
  return appBalanceRub.value
})

const usersTotalDisplay = computed(() => usersBalanceTotalRub.value)

const filteredModels = computed(() => {
  const q = modelFilter.value.trim().toLowerCase()
  const list = !q
    ? models.value
    : models.value.filter(m =>
        m.id.toLowerCase().includes(q)
        || m.name.toLowerCase().includes(q)
        || formatModelPrice(m).toLowerCase().includes(q)
      )
  return list.slice(0, q ? 80 : 40)
})

const selectedModel = computed(() =>
  models.value.find(m => m.id === modelDraft.value) || null
)

function formatPer1m(n?: number | null): string {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const v = Number(n)
  if (v === 0) return '0 ₽'
  if (v < 0.01) return `${v.toLocaleString('ru-RU', { maximumFractionDigits: 4 })} ₽`
  return `${v.toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ₽`
}

function formatModelPrice(m: AdminModelOption): string {
  return `вход ${formatPer1m(m.promptPer1mRub)} / выход ${formatPer1m(m.completionPer1mRub)}`
}

function pickModel(id: string) {
  modelDraft.value = id
}

function seriesValue(row: { calls: number; tokens: number; costRub: number }) {
  if (chartMetric.value === 'tokens') return Number(row.tokens) || 0
  if (chartMetric.value === 'calls') return Number(row.calls) || 0
  return Number(row.costRub) || 0
}

const chartSeries = computed(() =>
  fillUsageChartSeries(
    usage.value?.byDay,
    filterFrom.value,
    filterTo.value,
    usage.value?.granularity
  )
)

const dayMax = computed(() => {
  const rows = chartSeries.value
  return Math.max(1, ...rows.map(seriesValue))
})

const chartYTicks = computed(() => {
  const max = dayMax.value
  const steps = 4
  return Array.from({ length: steps + 1 }, (_, i) => (max * (steps - i)) / steps)
})

function formatLocalInput(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function startOfTodayLocal() {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

function endOfTodayLocal() {
  const d = new Date()
  d.setHours(23, 59, 59, 999)
  return d
}

function resetUsageFilters() {
  const to = endOfTodayLocal()
  const from = startOfTodayLocal()
  from.setDate(from.getDate() - 6)
  filterFrom.value = formatLocalInput(from)
  filterTo.value = formatLocalInput(to)
  filterModel.value = ''
  filterSource.value = ''
  filterUser.value = ''
}

function localInputToIso(value: string) {
  if (!value) return undefined
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return undefined
  return d.toISOString()
}

function formatBucketLabel(raw: string) {
  if (!raw) return ''
  if (raw.includes(' ')) {
    const [date, time] = raw.split(' ')
    const parts = date.split('-')
    const day = parts[2] ? String(Number(parts[2])) : date.slice(8)
    const month = parts[1] || date.slice(5, 7)
    return `${day}.${month} ${time.slice(0, 5)}`
  }
  const parts = raw.split('-')
  if (parts.length >= 3) {
    return `${Number(parts[2])}.${parts[1]}`
  }
  return raw.slice(5)
}

function formatChartTick(n: number) {
  if (chartMetric.value === 'cost') {
    return formatRub(n)
  }
  if (chartMetric.value === 'tokens') {
    if (n >= 1000) return `${(n / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}K`
    return n.toLocaleString('ru-RU')
  }
  return n.toLocaleString('ru-RU')
}

function formatBarValue(row: { calls: number; tokens: number; costRub: number }) {
  return formatChartTick(seriesValue(row))
}

const chartDense = computed(() => chartSeries.value.length > 14)

function barHeightPct(row: { calls: number; tokens: number; costRub: number }) {
  const value = seriesValue(row)
  if (value <= 0) return 0
  return Math.max(3, (value / dayMax.value) * 100)
}

function formatTokens(n?: number | null) {
  const v = Number(n) || 0
  if (v >= 1_000_000) return `${(v / 1_000_000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}M`
  if (v >= 1000) return `${(v / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}K`
  return v.toLocaleString('ru-RU')
}

function sourceLabel(source: string, scope: 'client' | 'session' = 'client') {
  return usageSourceLabel(source, scope)
}

function clientKey(row: { clientId?: string | null; clientName: string }) {
  return row.clientId || row.clientName
}

function toggleClient(row: { clientId?: string | null; clientName: string }) {
  const key = clientKey(row)
  expandedClientKey.value = expandedClientKey.value === key ? null : key
}

function toggleGeneral(source: string) {
  const card = usageByGeneralCards.value.find(c => c.source === source)
  if (!card || !canExpandGeneral(card)) return
  expandedGeneralKey.value = expandedGeneralKey.value === source ? null : source
}

function sessionStatusLabel(status?: string | null) {
  if (status === 'ACTIVE') return 'активный'
  if (status === 'CLOSED') return 'завершён'
  return status || ''
}

const sourceOptions = computed(() => [
  { value: '', label: 'Все источники' },
  ...(usage.value?.availableSources || []).map(s => ({
    value: s,
    label: sourceLabel(s),
    hint: s
  }))
])

const modelOptions = computed(() => [
  { value: '', label: 'Все модели' },
  ...(usage.value?.availableModels || []).map(m => ({
    value: m,
    label: m
  }))
])

function refreshUsageUserDirectory(stats: AdminUsageStats | null | undefined) {
  // Filtered-by-user responses omit byUser — keep the previous directory.
  if (filterUser.value) return
  const rows = stats?.byUser
  if (!rows?.length) return
  const next: { value: string; label: string; hint?: string }[] = []
  const seen = new Set<string>()
  for (const row of rows) {
    if (!row.therapistId || seen.has(row.therapistId)) continue
    seen.add(row.therapistId)
    next.push({
      value: row.therapistId,
      label: row.displayName,
      hint: row.email || undefined
    })
  }
  usageUserDirectory.value = next
}

const userOptions = computed(() => {
  const byId = new Map<string, { value: string; label: string; hint?: string }>()
  const appUserIdsCovered = new Set<string>()

  for (const opt of usageUserDirectory.value) {
    byId.set(opt.value, opt)
    const matched = users.value.find(u =>
      (opt.hint && u.email && opt.hint === u.email)
      || (u.displayName && u.displayName === opt.label)
      || u.username === opt.label
    )
    if (matched) appUserIdsCovered.add(matched.id)
  }

  for (const u of users.value) {
    if (appUserIdsCovered.has(u.id)) continue
    const label = u.displayName?.trim() || u.username
    byId.set(u.id, {
      value: u.id,
      label,
      hint: u.email || u.username
    })
  }

  // Keep the current selection visible even if directory/users momentarily diverge.
  if (filterUser.value && !byId.has(filterUser.value)) {
    const u = users.value.find(x => x.id === filterUser.value)
    byId.set(filterUser.value, {
      value: filterUser.value,
      label: u?.displayName?.trim() || u?.username || 'Пользователь',
      hint: u?.email || undefined
    })
  }

  return [
    { value: '', label: 'Все пользователи' },
    ...[...byId.values()].sort((a, b) => a.label.localeCompare(b.label, 'ru'))
  ]
})

const selectedUserLabel = computed(() => {
  if (!filterUser.value) return 'Все пользователи'
  return userOptions.value.find(o => o.value === filterUser.value)?.label || 'Пользователь'
})

const promptOptions = computed(() =>
  prompts.value.map(p => ({
    value: p.id,
    label: p.label,
    hint: p.id
  }))
)

async function loadUsage() {
  const reqId = ++usageRequestId
  usageLoading.value = true
  try {
    const next = await api.adminUsage({
      from: localInputToIso(filterFrom.value),
      to: localInputToIso(filterTo.value),
      model: filterModel.value || undefined,
      source: filterSource.value || undefined,
      therapistId: filterUser.value || undefined
    })
    if (reqId !== usageRequestId) return
    usage.value = next
    refreshUsageUserDirectory(next)
    expandedClientKey.value = null
    expandedGeneralKey.value = null
  } catch (e) {
    if (reqId !== usageRequestId) return
    error.value = e instanceof Error ? e.message : 'Не удалось загрузить использование'
  } finally {
    if (reqId === usageRequestId) usageLoading.value = false
  }
}

async function selectUsageUser(therapistId?: string | null) {
  if (!therapistId) return
  filterUser.value = therapistId
}

const limitPct = computed(() => {
  const lim = Number(overview.value?.keyLimitRub || 0)
  const used = Number(overview.value?.keyUsageMonthlyRub || overview.value?.keyUsageRub || 0)
  if (!lim || lim <= 0) return 0
  return Math.min(100, Math.round((used / lim) * 100))
})

function rub(n?: number | null) {
  return formatRub(n)
}

function formatTopUpWhen(iso?: string | null) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('ru-RU', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function applyOverview(ov: AdminOverview) {
  overview.value = ov
  modelDraft.value = ov.currentModel
  baseUrlDraft.value = ov.baseUrl || ''
  embeddingModelDraft.value = ov.embeddingModel || ''
  apiKeyDraft.value = ''
  if (meProfile.value && ov.balanceRub != null) {
    setMeProfile({ ...meProfile.value, appBalanceRub: ov.balanceRub })
  }
}

async function loadUsers() {
  users.value = await api.adminUsers()
}

async function loadTopUpHistory() {
  topUpHistory.value = await api.adminTopUpHistory().catch(() => [] as BalanceTopUp[])
}

async function loadAll() {
  loading.value = true
  usageFiltersReady.value = false
  error.value = ''
  resetUsageFilters()
  filterModel.value = ''
  filterSource.value = ''
  filterUser.value = ''
  try {
    const [ov, us, pr, usrs, history, me] = await Promise.all([
      api.adminOverview(),
      api.adminUsage({
        from: localInputToIso(filterFrom.value),
        to: localInputToIso(filterTo.value)
      }),
      api.adminPrompts(),
      api.adminUsers().catch(() => [] as AppUser[]),
      api.adminTopUpHistory().catch(() => [] as BalanceTopUp[]),
      api.me().catch(() => null)
    ])
    applyOverview(ov)
    usage.value = us
    refreshUsageUserDirectory(us)
    prompts.value = pr
    users.value = usrs
    topUpHistory.value = history
    if (me) {
      setMeProfile({
        ...me,
        appBalanceRub: ov.balanceRub ?? me.appBalanceRub
      })
    }
    if (!selectedPromptId.value && pr.length) {
      selectedPromptId.value = pr[0].id
    }
    if (ov.apiKeyConfigured) {
      try {
        models.value = await api.adminModels()
      } catch {
        models.value = []
      }
    } else {
      models.value = []
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось загрузить админку'
  } finally {
    loading.value = false
    usageFiltersReady.value = true
  }
}

async function createUser() {
  if (!newUser.value.username.trim() || !newUser.value.password) {
    error.value = 'Укажите логин и пароль'
    return
  }
  savingUser.value = true
  notice.value = ''
  error.value = ''
  try {
    await api.adminCreateUser({
      username: newUser.value.username.trim(),
      password: newUser.value.password,
      email: newUser.value.email.trim() || undefined,
      displayName: newUser.value.displayName.trim() || undefined,
      admin: newUser.value.admin
    })
    notice.value = `Пользователь ${newUser.value.username.trim()} создан`
    newUser.value = {
      username: '',
      password: '',
      email: '',
      displayName: '',
      admin: false
    }
    await loadUsers()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось создать пользователя'
  } finally {
    savingUser.value = false
  }
}

function openTopUp(u: AppUser) {
  topUpFor.value = u
  topUpPreset.value = 500
  topUpAmount.value = '500'
  topUpError.value = ''
  error.value = ''
}

function closeTopUp() {
  if (toppingUp.value) return
  topUpFor.value = null
  topUpError.value = ''
}

function selectTopUpPreset(preset: TopUpPreset) {
  topUpPreset.value = preset
  topUpError.value = ''
  if (preset === 'custom') {
    topUpAmount.value = ''
    nextTick(() => topUpAmountInput.value?.focus())
    return
  }
  topUpAmount.value = String(preset)
}

async function confirmTopUp() {
  const u = topUpFor.value
  if (!u || toppingUp.value) return
  const raw = String(topUpAmount.value ?? '').trim().replace(',', '.')
  const amount = Number(raw)
  if (!Number.isFinite(amount) || amount <= 0) {
    topUpError.value = 'Укажите сумму больше 0'
    return
  }
  toppingUp.value = true
  topUpError.value = ''
  notice.value = ''
  error.value = ''
  try {
    const updated = await api.adminTopUpUser(u.id, amount)
    users.value = users.value.map(row => (row.id === updated.id ? updated : row))
    notice.value = `Баланс ${u.username}: ${rub(updated.balanceRub)}`
    topUpFor.value = null
    await Promise.all([
      loadTopUpHistory(),
      api.me().then(setMeProfile).catch(() => undefined)
    ])
  } catch (e) {
    topUpError.value = e instanceof Error ? e.message : 'Не удалось пополнить баланс'
  } finally {
    toppingUp.value = false
  }
}

async function saveConnection() {
  if (!baseUrlDraft.value.trim()) {
    error.value = 'Укажите Base URL'
    return
  }
  if (!embeddingModelDraft.value.trim() && !overview.value?.embeddingModel) {
    error.value = 'Укажите embedding-модель'
    return
  }
  savingConnection.value = true
  notice.value = ''
  error.value = ''
  try {
    const body: { baseUrl: string; apiKey?: string; embeddingModel?: string } = {
      baseUrl: baseUrlDraft.value.trim(),
      embeddingModel: embeddingModelDraft.value.trim() || undefined
    }
    if (apiKeyDraft.value.trim()) {
      body.apiKey = apiKeyDraft.value.trim()
    }
    applyOverview(await api.adminSetConnection(body))
    notice.value = 'Base URL и ключ применены без перезапуска'
    if (overview.value?.apiKeyConfigured) {
      try {
        models.value = await api.adminModels()
      } catch {
        models.value = []
      }
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось сохранить подключение'
  } finally {
    savingConnection.value = false
  }
}

async function saveModel() {
  if (!modelDraft.value.trim()) return
  savingModel.value = true
  notice.value = ''
  error.value = ''
  try {
    applyOverview(await api.adminSetModel(modelDraft.value.trim()))
    notice.value = 'Модель применена без перезапуска'
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось сменить модель'
  } finally {
    savingModel.value = false
  }
}

async function loadPrompt(id: string) {
  if (!id) return
  try {
    promptDetail.value = await api.adminPrompt(id)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось загрузить промпт'
  }
}

async function savePrompt() {
  if (!promptDetail.value) return
  savingPrompt.value = true
  notice.value = ''
  error.value = ''
  try {
    promptDetail.value = await api.adminSavePrompt(promptDetail.value.id, promptDetail.value.content)
    prompts.value = await api.adminPrompts()
    notice.value = 'Промпт сохранён в MinIO'
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось сохранить промпт'
  } finally {
    savingPrompt.value = false
  }
}

watch(selectedPromptId, id => {
  if (id) void loadPrompt(id)
})

watch(adminSection, () => {
  notice.value = ''
  error.value = ''
})

watch(
  [filterFrom, filterTo, filterModel, filterSource, filterUser],
  () => {
    if (!usageFiltersReady.value) return
    void loadUsage()
  }
)

onMounted(loadAll)
</script>

<template>
  <Teleport defer to="#admin-nav-slot">
    <div class="admin-nav">
      <div class="section-label left-scroll-label">Админ</div>
      <div class="admin-app-balance" v-if="overview || appBalanceDisplay != null">
        <span class="muted">Баланс приложения</span>
        <strong>{{ rub(appBalanceDisplay) }}</strong>
        <span v-if="usersTotalDisplay != null" class="muted tiny">
          выдано пользователям: {{ rub(usersTotalDisplay) }}
        </span>
      </div>
      <div class="segmented-list admin-nav-list" role="tablist" aria-label="Разделы админки">
        <button
          v-for="item in adminNavItems"
          :key="item.id"
          type="button"
          role="tab"
          class="list-item admin-nav-item"
          :class="{ active: adminSection === item.id }"
          :aria-selected="adminSection === item.id"
          @click="setAdminSection(item.id)"
        >
          <span class="material-symbols-outlined admin-nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
        </button>
      </div>
    </div>
  </Teleport>

  <div class="admin-page page-scroll">
    <div class="admin-app-balance admin-app-balance-mobile" v-if="overview || appBalanceDisplay != null">
      <span class="muted">Баланс приложения</span>
      <strong>{{ rub(appBalanceDisplay) }}</strong>
      <span v-if="usersTotalDisplay != null" class="muted tiny">
        выдано пользователям: {{ rub(usersTotalDisplay) }}
      </span>
    </div>
    <div class="page-head compact">
      <div class="section-label">Админка</div>
      <h2 class="page-title">{{ head.title }}</h2>
      <p class="page-sub">
        {{ head.sub }}
        <a
          v-if="overview?.docsUrl && (adminSection === 'balance' || adminSection === 'models')"
          :href="overview.docsUrl"
          target="_blank"
          rel="noopener"
        >API reference →</a>
      </p>
    </div>

    <p v-if="loading" class="muted">Загрузка…</p>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="notice" class="ok-text">{{ notice }}</p>

    <template v-if="overview && !loading">
      <section v-show="adminSection === 'connection'" class="settings-section">
        <p class="muted settings-hint">
          LLM настраивается только здесь и хранится в БД (`app_setting`).
        </p>
        <label class="admin-field">
          <span>Base URL</span>
          <input v-model="baseUrlDraft" class="field" placeholder="https://routerai.ru/api/v1" />
        </label>
        <label class="admin-field">
          <span>API key</span>
          <input
            v-model="apiKeyDraft"
            class="field"
            type="password"
            autocomplete="off"
            :placeholder="overview.apiKeyConfigured ? `Сохранён: ${overview.apiKeyMasked}` : 'sk-…'"
          />
        </label>
        <label class="admin-field">
          <span>Embedding model</span>
          <input
            v-model="embeddingModelDraft"
            class="field"
            placeholder="`openai/text-embedding-3-small"
          />
        </label>
        <p class="muted settings-hint">
          Оставьте ключ пустым, чтобы не менять текущий.
          Сейчас: {{ overview.apiKeyConfigured ? overview.apiKeyMasked : 'не задан' }}
        </p>
        <button type="button" class="toolbar-btn" :disabled="savingConnection" @click="saveConnection">
          <span class="material-symbols-outlined">{{ savingConnection ? 'progress_activity' : 'link' }}</span>
          <span>Сохранить подключение</span>
        </button>
      </section>

      <section v-show="adminSection === 'balance'" class="settings-section">
        <p v-if="overview.billingError" class="error-text">{{ overview.billingError }}</p>
        <div class="admin-stats">
          <div class="admin-stat">
            <span class="muted">Баланс (/credits)</span>
            <strong>{{ rub(overview.balanceRub) }}</strong>
          </div>
          <div class="admin-stat">
            <span class="muted">Лимит ключа (/key)</span>
            <strong>{{ rub(overview.keyLimitRemainingRub) }}</strong>
            <span class="muted tiny">осталось из {{ rub(overview.keyLimitRub) }}</span>
          </div>
          <div class="admin-stat">
            <span class="muted">Расход за период</span>
            <strong>{{ rub(overview.keyUsageMonthlyRub ?? overview.keyUsageRub) }}</strong>
            <span class="muted tiny">{{ overview.keyLimitReset || 'month' }}</span>
          </div>
        </div>
        <div class="admin-meter" aria-hidden="true">
          <div class="admin-meter-fill" :style="{ width: limitPct + '%' }" />
        </div>
        <p class="muted settings-hint">
          Ключ: {{ overview.keyName || '—' }}
          <a :href="overview.activityUrl" target="_blank" rel="noopener">activity →</a>
        </p>
      </section>

      <section v-show="adminSection === 'models'" class="settings-section">
        <p class="muted settings-hint">
          Текущая модель чата:
          <code>{{ overview.currentModel || 'не задана' }}</code>
          <template v-if="selectedModel">
            · {{ formatModelPrice(selectedModel) }}
            <span v-if="selectedModel.contextLength"> · ctx {{ selectedModel.contextLength.toLocaleString('ru-RU') }}</span>
          </template>
        </p>
        <label class="admin-field">
          <span>Поиск модели</span>
          <input
            v-model="modelFilter"
            class="field"
            type="search"
            autocomplete="off"
            placeholder="Название, id или цена…"
          />
        </label>
        <div class="model-picker" role="listbox" aria-label="Каталог моделей">
          <div
            v-for="m in filteredModels"
            :key="m.id"
            class="model-picker-item"
            role="option"
            tabindex="0"
            :aria-selected="modelDraft === m.id"
            :class="{ selected: modelDraft === m.id }"
            @click="pickModel(m.id)"
            @keydown.enter.prevent="pickModel(m.id)"
            @keydown.space.prevent="pickModel(m.id)"
          >
            <div class="model-picker-title">{{ m.name }}</div>
            <div class="model-picker-id">{{ m.id }}</div>
            <div class="model-picker-prices">
              <span class="model-price-chip">вход {{ formatPer1m(m.promptPer1mRub) }}/1M</span>
              <span class="model-price-chip">выход {{ formatPer1m(m.completionPer1mRub) }}/1M</span>
              <span v-if="m.contextLength" class="model-price-chip muted">ctx {{ m.contextLength.toLocaleString('ru-RU') }}</span>
            </div>
          </div>
          <p v-if="!models.length" class="muted settings-hint model-picker-empty">
            Каталог пуст — проверьте API key и Base URL на вкладке «Подключение».
          </p>
          <p v-else-if="!filteredModels.length" class="muted settings-hint model-picker-empty">
            Ничего не найдено по «{{ modelFilter }}».
          </p>
        </div>
        <div class="admin-model-row">
          <input v-model="modelDraft" class="field" placeholder="provider/model-id" />
          <button type="button" class="toolbar-btn" :disabled="savingModel || !modelDraft" @click="saveModel">
            <span class="material-symbols-outlined">{{ savingModel ? 'progress_activity' : 'save' }}</span>
            <span>Применить</span>
          </button>
        </div>
        <p v-if="models.length" class="muted settings-hint">
          Показано {{ filteredModels.length }} из {{ models.length }}
          <template v-if="!modelFilter.trim()"> · начните вводить запрос, чтобы сузить список</template>
          · цены в ₽ за 1 млн токенов
        </p>
      </section>

      <section v-show="adminSection === 'usage'" class="settings-section usage-dashboard">
        <div class="usage-filters">
          <label class="admin-field usage-filter-from">
            <span>С</span>
            <NiceDateTime v-model="filterFrom" aria-label="Дата начала" placeholder="Начало периода" />
          </label>
          <label class="admin-field usage-filter-to">
            <span>По</span>
            <NiceDateTime
              v-model="filterTo"
              disable-future
              aria-label="Дата конца"
              placeholder="Конец периода"
            />
          </label>
          <label class="admin-field usage-filter-user">
            <span>Пользователь</span>
            <NiceSelect
              v-model="filterUser"
              :options="userOptions"
              placeholder="Все пользователи"
              aria-label="Пользователь"
            />
          </label>
          <label class="admin-field usage-filter-source">
            <span>Источник</span>
            <NiceSelect
              v-model="filterSource"
              :options="sourceOptions"
              placeholder="Все источники"
              aria-label="Источник"
            />
          </label>
          <label class="admin-field usage-filter-model">
            <span>Модель</span>
            <NiceSelect
              v-model="filterModel"
              :options="modelOptions"
              placeholder="Все модели"
              aria-label="Модель"
            />
          </label>
        </div>

        <div class="usage-metric-cards">
          <div class="usage-metric-card">
            <div class="usage-metric-copy">
              <span class="muted">Общая стоимость</span>
              <strong>{{ rub(usage?.totalCostRub) }}</strong>
            </div>
            <span class="material-symbols-outlined usage-metric-icon" aria-hidden="true">account_balance_wallet</span>
          </div>
          <div class="usage-metric-card">
            <div class="usage-metric-copy">
              <span class="muted">Количество моделей</span>
              <strong>{{ (usage?.modelCount ?? usage?.byModel?.length ?? 0).toLocaleString('ru-RU') }}</strong>
            </div>
            <span class="material-symbols-outlined usage-metric-icon" aria-hidden="true">stacks</span>
          </div>
          <div class="usage-metric-card">
            <div class="usage-metric-copy">
              <span class="muted">Всего запросов</span>
              <strong>{{ (usage?.totalCalls || 0).toLocaleString('ru-RU') }}</strong>
            </div>
            <span class="material-symbols-outlined usage-metric-icon" aria-hidden="true">monitoring</span>
          </div>
          <div class="usage-metric-card">
            <div class="usage-metric-copy">
              <span class="muted">Всего токенов</span>
              <strong>{{ formatTokens(usage?.totalTokens) }}</strong>
            </div>
            <span class="material-symbols-outlined usage-metric-icon" aria-hidden="true">psychology</span>
          </div>
        </div>

        <div class="usage-chart-card">
          <div class="usage-chart-head">
            <div class="section-label" style="margin: 0">График расходов</div>
            <div class="usage-metric-toggle" role="tablist" aria-label="Метрика графика">
              <button
                type="button"
                role="tab"
                :class="{ active: chartMetric === 'cost' }"
                :aria-selected="chartMetric === 'cost'"
                @click="chartMetric = 'cost'"
              >Рубли</button>
              <button
                type="button"
                role="tab"
                :class="{ active: chartMetric === 'tokens' }"
                :aria-selected="chartMetric === 'tokens'"
                @click="chartMetric = 'tokens'"
              >Токены</button>
              <button
                type="button"
                role="tab"
                :class="{ active: chartMetric === 'calls' }"
                :aria-selected="chartMetric === 'calls'"
                @click="chartMetric = 'calls'"
              >Запросы</button>
            </div>
          </div>

          <div v-if="!chartSeries.length" class="muted usage-chart-empty">
            Пока нет данных за выбранный период — появятся после LLM-запросов.
          </div>
          <div v-else class="usage-chart-body" :class="{ dense: chartDense }">
            <div class="usage-chart-y" aria-hidden="true">
              <span v-for="(tick, i) in chartYTicks" :key="i">{{ formatChartTick(tick) }}</span>
            </div>
            <div class="usage-chart-plot">
              <div class="usage-chart-grid" aria-hidden="true">
                <div v-for="(_, i) in chartYTicks" :key="`g-${i}`" class="usage-chart-grid-line" />
              </div>
              <div class="bar-chart usage-bar-chart">
                <div
                  v-for="row in chartSeries"
                  :key="row.day"
                  class="bar-col"
                  :title="`${formatBucketLabel(row.day)}: ${rub(row.costRub)} · ${row.calls} · ${formatTokens(row.tokens)}`"
                >
                  <div
                    class="bar"
                    :class="{ empty: seriesValue(row) <= 0 }"
                    :style="{ height: `${barHeightPct(row)}%` }"
                  />
                </div>
              </div>
              <div class="usage-chart-x">
                <div
                  v-for="row in chartSeries"
                  :key="`x-${row.day}`"
                  class="usage-chart-x-tick"
                  :title="`${formatBucketLabel(row.day)}: ${rub(row.costRub)} · ${row.calls} · ${formatTokens(row.tokens)}`"
                >
                  <strong class="usage-chart-x-value">{{ formatBarValue(row) }}</strong>
                  <span class="usage-chart-x-day">{{ formatBucketLabel(row.day) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">Общее</div>
          <p class="muted settings-hint">
            <template v-if="filterUser"> · {{ selectedUserLabel }}</template>
          </p>
          <div v-if="!usageByGeneralCards.length" class="muted">Пока нет общих расходов.</div>
          <div v-else class="usage-client-list">
            <div
              v-for="row in usageByGeneralCards"
              :key="row.source"
              class="usage-client-card list-item"
              :class="{
                expanded: expandedGeneralKey === row.source,
                'usage-source-card': !canExpandGeneral(row)
              }"
              :role="canExpandGeneral(row) ? 'button' : undefined"
              :tabindex="canExpandGeneral(row) ? 0 : undefined"
              :aria-expanded="canExpandGeneral(row) ? expandedGeneralKey === row.source : undefined"
              @click="canExpandGeneral(row) ? toggleGeneral(row.source) : undefined"
              @keydown.enter.prevent="canExpandGeneral(row) ? toggleGeneral(row.source) : undefined"
              @keydown.space.prevent="canExpandGeneral(row) ? toggleGeneral(row.source) : undefined"
            >
              <div class="list-item-top">
                <div class="list-item-title usage-client-title">
                  <span
                    v-if="canExpandGeneral(row)"
                    class="material-symbols-outlined usage-client-chevron"
                    aria-hidden="true"
                  >
                    {{ expandedGeneralKey === row.source ? 'expand_more' : 'chevron_right' }}
                  </span>
                  <strong>{{ sourceLabel(row.source) }}</strong>
                </div>
                <strong class="usage-client-total">{{ rub(row.costRub) }}</strong>
              </div>
              <div class="muted tiny">
                {{ row.calls.toLocaleString('ru-RU') }} запросов · {{ formatTokens(row.tokens) }} токенов
                <template v-if="row.items?.length">
                  · {{ row.items.length.toLocaleString('ru-RU') }} {{ generalItemUnit(row.source) }}
                </template>
              </div>

              <div
                v-if="expandedGeneralKey === row.source && row.items?.length"
                class="usage-session-list"
                @click.stop
              >
                <div
                  v-for="item in row.items"
                  :key="item.refId || item.label"
                  class="usage-session-card"
                >
                  <div class="list-item-top">
                    <div class="usage-session-title">
                      <strong>{{ item.label }}</strong>
                    </div>
                    <strong class="usage-client-total">{{ rub(item.costRub) }}</strong>
                  </div>
                  <div class="muted tiny">
                    {{ item.calls.toLocaleString('ru-RU') }} запросов · {{ formatTokens(item.tokens) }} токенов
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">По клиентам</div>
          <p class="muted settings-hint">
            Нажмите на клиента, чтобы открыть статистику по сеансам.
            <template v-if="filterUser"> · {{ selectedUserLabel }}</template>
            <template v-else> · все пользователи</template>
          </p>
          <div v-if="!usageByClientCards.length" class="muted">Пока нет данных по клиентам.</div>
          <div v-else class="usage-client-list">
            <div
              v-for="row in usageByClientCards"
              :key="clientKey(row)"
              class="usage-client-card list-item"
              :class="{ expanded: expandedClientKey === clientKey(row) }"
              role="button"
              tabindex="0"
              :aria-expanded="expandedClientKey === clientKey(row)"
              @click="toggleClient(row)"
              @keydown.enter.prevent="toggleClient(row)"
              @keydown.space.prevent="toggleClient(row)"
            >
              <div class="list-item-top">
                <div class="list-item-title usage-client-title">
                  <span class="material-symbols-outlined usage-client-chevron" aria-hidden="true">
                    {{ expandedClientKey === clientKey(row) ? 'expand_more' : 'chevron_right' }}
                  </span>
                  <strong>{{ row.clientName }}</strong>
                  <span v-if="row.clientAge != null" class="muted">· {{ row.clientAge }}</span>
                </div>
                <strong class="usage-client-total">{{ rub(row.costRub) }}</strong>
              </div>
              <div class="muted tiny">
                {{ row.calls.toLocaleString('ru-RU') }} запросов · {{ formatTokens(row.tokens) }} токенов
                <template v-if="row.bySession?.some(s => s.sessionId)">
                  · {{ row.bySession.filter(s => s.sessionId).length.toLocaleString('ru-RU') }} сеанс.
                </template>
              </div>
              <div v-if="row.byPurpose?.length" class="usage-client-purposes usage-table-wrap usage-purpose-table-wrap">
                <table class="usage-table usage-purpose-table">
                  <thead>
                    <tr>
                      <th>Тип</th>
                      <th class="num">Запросы</th>
                      <th class="num">Токены</th>
                      <th class="num">₽</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in row.byPurpose" :key="`${clientKey(row)}-${p.source}`">
                      <td>{{ sourceLabel(p.source) }}</td>
                      <td class="num usage-col-calls">{{ p.calls.toLocaleString('ru-RU') }}</td>
                      <td class="num usage-col-tokens">{{ formatTokens(p.tokens) }}</td>
                      <td class="num usage-col-cost">{{ rub(p.costRub) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div
                v-if="expandedClientKey === clientKey(row)"
                class="usage-session-list"
                @click.stop
              >
                <div class="section-label">Сеансы</div>
                <div v-if="!row.bySession?.length" class="muted tiny">Нет разбивки по сеансам.</div>
                <div
                  v-for="session in row.bySession"
                  :key="session.sessionId || session.sessionLabel"
                  class="usage-session-card"
                >
                  <div class="list-item-top">
                    <div class="usage-session-title">
                      <strong>{{ session.sessionLabel }}</strong>
                      <span v-if="session.status" class="tag usage-session-status">
                        {{ sessionStatusLabel(session.status) }}
                      </span>
                    </div>
                    <strong class="usage-client-total">{{ rub(session.costRub) }}</strong>
                  </div>
                  <div class="muted tiny">
                    {{ session.calls.toLocaleString('ru-RU') }} запросов · {{ formatTokens(session.tokens) }} токенов
                  </div>
                  <div
                    v-if="session.byPurpose?.length"
                    class="usage-client-purposes usage-table-wrap usage-purpose-table-wrap"
                  >
                    <table class="usage-table usage-purpose-table">
                      <thead>
                        <tr>
                          <th>Тип</th>
                          <th class="num">Запросы</th>
                          <th class="num">Токены</th>
                          <th class="num">₽</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="p in session.byPurpose"
                          :key="`${session.sessionId || session.sessionLabel}-${p.source}`"
                        >
                          <td>{{ sourceLabel(p.source, 'session') }}</td>
                          <td class="num usage-col-calls">{{ p.calls.toLocaleString('ru-RU') }}</td>
                          <td class="num usage-col-tokens">{{ formatTokens(p.tokens) }}</td>
                          <td class="num usage-col-cost">{{ rub(p.costRub) }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">По моделям</div>
          <div v-if="!usage?.byModel?.length" class="muted">Нет агрегатов по моделям.</div>
          <div v-else class="usage-client-list">
            <div
              v-for="row in usage.byModel"
              :key="row.model"
              class="usage-client-card usage-source-card list-item"
            >
              <div class="list-item-top">
                <div class="list-item-title usage-client-title">
                  <strong>{{ row.model }}</strong>
                </div>
                <strong class="usage-client-total">{{ rub(row.costRub) }}</strong>
              </div>
              <div class="muted tiny">
                {{ row.calls.toLocaleString('ru-RU') }} запросов · {{ formatTokens(row.tokens) }} токенов
              </div>
            </div>
          </div>
        </div>

        <div v-if="!filterUser" class="admin-chart">
          <div class="section-label">По пользователям</div>
          <div v-if="!usage?.byUser?.length" class="muted">Пока нет данных по пользователям.</div>
          <div v-else class="usage-table-wrap">
            <table class="usage-table">
              <thead>
                <tr>
                  <th>Пользователь</th>
                  <th class="num">Запросы</th>
                  <th class="num">Токены</th>
                  <th class="num">₽</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in usage.byUser"
                  :key="row.therapistId || row.displayName"
                  class="usage-table-row"
                  :class="{ clickable: !!row.therapistId }"
                  :tabindex="row.therapistId ? 0 : undefined"
                  :title="row.therapistId ? 'Показать только этого пользователя' : undefined"
                  @click="selectUsageUser(row.therapistId)"
                  @keydown.enter.prevent="selectUsageUser(row.therapistId)"
                >
                  <td>
                    <strong>{{ row.displayName }}</strong>
                    <div v-if="row.email" class="muted tiny">{{ row.email }}</div>
                  </td>
                  <td class="num">{{ row.calls.toLocaleString('ru-RU') }}</td>
                  <td class="num">{{ formatTokens(row.tokens) }}</td>
                  <td class="num">{{ rub(row.costRub) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

      </section>

      <section v-show="adminSection === 'users'" class="settings-section">
        <div class="section-label">Новый пользователь</div>
        <div class="admin-user-form">
          <label class="admin-field">
            <span>Логин</span>
            <input v-model="newUser.username" class="field" autocomplete="off" placeholder="ivan" />
          </label>
          <label class="admin-field">
            <span>Пароль</span>
            <input v-model="newUser.password" class="field" type="password" autocomplete="new-password" />
          </label>
          <label class="admin-field">
            <span>Email</span>
            <input v-model="newUser.email" class="field" type="email" placeholder="ivan@psy.local" />
          </label>
          <label class="admin-field">
            <span>Отображаемое имя</span>
            <input v-model="newUser.displayName" class="field" />
          </label>
          <label class="case-share-all">
            <input v-model="newUser.admin" type="checkbox" />
            <span>Роль admin (+ therapist)</span>
          </label>
        </div>
        <p class="muted settings-hint">
          По умолчанию роль therapist, баланс 0 ₽. LLM-функции блокируются при балансе &lt; 0 —
          пополните пользователя ниже.
        </p>
        <button type="button" class="toolbar-btn" :disabled="savingUser" @click="createUser">
          <span class="material-symbols-outlined">{{ savingUser ? 'progress_activity' : 'person_add' }}</span>
          <span>Создать пользователя</span>
        </button>

        <div class="admin-chart">
          <div class="section-label">Пользователи</div>
          <div v-if="!users.length" class="muted">Список пуст.</div>
          <div v-else class="admin-user-list">
            <div v-for="u in users" :key="u.id" class="admin-user-row list-item">
              <div class="admin-user-row-main">
                <div class="list-item-top">
                  <div class="list-item-title">
                    <strong>{{ u.username }}</strong>
                    <span v-if="u.displayName" class="muted"> · {{ u.displayName }}</span>
                  </div>
                  <strong
                    class="admin-user-balance"
                    :class="{ 'balance-negative': Number(u.balanceRub ?? 0) < 0 }"
                  >{{ rub(u.balanceRub) }}</strong>
                </div>
                <div v-if="u.email" class="muted tiny">{{ u.email }}</div>
                <div class="tags">
                  <span v-for="r in u.roles" :key="r" class="tag">{{ r }}</span>
                  <span class="tag">{{ u.enabled ? 'активен' : 'выключен' }}</span>
                </div>
              </div>
              <button
                type="button"
                class="icon-btn"
                title="Пополнить баланс"
                aria-label="Пополнить баланс"
                :disabled="toppingUp"
                @click="openTopUp(u)"
              >
                <span class="material-symbols-outlined" aria-hidden="true">add_card</span>
              </button>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">История пополнений</div>
          <div v-if="!topUpHistory.length" class="muted">Пополнений пока нет.</div>
          <div v-else class="balance-history-list">
            <div
              v-for="row in topUpHistory"
              :key="row.id"
              class="balance-history-row list-item"
            >
              <div class="balance-history-main">
                <div class="list-item-top">
                  <div class="list-item-title">
                    <strong>{{ row.displayName || row.username }}</strong>
                    <span class="muted"> · @{{ row.username }}</span>
                  </div>
                  <strong class="balance-history-amount">+{{ rub(row.amountRub) }}</strong>
                </div>
                <div class="muted tiny">
                  {{ formatTopUpWhen(row.createdAt) }}
                  <span v-if="row.createdByUsername"> · {{ row.createdByUsername }}</span>
                  · остаток {{ rub(row.balanceAfterRub) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-show="adminSection === 'prompts'" class="settings-section admin-prompts-section">
        <div class="admin-prompt-toolbar">
          <label class="admin-field admin-prompt-select-field">
            <span>Промпт</span>
            <NiceSelect
              v-model="selectedPromptId"
              :options="promptOptions"
              placeholder="Выберите промпт…"
              aria-label="Промпт"
            />
          </label>
          <button
            type="button"
            class="toolbar-btn"
            :disabled="savingPrompt || !promptDetail"
            @click="savePrompt"
          >
            <span class="material-symbols-outlined">{{ savingPrompt ? 'progress_activity' : 'save' }}</span>
            <span>Сохранить</span>
          </button>
        </div>

        <p v-if="promptDetail" class="muted settings-hint admin-prompt-meta">
          <code>{{ promptDetail.id }}</code>
          · {{ promptDetail.content.length.toLocaleString('ru-RU') }} симв.
        </p>
        <p v-else-if="!prompts.length" class="muted settings-hint">Промпты не найдены в MinIO.</p>

        <textarea
          v-if="promptDetail"
          v-model="promptDetail.content"
          class="field admin-prompt-textarea"
          spellcheck="false"
          rows="22"
        />
      </section>
    </template>

    <Teleport to="body">
      <div
        v-if="topUpFor"
        class="case-start-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="admin-topup-title"
        @click.self="closeTopUp"
      >
        <div class="case-start-card" :class="{ 'is-working': toppingUp }">
          <div class="case-start-card-body">
            <div class="section-label">Пополнение баланса</div>
            <h3 id="admin-topup-title">{{ topUpFor.displayName || topUpFor.username }}</h3>
            <p class="case-start-hint muted">
              @{{ topUpFor.username }} · сейчас
              <strong :class="{ 'balance-negative': Number(topUpFor.balanceRub ?? 0) < 0 }">
                {{ rub(topUpFor.balanceRub) }}
              </strong>
            </p>

            <AiWorkingBlock
              v-if="toppingUp"
              compact
              title="Пополняем баланс"
              :statuses="['Сохраняем…']"
            />

            <template v-else>
              <div class="case-start-field">
                <span class="section-label">Сумма</span>
                <div class="case-start-seg" role="group" aria-label="Сумма пополнения">
                  <button
                    v-for="p in TOP_UP_PRESETS"
                    :key="p"
                    type="button"
                    :class="{ active: topUpPreset === p }"
                    @click="selectTopUpPreset(p)"
                  >{{ p }} ₽</button>
                  <button
                    type="button"
                    :class="{ active: topUpPreset === 'custom' }"
                    @click="selectTopUpPreset('custom')"
                  >Своя</button>
                </div>
                <input
                  v-show="topUpPreset === 'custom'"
                  ref="topUpAmountInput"
                  v-model="topUpAmount"
                  class="field case-start-age-input"
                  type="number"
                  min="0.01"
                  step="0.01"
                  inputmode="decimal"
                  placeholder="Сумма в ₽"
                  @keydown.enter.prevent="confirmTopUp"
                />
              </div>
              <p v-if="topUpError" class="error-text">{{ topUpError }}</p>
            </template>
          </div>

          <div class="case-start-actions">
            <button type="button" class="toolbar-btn secondary" :disabled="toppingUp" @click="closeTopUp">
              Отмена
            </button>
            <button
              type="button"
              class="toolbar-btn"
              :class="{ analyzing: toppingUp }"
              :disabled="toppingUp"
              @click="confirmTopUp"
            >
              <span
                class="material-symbols-outlined"
                :class="{ 'spin-slow': toppingUp }"
                aria-hidden="true"
              >{{ toppingUp ? 'progress_activity' : 'add_card' }}</span>
              <span>{{ toppingUp ? 'Пополняем…' : 'Пополнить' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
