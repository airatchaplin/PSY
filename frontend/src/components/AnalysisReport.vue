<script setup lang="ts">
import { computed } from 'vue'
import { rubricLabel } from '../labels'
import type { Analysis } from '../types'
import { renderMarkdown } from '../utils/markdown'

const props = defineProps<{
  analysis: Analysis
}>()

type RubricPayload = {
  scores?: Record<string, number>
  strengths?: string[]
  growthAreas?: string[]
  safetyNotes?: string[]
}

type AlternativeReply = {
  original?: string
  alternative?: string
  why?: string
}

const RUBRIC_ORDER = [
  'contact_quality',
  'phenomenological_attention',
  'figure_ground',
  'experiment_use',
  'boundary_awareness',
  'empathy_presence',
  'question_vs_interpretation_balance'
]

const rubric = computed((): RubricPayload => {
  try {
    return JSON.parse(props.analysis.rubricJson || '{}')
  } catch {
    return {}
  }
})

const reportHtml = computed(() => renderMarkdown(props.analysis.reportMd || ''))

const scores = computed(() => {
  const raw = rubric.value.scores || {}
  const keys = [
    ...RUBRIC_ORDER.filter(k => k in raw),
    ...Object.keys(raw).filter(k => !RUBRIC_ORDER.includes(k))
  ]
  return keys
    .filter(k => typeof raw[k] === 'number')
    .map(dimension => {
      const value = Number(raw[dimension])
      return {
        dimension,
        label: rubricLabel(dimension),
        value,
        pct: Math.max(0, Math.min(100, (value / 5) * 100)),
        tone: value <= 2 ? 'low' : value <= 3 ? 'mid' : 'high'
      }
    })
})

const strengths = computed(() => rubric.value.strengths?.filter(Boolean) ?? [])
const growthAreas = computed(() => rubric.value.growthAreas?.filter(Boolean) ?? [])
const safetyNotes = computed(() => rubric.value.safetyNotes?.filter(Boolean) ?? [])

const alternatives = computed((): AlternativeReply[] => {
  if (!props.analysis.alternativesJson) return []
  try {
    const parsed = JSON.parse(props.analysis.alternativesJson)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
})

const avgScore = computed(() => {
  if (!scores.value.length) return null
  const sum = scores.value.reduce((acc, s) => acc + s.value, 0)
  return sum / scores.value.length
})
</script>

<template>
  <div class="analysis-report">
    <div v-if="avgScore != null" class="analysis-summary-card analysis-reveal">
      <div class="analysis-summary-score">
        <strong>{{ avgScore.toFixed(1) }}</strong>
        <span>из 5</span>
      </div>
      <div class="analysis-summary-copy">
        <div class="section-label">Средний балл по рубрике</div>
        <p>Оценка учебной сессии по гештальт-шкалам</p>
      </div>
    </div>

    <div
      v-if="reportHtml"
      class="analysis-md analysis-reveal"
      v-html="reportHtml"
    />

    <section v-if="scores.length" class="analysis-section analysis-reveal">
      <div class="section-label">Рубрика</div>
      <div class="rubric-bars">
        <div
          v-for="s in scores"
          :key="s.dimension"
          class="rubric-row"
          :class="`tone-${s.tone}`"
        >
          <div class="rubric-row-top">
            <span class="rubric-label">{{ s.label }}</span>
            <span class="rubric-value">{{ s.value }}</span>
          </div>
          <div class="rubric-track" aria-hidden="true">
            <div class="rubric-fill" :style="{ width: `${s.pct}%` }" />
          </div>
        </div>
      </div>
    </section>

    <section v-if="strengths.length" class="analysis-section analysis-reveal">
      <div class="section-label">Сильные стороны</div>
      <ul class="analysis-chip-list">
        <li v-for="(s, i) in strengths" :key="'s' + i">{{ s }}</li>
      </ul>
    </section>

    <section v-if="growthAreas.length" class="analysis-section analysis-reveal">
      <div class="section-label">Зоны роста</div>
      <ul class="analysis-chip-list growth">
        <li v-for="(s, i) in growthAreas" :key="'g' + i">{{ s }}</li>
      </ul>
    </section>

    <section v-if="alternatives.length" class="analysis-section analysis-reveal">
      <div class="section-label">Альтернативные реплики</div>
      <div class="alt-cards">
        <article v-for="(a, i) in alternatives" :key="'a' + i" class="alt-card">
          <div v-if="a.original" class="alt-block">
            <span class="alt-kicker">Было</span>
            <p>{{ a.original }}</p>
          </div>
          <div v-if="a.alternative" class="alt-block alt">
            <span class="alt-kicker">Можно так</span>
            <p>{{ a.alternative }}</p>
          </div>
          <p v-if="a.why" class="alt-why">{{ a.why }}</p>
        </article>
      </div>
    </section>

    <section v-if="safetyNotes.length" class="analysis-section analysis-reveal">
      <div class="section-label">Заметки по безопасности</div>
      <ul class="analysis-safety-list">
        <li v-for="(s, i) in safetyNotes" :key="'n' + i">{{ s }}</li>
      </ul>
    </section>
  </div>
</template>
