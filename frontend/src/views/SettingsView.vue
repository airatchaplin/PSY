<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client'
import { isAdmin, logout } from '../auth/session'
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
import { formatBalanceRub, setMeProfile } from '../meProfile'
import {
  canInstallPwa,
  isPwaInstalled,
  promptPwaInstall,
  showIosInstallHint
} from '../pwa'
import {
  settingsNavItems,
  settingsSection,
  setSettingsSection,
  type SettingsSection
} from '../settingsUi'
import {
  THEMES,
  currentTheme,
  isSystemTheme,
  setSystemTheme,
  setTheme,
  type ThemeId
} from '../theme'
import type { AdminUsageChartMetric, AdminUsageStats, BalanceTopUp, Me } from '../types'
import { usageChatRoute } from '../usageNav'

const router = useRouter()

const me = ref<Me | null>(null)
const balanceHistory = ref<BalanceTopUp[]>([])
const historyLoading = ref(false)
const usage = ref<AdminUsageStats | null>(null)
const usageLoading = ref(false)
const chartMetric = ref<AdminUsageChartMetric>('cost')
const filterFrom = ref('')
const filterTo = ref('')
const filterModel = ref('')
const filterSource = ref('')
const usageFiltersReady = ref(false)
let usageRequestId = 0
const usageError = ref('')
const expandedClientKey = ref<string | null>(null)
const expandedGeneralKey = ref<string | null>(null)

const usageByClientCards = computed(() =>
  (usage.value?.byClient || []).filter(row => !!row.clientId)
)

const usageByGeneralCards = computed(() => buildGeneralUsageCards(usage.value))
const installBusy = ref(false)
const installMsg = ref('')

const sectionTitles: Record<SettingsSection, { title: string; sub: string }> = {
  profile: {
    title: 'Профиль',
    sub: 'Аккаунт, баланс и сессия.'
  },
  themes: {
    title: 'Темы',
    sub: 'Оформление тетради на этом устройстве.'
  },
  topups: {
    title: 'Пополнения',
    sub: 'История пополнений внутреннего баланса.'
  },
  usage: {
    title: 'Использование',
    sub: 'Ваши расходы, токены и запросы к ИИ.'
  }
}

const head = computed(() => sectionTitles[settingsSection.value])

onMounted(async () => {
  usageFiltersReady.value = false
  resetUsageFilters()
  try {
    me.value = await api.me()
    setMeProfile(me.value)
  } catch {
    me.value = null
  }
  await Promise.all([loadBalanceHistory(), loadUsage()])
  usageFiltersReady.value = true
})

watch(settingsSection, section => {
  if (section === 'topups' && !balanceHistory.value.length && !historyLoading.value) {
    void loadBalanceHistory()
  }
  if (section === 'usage' && !usage.value && !usageLoading.value) {
    void loadUsage()
  }
})

watch(
  [filterFrom, filterTo, filterModel, filterSource],
  () => {
    if (!usageFiltersReady.value) return
    void loadUsage()
  }
)

async function loadBalanceHistory() {
  historyLoading.value = true
  try {
    balanceHistory.value = await api.myBalanceHistory().catch(() => [] as BalanceTopUp[])
  } finally {
    historyLoading.value = false
  }
}

function pickTheme(id: ThemeId) {
  setTheme(id)
}

