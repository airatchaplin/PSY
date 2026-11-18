<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { adminNavItems, adminSection, setAdminSection, type AdminSection } from '../adminUi'
import { api } from '../api/client'
import type {
  AdminModelOption,
  AdminOverview,
  AdminUsageChartMetric,
  AdminUsageStats,
  KeycloakUser,
  PromptDetail,
  PromptSummary
} from '../types'

const overview = ref<AdminOverview | null>(null)
const models = ref<AdminModelOption[]>([])
const usage = ref<AdminUsageStats | null>(null)
const prompts = ref<PromptSummary[]>([])
const users = ref<KeycloakUser[]>([])
const selectedPromptId = ref('')
const promptDetail = ref<PromptDetail | null>(null)
const modelDraft = ref('')
const modelFilter = ref('')
const baseUrlDraft = ref('')
const apiKeyDraft = ref('')
const loading = ref(true)
const usageLoading = ref(false)
const chartMetric = ref<AdminUsageChartMetric>('cost')
const filterFrom = ref('')
const filterTo = ref('')
const filterModel = ref('')
const filterSource = ref('')
const appliedFrom = ref('')
const appliedTo = ref('')
const appliedModel = ref('')
const appliedSource = ref('')
const savingModel = ref(false)
const savingConnection = ref(false)
const savingPrompt = ref(false)
const savingUser = ref(false)
const error = ref('')
const notice = ref('')

const newUser = ref({
  username: '',
  password: '',
  email: '',
  firstName: '',
  lastName: '',
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
    sub: 'Создание учёток в Keycloak (realm psy).'
  }
}

const head = computed(() => sectionTitles[adminSection.value])

const filteredModels = computed(() => {
  const q = modelFilter.value.trim().toLowerCase()
  if (!q) return models.value.slice(0, 120)
  return models.value.filter(m =>
    m.id.toLowerCase().includes(q) || m.name.toLowerCase().includes(q)
  ).slice(0, 120)
})

function seriesValue(row: { calls: number; tokens: number; costRub: number }) {
  if (chartMetric.value === 'tokens') return Number(row.tokens) || 0
  if (chartMetric.value === 'calls') return Number(row.calls) || 0
  return Number(row.costRub) || 0
}

const dayMax = computed(() => {
  const rows = usage.value?.byDay || []
  return Math.max(1, ...rows.map(seriesValue))
})

const modelMax = computed(() => {
  const rows = usage.value?.byModel || []
  return Math.max(1, ...rows.map(r => Number(r.costRub) || Number(r.calls) || 0))
})

const userMax = computed(() => {
  const rows = usage.value?.byUser || []
  return Math.max(1, ...rows.map(r => Number(r.costRub) || Number(r.tokens) || 0))
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
    const md = date.slice(5)
    return `${md} ${time.slice(0, 5)}`
  }
  return raw.slice(5)
}

function formatChartTick(n: number) {
  if (chartMetric.value === 'cost') {
    return `${n.toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ₽`
  }
  if (chartMetric.value === 'tokens') {
    if (n >= 1000) return `${(n / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}K`
    return n.toLocaleString('ru-RU')
  }
  return n.toLocaleString('ru-RU')
}

function formatTokens(n?: number | null) {
  const v = Number(n) || 0
  if (v >= 1_000_000) return `${(v / 1_000_000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}M`
  if (v >= 1000) return `${(v / 1000).toLocaleString('ru-RU', { maximumFractionDigits: 1 })}K`
  return v.toLocaleString('ru-RU')
}

function sourceLabel(source: string) {
  const map: Record<string, string> = {
    chat: 'Чат клиента',
    stream: 'Стрим',
    supervisor: 'Супервизор',
    analysis: 'Анализ',
    reception: 'Генерация клиента',
    case: 'Кейсы',
    prompt: 'Промпт'
  }
  return map[source] || source
}

