<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  title?: string
  statuses?: string[]
  compact?: boolean
}>(), {
  title: 'ИИ готовит клиента',
  statuses: () => [
    'Собираю персону…',
    'Подбираю тон поведения…',
    'Уточняю жизненный контекст…',
    'Формирую первую реплику…'
  ],
  compact: false
})

const idx = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const status = computed(() => props.statuses[idx.value % props.statuses.length] ?? props.statuses[0])

onMounted(() => {
  idx.value = 0
  timer = setInterval(() => {
    idx.value = (idx.value + 1) % Math.max(1, props.statuses.length)
  }, 2200)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  timer = null
})
</script>

<template>
  <div
    class="analysis-generating reception-generating"
    :class="{ compact }"
    aria-live="polite"
    aria-busy="true"
  >
    <div class="analysis-generating-orb" aria-hidden="true">
      <span class="material-symbols-outlined analysis-generating-icon">auto_awesome</span>
      <span class="analysis-generating-ring"></span>
      <span class="analysis-generating-ring delay"></span>
    </div>
    <p class="analysis-generating-title">{{ title }}</p>
    <p class="analysis-generating-status" :key="status">{{ status }}</p>
  </div>
</template>
