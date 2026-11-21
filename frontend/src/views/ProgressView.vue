<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api/client'
import AnalysisReport from '../components/AnalysisReport.vue'
import { difficultyLabel, rubricLabel, tagLabel } from '../labels'
import { apiErrorMessage, INSUFFICIENT_FUNDS_MSG } from '../api/errors'
import { llmAllowed, setMeProfile } from '../meProfile'
import { openHubMenu } from '../hubMenu'
import { setProgressMobileTab } from '../progressUi'
import type { Analysis, Progress } from '../types'

const progress = ref<Progress | null>(null)
const notebookAnalyses = ref<Analysis[]>([])
const selectedAnalysisId = ref<string | null>(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const analyzingStatusIdx = ref(0)
let analyzingStatusTimer: ReturnType<typeof setInterval> | null = null

const ANALYZING_STATUSES = [
  'Собираю сеансы…',
  'Сверяю с гештальт-рубрикой…',
  'Считаю слабые зоны…',
  'Формулирую отчёт…'
]

const analyzingStatus = computed(() => ANALYZING_STATUSES[analyzingStatusIdx.value] ?? ANALYZING_STATUSES[0])

const selectedAnalysis = computed(() =>
  notebookAnalyses.value.find(a => a.id === selectedAnalysisId.value) || null
)

const statsHead = {
  title: 'Статистика',
  sub: 'Сводка по практике: сколько сеансов вы провели, с какими тонами клиентов работали чаще и где по гештальт-рубрике пока слабее всего.'
}

const analysesHead = computed(() => {
  if (selectedAnalysis.value && !busy.value) {
    return {
      title: 'Анализ тетради',
      sub: formatAnalysisWhen(selectedAnalysis.value.createdAt)
    }
  }
  return {
    title: 'Анализы',
    sub: 'Сохранённые анализы всей тетради.'
  }
})

function formatAnalysisWhen(iso: string) {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('ru-RU', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

async function loadNotebookAnalyses() {
  const list = await api.listGlobalAnalyses()
  notebookAnalyses.value = Array.isArray(list) ? list : []
  // Keep selection only if it still exists — never auto-open an analysis.
  if (
    selectedAnalysisId.value
    && !notebookAnalyses.value.some(a => a.id === selectedAnalysisId.value)
  ) {
    selectedAnalysisId.value = null
  }
}

onMounted(async () => {
  try {
    progress.value = await api.progress()
  } catch (e) {
    error.value = apiErrorMessage(e)
  }
  try {
    await loadNotebookAnalyses()
  } catch (e) {
    if (!error.value) error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (analyzingStatusTimer) clearInterval(analyzingStatusTimer)
})

async function analyzeAll() {
  if (busy.value) return
  if (!llmAllowed.value) {
    error.value = INSUFFICIENT_FUNDS_MSG
    return
  }
  busy.value = true
  error.value = ''
  analyzingStatusIdx.value = 0
  if (analyzingStatusTimer) clearInterval(analyzingStatusTimer)
  analyzingStatusTimer = setInterval(() => {
    analyzingStatusIdx.value = (analyzingStatusIdx.value + 1) % ANALYZING_STATUSES.length
  }, 2200)
  try {
    const created = await api.analyzeGlobal()
    notebookAnalyses.value = [created, ...notebookAnalyses.value.filter(a => a.id !== created.id)]
    selectedAnalysisId.value = created.id
    setProgressMobileTab('clients')
    try { setMeProfile(await api.me()) } catch { /* ignore */ }
  } catch (e) {
    error.value = apiErrorMessage(e)
    try { setMeProfile(await api.me()) } catch { /* ignore */ }
  } finally {
    if (analyzingStatusTimer) {
      clearInterval(analyzingStatusTimer)
      analyzingStatusTimer = null
    }
    busy.value = false
  }
}

function selectAnalysis(id: string) {
  selectedAnalysisId.value = id
  setProgressMobileTab('clients')
}

function backToAnalyses() {
  selectedAnalysisId.value = null
}
</script>

<template>
  <Teleport defer to="#progress-recent-slot">
    <div
      class="progress-recent progress-analyses"
      :class="{ 'progress-analyses--detail': selectedAnalysis && !busy }"
    >
      <header class="page-head compact hub-sticky-head progress-hub-head">
        <div class="hub-sticky-head-row">
          <button
            type="button"
            class="hub-section-menu-btn"
            aria-haspopup="menu"
            aria-label="Разделы прогресса"
            title="Разделы"
            @click="openHubMenu('progress')"
          >
            <span class="material-symbols-outlined" aria-hidden="true">more_vert</span>
          </button>
          <div class="hub-sticky-head-copy">
            <div class="section-label">Прогресс</div>
            <h2 class="page-title">{{ analysesHead.title }}</h2>
            <p class="page-sub">{{ analysesHead.sub }}</p>
          </div>
        </div>
      </header>

      <div class="progress-analyses-body">
        <template v-if="selectedAnalysis && !busy">
          <div class="progress-analysis-head progress-analysis-head-desktop">
            <button
              class="icon-btn secondary panel-back-clients"
              type="button"
              title="Ко всем анализам"
              aria-label="Ко всем анализам"
              @click="backToAnalyses"
            >
              <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
            </button>
            <div class="section-label left-scroll-label progress-analysis-title">
              Анализ тетради
              <span>{{ formatAnalysisWhen(selectedAnalysis.createdAt) }}</span>
            </div>
          </div>
          <button
            class="toolbar-btn secondary progress-analysis-back-mobile"
            type="button"
            @click="backToAnalyses"
          >
            <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
            Ко всем анализам
          </button>
          <div class="progress-analysis-detail analysis-reveal">
            <AnalysisReport :analysis="selectedAnalysis" />
          </div>
        </template>
        <template v-else>
          <div class="section-label left-scroll-label progress-analyses-desktop-label">Анализы тетради</div>
          <p v-if="loading" class="empty-hint">Загрузка…</p>
          <p v-else-if="!notebookAnalyses.length" class="empty-hint">
            Пока нет сохранённых анализов — создайте на странице статистики.
          </p>
          <template v-else>
            <div class="segmented-list progress-analysis-picker" role="listbox" aria-label="Анализы тетради">
              <button
                v-for="a in notebookAnalyses"
                :key="a.id"
                type="button"
                role="option"
                class="list-item progress-analysis-pick"
                :aria-selected="false"
                @click="selectAnalysis(a.id)"
              >
                <div class="list-item-top">
                  <div class="list-item-title">
                    <strong>{{ formatAnalysisWhen(a.createdAt) }}</strong>
                  </div>
                </div>
              </button>
            </div>
          </template>
        </template>
      </div>
    </div>
  </Teleport>

  <div class="progress-stats-page">
    <header class="page-head compact hub-sticky-head progress-hub-head">
      <div class="hub-sticky-head-row">
        <button
          type="button"
          class="hub-section-menu-btn"
          aria-haspopup="menu"
          aria-label="Разделы прогресса"
          title="Разделы"
          @click="openHubMenu('progress')"
        >
          <span class="material-symbols-outlined" aria-hidden="true">more_vert</span>
        </button>
        <div class="hub-sticky-head-copy">
          <div class="section-label">Прогресс</div>
          <h2 class="page-title">{{ statsHead.title }}</h2>
          <p class="page-sub">{{ statsHead.sub }}</p>
        </div>
      </div>
      <button
        class="toolbar-btn progress-analyze-btn"
        type="button"
        :class="{ analyzing: busy }"
        :disabled="busy || !llmAllowed"
        :title="llmAllowed ? undefined : INSUFFICIENT_FUNDS_MSG"
        @click="analyzeAll"
      >
        <span
          class="material-symbols-outlined"
          :class="{ 'spin-slow': busy }"
          aria-hidden="true"
        >{{ busy ? 'progress_activity' : 'analytics' }}</span>
        {{ busy ? 'Анализируем…' : 'Анализ тетради' }}
      </button>
    </header>

    <div class="page-scroll progress-stats">
      <div v-if="busy" class="analysis-generating panel inset" aria-live="polite" aria-busy="true">
        <div class="analysis-generating-orb" aria-hidden="true">
          <span class="material-symbols-outlined analysis-generating-icon">auto_awesome</span>
          <span class="analysis-generating-ring"></span>
          <span class="analysis-generating-ring delay"></span>
        </div>
        <p class="analysis-generating-title">ИИ готовит анализ</p>
        <p class="analysis-generating-status" :key="analyzingStatus">{{ analyzingStatus }}</p>
      </div>

      <p v-if="loading" class="empty-hint">Загрузка…</p>
      <p v-else-if="error" class="error-text">{{ error }}</p>
      <template v-else-if="progress">
        <div class="stats">
          <div class="stat"><span class="muted">Клиенты</span><strong>{{ progress.clientsTotal }}</strong></div>
          <div class="stat"><span class="muted">Сеансы</span><strong>{{ progress.sessionsTotal }}</strong></div>
          <div class="stat"><span class="muted">Завершено</span><strong>{{ progress.sessionsClosed }}</strong></div>
          <div class="stat"><span class="muted">Минуты</span><strong>{{ progress.practiceMinutes }}</strong></div>
        </div>

        <h3 class="section-block">По поведениям / тонам</h3>
        <p v-if="!progress.byBehaviorTag.length" class="empty-hint">Пока нет данных.</p>
        <div v-for="t in progress.byBehaviorTag" :key="t.tag" class="list-item plain">
          <strong>{{ t.labelRu || tagLabel(t.tag) }}</strong>
          <div class="muted">
            сеансов: {{ t.sessions }}, минут: {{ t.minutes }}
            <span v-if="t.avgRubric != null">, средний балл: {{ t.avgRubric.toFixed(2) }}</span>
          </div>
        </div>

        <h3 class="section-block">По сложности</h3>
        <div class="stats">
          <div class="stat" v-for="d in progress.byDifficulty" :key="d.difficulty">
            <span class="muted">{{ difficultyLabel(d.difficulty, d.labelRu) }}</span>
            <strong>{{ d.clients }}</strong>
            <div class="muted">сеансов: {{ d.sessions }}</div>
          </div>
        </div>

        <h3 class="section-block">Слабые зоны</h3>
        <div class="tags">
          <span class="tag" v-for="w in progress.weakZones" :key="w">{{ rubricLabel(w) }}</span>
          <span v-if="!progress.weakZones.length" class="empty-hint">Появятся после анализов сессий.</span>
        </div>

        <h3 class="section-block">Рубрика (средние)</h3>
        <div v-for="r in progress.rubricAverages" :key="r.dimension" class="muted">
          {{ rubricLabel(r.dimension) }}: {{ r.average.toFixed(2) }}
        </div>
      </template>
    </div>
  </div>

</template>