async function loadUsage() {
  usageLoading.value = true
  try {
    usage.value = await api.adminUsage({
      from: localInputToIso(appliedFrom.value),
      to: localInputToIso(appliedTo.value),
      model: appliedModel.value || undefined,
      source: appliedSource.value || undefined
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось загрузить использование'
  } finally {
    usageLoading.value = false
  }
}

async function applyUsageFilters() {
  appliedFrom.value = filterFrom.value
  appliedTo.value = filterTo.value
  appliedModel.value = filterModel.value
  appliedSource.value = filterSource.value
  await loadUsage()
}

async function resetAndLoadUsage() {
  resetUsageFilters()
  await applyUsageFilters()
}

const limitPct = computed(() => {
  const lim = Number(overview.value?.keyLimitRub || 0)
  const used = Number(overview.value?.keyUsageMonthlyRub || overview.value?.keyUsageRub || 0)
  if (!lim || lim <= 0) return 0
  return Math.min(100, Math.round((used / lim) * 100))
})

function rub(n?: number | null) {
  if (n == null || Number.isNaN(Number(n))) return '—'
  return `${Number(n).toLocaleString('ru-RU', { maximumFractionDigits: 2 })} ₽`
}

function applyOverview(ov: AdminOverview) {
  overview.value = ov
  modelDraft.value = ov.currentModel
  baseUrlDraft.value = ov.baseUrl || ov.defaultBaseUrl || 'https://routerai.ru/api/v1'
  apiKeyDraft.value = ''
}

async function loadUsers() {
  users.value = await api.adminUsers()
}

async function loadAll() {
  loading.value = true
  error.value = ''
  resetUsageFilters()
  appliedFrom.value = filterFrom.value
  appliedTo.value = filterTo.value
  appliedModel.value = ''
  appliedSource.value = ''
  try {
    const [ov, us, pr, usrs] = await Promise.all([
      api.adminOverview(),
      api.adminUsage({
        from: localInputToIso(appliedFrom.value),
        to: localInputToIso(appliedTo.value)
      }),
      api.adminPrompts(),
      api.adminUsers().catch(() => [] as KeycloakUser[])
    ])
    applyOverview(ov)
    usage.value = us
    prompts.value = pr
    users.value = usrs
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
      firstName: newUser.value.firstName.trim() || undefined,
      lastName: newUser.value.lastName.trim() || undefined,
      admin: newUser.value.admin
    })
    notice.value = `Пользователь ${newUser.value.username.trim()} создан`
    newUser.value = {
      username: '',
      password: '',
      email: '',
      firstName: '',
      lastName: '',
      admin: false
    }
    await loadUsers()
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Не удалось создать пользователя'
  } finally {
    savingUser.value = false
  }
}