function pickSystemTheme() {
  setSystemTheme()
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

function rub(n?: number | null) {
  return formatRub(n)
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

function openUsageClient(
  clientId: string | null | undefined,
  session?: {
    sessionId?: string | null
    status?: string | null
    hasAnalysis?: boolean
  } | null,
  view?: 'analysis' | null
) {
  if (!clientId) return
  void router.push(usageChatRoute(clientId, session, view))
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

async function loadUsage() {
  const reqId = ++usageRequestId
  usageLoading.value = true
  usageError.value = ''
  try {
    const next = await api.myUsage({
      from: localInputToIso(filterFrom.value),
      to: localInputToIso(filterTo.value),
      model: filterModel.value || undefined,
      source: filterSource.value || undefined
    })
    if (reqId !== usageRequestId) return
    usage.value = next
    expandedClientKey.value = null
    expandedGeneralKey.value = null
  } catch (e) {
    if (reqId !== usageRequestId) return
    usageError.value = e instanceof Error ? e.message : 'Не удалось загрузить использование'
  } finally {
    if (reqId === usageRequestId) usageLoading.value = false
  }
}

async function installApp() {
  if (installBusy.value) return
  installBusy.value = true
  installMsg.value = ''
  try {
    const outcome = await promptPwaInstall()
    if (outcome === 'accepted') {
      installMsg.value = 'Ярлык добавлен на устройство.'
    } else if (outcome === 'dismissed') {
      installMsg.value = 'Установка отменена.'
    } else {
      installMsg.value = 'Установка сейчас недоступна в этом браузере.'
    }
  } finally {
    installBusy.value = false
  }
}
</script>

<template>
  <Teleport defer to="#settings-nav-slot">
    <div class="settings-nav">
      <div class="section-label left-scroll-label">Настройки</div>
      <div class="segmented-list settings-nav-list" role="tablist" aria-label="Разделы настроек">
        <button
          v-for="item in settingsNavItems"
          :key="item.id"
          type="button"
          role="tab"
          class="list-item settings-nav-item"
          :class="{ active: settingsSection === item.id }"
          :aria-selected="settingsSection === item.id"
          @click="setSettingsSection(item.id)"
        >
          <span class="material-symbols-outlined settings-nav-icon" aria-hidden="true">{{ item.icon }}</span>
          <strong>{{ item.label }}</strong>
        </button>
      </div>
    </div>
  </Teleport>

  <div class="settings-page page-scroll">
    <div class="page-head compact">
      <div class="section-label">Настройки</div>
      <h2 class="page-title">{{ head.title }}</h2>
      <p class="page-sub">{{ head.sub }}</p>
    </div>

    <section v-show="settingsSection === 'profile'" class="settings-section">
      <div class="section-label">Аккаунт</div>
      <div class="settings-account">
        <div class="settings-avatar" aria-hidden="true">
          <span class="material-symbols-outlined">person</span>
        </div>
        <div class="settings-account-copy">
          <strong>{{ me?.displayName || 'Терапевт' }}</strong>
          <span v-if="me?.email" class="muted">{{ me.email }}</span>
          <span
            v-if="me?.admin || isAdmin"
            class="muted"
          >
            Баланс приложения:
            {{ me?.appBalanceRub == null ? '—' : formatBalanceRub(me.appBalanceRub) }}
          </span>
          <span
            v-if="me?.admin || isAdmin"
            class="muted"
          >
            Выдано пользователям:
            {{ me?.usersBalanceTotalRub == null ? '—' : formatBalanceRub(me.usersBalanceTotalRub) }}
          </span>
          <span
            class="muted"
            :class="{
              'balance-negative': me && Number(me.balanceRub ?? 0) < 0,
              'balance-warning': me && Number(me.balanceRub ?? 0) === 0
            }"
          >
            {{ me?.admin || isAdmin ? 'Личный баланс' : 'Баланс' }}: {{ formatBalanceRub(me?.balanceRub) }}
          </span>
        </div>
      </div>

      <div class="section-label" style="margin-top: 12px">Ярлык на телефон</div>
      <p class="muted settings-hint">
        Можно добавить PSY на домашний экран и открывать как приложение.
      </p>

      <div v-if="isPwaInstalled" class="settings-install-status">
        <span class="material-symbols-outlined" aria-hidden="true">check_circle</span>
        Уже установлено на этом устройстве.
      </div>

      <template v-else>
        <button
          v-if="canInstallPwa"
          type="button"
          class="toolbar-btn"
          :disabled="installBusy"
          @click="installApp"
        >
          <span class="material-symbols-outlined" aria-hidden="true">install_mobile</span>
          {{ installBusy ? 'Открываем…' : 'Установить ярлык' }}
        </button>

        <div v-else-if="showIosInstallHint" class="settings-ios-hint">
          <p>
            В Safari нажмите
            <span class="material-symbols-outlined settings-ios-share" aria-hidden="true">ios_share</span>
            «Поделиться», затем «На экран „Домой“».
          </p>
        </div>

        <p v-else class="muted settings-hint">
          Откройте сайт в Chrome или Safari на телефоне — появится кнопка установки
          или пункт «Добавить на главный экран».
        </p>
      </template>

      <p v-if="installMsg" class="muted settings-install-msg">{{ installMsg }}</p>

      <div class="section-label" style="margin-top: 12px">Сессия</div>
      <button type="button" class="toolbar-btn danger-outline" @click="logout">
        <span class="material-symbols-outlined" aria-hidden="true">logout</span>
        Выйти из аккаунта
      </button>
    </section>

    <section v-show="settingsSection === 'themes'" class="settings-section">
      <div class="section-label">Тема тетради</div>
      <p class="muted settings-hint">
        По умолчанию как на устройстве (светлая / тёмная). Можно зафиксировать вручную.
      </p>
      <div class="theme-grid" role="radiogroup" aria-label="Тема тетради">
        <button
          type="button"
          class="theme-card"
          role="radio"
          :aria-checked="isSystemTheme"
          :class="{ active: isSystemTheme }"
          @click="pickSystemTheme"
        >
          <div class="theme-swatches" aria-hidden="true">
            <span class="theme-swatch" style="background: #F2F2F7" />
            <span class="theme-swatch" style="background: #007AFF" />
            <span class="theme-swatch" style="background: #1C1C1E" />
          </div>
          <div class="theme-card-copy">
            <strong>Как на устройстве</strong>
            <span>Следует системной светлой или тёмной теме</span>
          </div>
          <span
            class="material-symbols-outlined theme-check"
            aria-hidden="true"
          >{{ isSystemTheme ? 'check_circle' : 'circle' }}</span>
        </button>
        <button
          v-for="t in THEMES"
          :key="t.id"
          type="button"
          class="theme-card"
          role="radio"
          :aria-checked="!isSystemTheme && currentTheme === t.id"
          :class="{ active: !isSystemTheme && currentTheme === t.id }"
          @click="pickTheme(t.id)"
        >
          <div class="theme-swatches" aria-hidden="true">
            <span
              v-for="(c, i) in t.swatches"
              :key="i"
              class="theme-swatch"
              :style="{ background: c }"
            />
          </div>
          <div class="theme-card-copy">
            <strong>{{ t.label }}</strong>
            <span>{{ t.description }}</span>
          </div>
          <span
            class="material-symbols-outlined theme-check"
            aria-hidden="true"
          >{{ !isSystemTheme && currentTheme === t.id ? 'check_circle' : 'circle' }}</span>
        </button>
      </div>
    </section>

    <section v-show="settingsSection === 'topups'" class="settings-section">
      <div class="section-label">История пополнений</div>
      <p class="muted settings-hint">
        Пополнения внутреннего баланса администратором.
      </p>
      <div v-if="historyLoading" class="muted">Загрузка…</div>
      <div v-else-if="!balanceHistory.length" class="muted">Пополнений пока нет.</div>
      <div v-else class="balance-history-list">
        <div
          v-for="row in balanceHistory"
          :key="row.id"
          class="balance-history-row list-item"
        >
          <div class="balance-history-main">
            <div class="list-item-top">
              <div class="list-item-title">
                <strong>+{{ formatBalanceRub(row.amountRub) }}</strong>
              </div>
              <span class="muted tiny">остаток {{ formatBalanceRub(row.balanceAfterRub) }}</span>
            </div>
            <div class="muted tiny">
              {{ formatTopUpWhen(row.createdAt) }}
              <span v-if="row.createdByUsername"> · {{ row.createdByUsername }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-show="settingsSection === 'usage'" class="settings-section usage-dashboard">
      <p v-if="usageError" class="error-text">{{ usageError }}</p>

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

        <div v-if="usageLoading" class="muted usage-chart-empty">Загрузка…</div>
        <div v-else-if="!chartSeries.length" class="muted usage-chart-empty">
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
        <p class="muted settings-hint"></p>
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
        <p class="muted settings-hint">Нажмите на клиента, чтобы открыть статистику по сеансам.</p>
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
                <div v-if="row.clientId && session.sessionId" class="usage-session-actions" @click.stop>
                  <button
                    v-if="String(session.status || '').toUpperCase() === 'ACTIVE'"
                    type="button"
                    class="toolbar-btn secondary usage-session-btn"
                    @click.stop="openUsageClient(row.clientId, session)"
                  >
                    <span class="material-symbols-outlined" aria-hidden="true">chat</span>
                    <span>В чат</span>
                  </button>
                  <button
                    v-else
                    type="button"
                    class="toolbar-btn secondary usage-session-btn"
                    @click.stop="openUsageClient(row.clientId, session)"
                  >
                    <span class="material-symbols-outlined" aria-hidden="true">history</span>
                    <span>Открыть сеанс</span>
                  </button>
                  <button
                    type="button"
                    class="toolbar-btn secondary usage-session-btn"
                    :disabled="!session.hasAnalysis"
                    :title="session.hasAnalysis ? 'Открыть анализ сеанса' : 'Анализ ещё не создан'"
                    @click.stop="openUsageClient(row.clientId, session, 'analysis')"
                  >
                    <span class="material-symbols-outlined" aria-hidden="true">analytics</span>
                    <span>Анализ</span>
                  </button>
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

    </section>
  </div>
</template>
