<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { api } from '../api/client'
import AnalysisReport from '../components/AnalysisReport.vue'
import { closeLeftPanelOnMobile } from '../sessionUi'
import { difficultyLabel, rubricLabel, tagCode, tagLabel } from '../labels'
import type { Analysis, Progress } from '../types'

const progress = ref<Progress | null>(null)
const globalAnalysis = ref<Analysis | null>(null)
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

onMounted(async () => {
  try {
    progress.value = await api.progress()
  } catch (e) {
    error.value = String(e)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  if (analyzingStatusTimer) clearInterval(analyzingStatusTimer)
})

async function analyzeAll() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  analyzingStatusIdx.value = 0
  if (analyzingStatusTimer) clearInterval(analyzingStatusTimer)
  analyzingStatusTimer = setInterval(() => {
    analyzingStatusIdx.value = (analyzingStatusIdx.value + 1) % ANALYZING_STATUSES.length
  }, 2200)
  try {
    globalAnalysis.value = await api.analyzeGlobal()
  } catch (e) {
    error.value = String(e)
  } finally {
    if (analyzingStatusTimer) {
      clearInterval(analyzingStatusTimer)
      analyzingStatusTimer = null
    }
    busy.value = false
  }
}

function therapyProgressLabel(value: unknown) {
  const n = typeof value === 'number' ? value : Number(value)
  return `${Number.isFinite(n) ? Math.round(n) : 0}%`
}
</script>

<template>
  <Teleport defer to="#progress-recent-slot">
    <div class="progress-recent">
      <div class="section-label left-scroll-label">Недавние клиенты</div>
      <p v-if="loading" class="empty-hint">Загрузка…</p>
      <p v-else-if="!progress?.recentClients.length" class="empty-hint">Пока нет клиентов.</p>
      <div v-else class="segmented-list">
        <RouterLink
          v-for="c in progress.recentClients"
          :key="c.id"
          class="list-item"
          :to="`/clients/${c.id}`"
          @click="closeLeftPanelOnMobile({ instant: true })"
        >
          <div class="list-item-top">
            <div class="list-item-title">
              <strong>{{ c.name }}</strong>, {{ c.age }} · сеансов: {{ c.sessions }}
            </div>
            <span class="session-entry-badge progress-badge">{{ therapyProgressLabel(c.therapyProgress) }}</span>
          </div>
          <div class="tags">
            <span class="tag" v-for="t in c.behaviorTags" :key="tagCode(t)">{{ tagLabel(t) }}</span>
          </div>
        </RouterLink>
      </div>
    </div>
  </Teleport>

  <div class="page-scroll progress-stats">
    <div class="row page-head-row">
      <div class="page-head compact">
        <h2 class="section-label left-scroll-label">Прогресс</h2>
        <p class="page-sub">
          Сводка по практике: сколько сеансов вы провели, с какими тонами клиентов
          работали чаще и где по гештальт-рубрике пока слабее всего.
        </p>
      </div>
      <button
        class="toolbar-btn"
        type="button"
        :class="{ analyzing: busy }"
        :disabled="busy"
        @click="analyzeAll"
      >
        <span
          class="material-symbols-outlined"
          :class="{ 'spin-slow': busy }"
          aria-hidden="true"
        >{{ busy ? 'progress_activity' : 'analytics' }}</span>
        {{ busy ? 'Анализируем…' : 'Анализ тетради' }}
      </button>
    </div>

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

    <div v-if="globalAnalysis && !busy" class="panel inset analysis-reveal">
      <h3>Анализ тетради</h3>
      <AnalysisReport :analysis="globalAnalysis" />
    </div>
  </div>
</template>