async function saveConnection() {
  if (!baseUrlDraft.value.trim()) return
  savingConnection.value = true
  notice.value = ''
  error.value = ''
  try {
    const body: { baseUrl: string; apiKey?: string } = {
      baseUrl: baseUrlDraft.value.trim()
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

onMounted(loadAll)
</script>

<template>
  <Teleport defer to="#admin-nav-slot">
    <div class="admin-nav">
      <div class="section-label left-scroll-label">Админ</div>
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
          Сохранённое здесь в БД приоритетнее. Vault/env — только bootstrap, пока ключ ещё не задан.
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
        <p class="muted settings-hint">
          Оставьте ключ пустым, чтобы не менять текущий.
          Сейчас: {{ overview.apiKeyConfigured ? overview.apiKeyMasked : 'не задан' }}
          · bootstrap base: {{ overview.defaultBaseUrl }}
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
          Текущая: <code>{{ overview.currentModel }}</code>
          · env: <code>{{ overview.defaultModel }}</code>
        </p>
        <input v-model="modelFilter" class="field" placeholder="Поиск по названию или id…" />
        <div class="admin-model-row">
          <select v-model="modelDraft" class="field admin-model-select" :disabled="!filteredModels.length">
            <option disabled value="">Выберите модель…</option>
            <option
              v-for="m in filteredModels"
              :key="m.id"
              :value="m.id"
            >{{ m.name }} — {{ m.id }}</option>
          </select>
          <button type="button" class="toolbar-btn" :disabled="savingModel || !modelDraft" @click="saveModel">
            <span class="material-symbols-outlined">{{ savingModel ? 'progress_activity' : 'save' }}</span>
            <span>Применить</span>
          </button>
        </div>
        <p class="muted settings-hint">Или введите id вручную:</p>
        <input v-model="modelDraft" class="field" placeholder="provider/model-id" />
        <p v-if="!models.length" class="muted settings-hint">Каталог пуст — проверьте API key и Base URL.</p>
        <p v-else class="muted settings-hint">Найдено: {{ filteredModels.length }} из {{ models.length }}</p>
      </section>

      <section v-show="adminSection === 'usage'" class="settings-section usage-dashboard">
        <div class="usage-filters">
          <label class="admin-field usage-filter-period">
            <span>Период</span>
            <div class="usage-period-row">
              <input v-model="filterFrom" class="field" type="datetime-local" />
              <span class="usage-period-sep">—</span>
              <input v-model="filterTo" class="field" type="datetime-local" />
            </div>
          </label>
          <label class="admin-field">
            <span>Фильтры</span>
            <div class="usage-filter-row">
              <select v-model="filterSource" class="field">
                <option value="">Все источники</option>
                <option
                  v-for="s in usage?.availableSources || []"
                  :key="s"
                  :value="s"
                >{{ sourceLabel(s) }}</option>
              </select>
              <select v-model="filterModel" class="field">
                <option value="">Все модели</option>
                <option
                  v-for="m in usage?.availableModels || []"
                  :key="m"
                  :value="m"
                >{{ m }}</option>
              </select>
            </div>
          </label>
          <div class="usage-filter-actions">
            <button type="button" class="toolbar-btn" :disabled="usageLoading" @click="applyUsageFilters">
              <span class="material-symbols-outlined">{{ usageLoading ? 'progress_activity' : 'filter_alt' }}</span>
              <span>Применить</span>
            </button>
            <button type="button" class="toolbar-btn secondary" :disabled="usageLoading" @click="resetAndLoadUsage">
              <span>Сбросить</span>
            </button>
          </div>
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

          <div v-if="!usage?.byDay?.length" class="muted usage-chart-empty">
            Пока нет данных за выбранный период — появятся после LLM-запросов.
          </div>
          <div v-else class="usage-chart-body">
            <div class="usage-chart-y" aria-hidden="true">
              <span v-for="(tick, i) in chartYTicks" :key="i">{{ formatChartTick(tick) }}</span>
            </div>
            <div class="usage-chart-plot">
              <div class="usage-chart-grid" aria-hidden="true">
                <div v-for="(_, i) in chartYTicks" :key="`g-${i}`" class="usage-chart-grid-line" />
              </div>
              <div class="bar-chart usage-bar-chart">
                <div
                  v-for="row in usage.byDay"
                  :key="row.day"
                  class="bar-col"
                  :title="`${row.day}: ${rub(row.costRub)} · ${row.calls} · ${formatTokens(row.tokens)}`"
                >
                  <div
                    class="bar"
                    :style="{ height: `${Math.max(3, seriesValue(row) / dayMax * 100)}%` }"
                  />
                  <span>{{ formatBucketLabel(row.day) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">По пользователям</div>
          <div v-if="!usage?.byUser?.length" class="muted">Пока нет данных по пользователям.</div>
          <div v-else class="admin-user-table-wrap">
            <table class="admin-user-table">
              <thead>
                <tr>
                  <th>Пользователь</th>
                  <th>Вызовы</th>
                  <th>Токены</th>
                  <th>₽</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in usage.byUser" :key="row.therapistId || row.displayName">
                  <td>
                    <strong>{{ row.displayName }}</strong>
                    <div v-if="row.email" class="muted tiny">{{ row.email }}</div>
                  </td>
                  <td>{{ row.calls.toLocaleString('ru-RU') }}</td>
                  <td>{{ Number(row.tokens).toLocaleString('ru-RU') }}</td>
                  <td>{{ rub(row.costRub) }}</td>
                </tr>
              </tbody>
            </table>
            <div class="h-bars admin-user-bars">
              <div v-for="row in usage.byUser" :key="`bar-${row.therapistId || row.displayName}`" class="h-bar-row">
                <div class="h-bar-label">{{ row.displayName }}</div>
                <div class="h-bar-track">
                  <div
                    class="h-bar-fill"
                    :style="{ width: `${Math.max(4, (Number(row.costRub) || Number(row.tokens)) / userMax * 100)}%` }"
                  />
                </div>
                <div class="h-bar-meta">{{ rub(row.costRub) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="admin-chart">
          <div class="section-label">По моделям</div>
          <div v-if="!usage?.byModel?.length" class="muted">Нет агрегатов по моделям.</div>
          <div v-else class="h-bars">
            <div v-for="row in usage.byModel" :key="row.model" class="h-bar-row">
              <div class="h-bar-label">{{ row.model }}</div>
              <div class="h-bar-track">
                <div
                  class="h-bar-fill"
                  :style="{ width: `${Math.max(4, (Number(row.costRub) || Number(row.calls)) / modelMax * 100)}%` }"
                />
              </div>
              <div class="h-bar-meta">{{ rub(row.costRub) }} · {{ row.calls }}</div>
            </div>
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
            <span>Имя</span>
            <input v-model="newUser.firstName" class="field" />
          </label>
          <label class="admin-field">
            <span>Фамилия</span>
            <input v-model="newUser.lastName" class="field" />
          </label>
          <label class="case-share-all">
            <input v-model="newUser.admin" type="checkbox" />
            <span>Роль admin (+ therapist)</span>
          </label>
        </div>
        <p class="muted settings-hint">По умолчанию выдаётся роль therapist. Галочка admin добавляет доступ к админке.</p>
        <button type="button" class="toolbar-btn" :disabled="savingUser" @click="createUser">
          <span class="material-symbols-outlined">{{ savingUser ? 'progress_activity' : 'person_add' }}</span>
          <span>Создать в Keycloak</span>
        </button>

        <div class="admin-chart">
          <div class="section-label">Пользователи realm</div>
          <div v-if="!users.length" class="muted">Список пуст или Keycloak недоступен.</div>
          <table v-else class="admin-user-table">
            <thead>
              <tr>
                <th>Логин</th>
                <th>Имя</th>
                <th>Роли</th>
                <th>Статус</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in users" :key="u.id">
                <td>
                  <strong>{{ u.username }}</strong>
                  <div v-if="u.email" class="muted tiny">{{ u.email }}</div>
                </td>
                <td>{{ [u.firstName, u.lastName].filter(Boolean).join(' ') || '—' }}</td>
                <td>
                  <div class="tags">
                    <span v-for="r in u.roles.filter(x => !x.startsWith('default-') && x !== 'offline_access' && x !== 'uma_authorization')" :key="r" class="tag">{{ r }}</span>
                  </div>
                </td>
                <td>{{ u.enabled ? 'активен' : 'выключен' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-show="adminSection === 'prompts'" class="settings-section admin-prompts-section">
        <div class="admin-prompt-toolbar">
          <label class="admin-field admin-prompt-select-field">
            <span>Промпт</span>
            <select v-model="selectedPromptId" class="field admin-prompt-select">
              <option disabled value="">Выберите промпт…</option>
              <option v-for="p in prompts" :key="p.id" :value="p.id">
                {{ p.label }}
              </option>
            </select>
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
  </div>
</template>
