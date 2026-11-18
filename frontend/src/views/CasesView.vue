<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/client'
import AiWorkingBlock from '../components/AiWorkingBlock.vue'
import { casesShareNotice, casesVersion } from '../casesUi'
import { closeLeftPanelOnMobile } from '../sessionUi'
import { difficultyLabel, tagCode, tagLabel } from '../labels'
import type { CaseItem, GenderPreference } from '../types'

const cases = ref<CaseItem[]>([])
const busy = ref(false)
const error = ref('')
const notice = ref('')
const router = useRouter()
const sharing = ref(false)

const startFor = ref<CaseItem | null>(null)
const startGender = ref<GenderPreference>('ANY')
const ageAny = ref(true)
const startAge = ref(30)

async function loadCases() {
  cases.value = await api.cases()
}

onMounted(async () => {
  if (casesShareNotice.value) {
    notice.value = casesShareNotice.value
    casesShareNotice.value = ''
  }
  await loadCases()
})
watch(casesVersion, loadCases)

function openStart(c: CaseItem) {
  startFor.value = c
  startGender.value = 'ANY'
  ageAny.value = true
  startAge.value = 30
  error.value = ''
  notice.value = ''
}

function closeStart() {
  startFor.value = null
}

function clampAge() {
  if (startAge.value < 18) startAge.value = 18
  if (startAge.value > 80) startAge.value = 80
}

async function confirmStart() {
  const c = startFor.value
  if (!c || busy.value) return
  if (!ageAny.value) clampAge()
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const reception = await api.startReception({
      difficulty: c.difficulty,
      caseId: c.id,
      gender: startGender.value,
      age: ageAny.value ? null : startAge.value
    })
    closeStart()
    closeLeftPanelOnMobile({ instant: true })
    await router.push(`/clients/${reception.client.id}`)
  } catch (e) {
    error.value = String(e)
  } finally {
    busy.value = false
  }
}

async function copyShareLink(c: CaseItem) {
  notice.value = ''
  error.value = ''
  sharing.value = true
  try {
    const link = await api.createCaseShareLink(c.id)
    const url = `${window.location.origin}${link.path}`
    await navigator.clipboard.writeText(url)
    notice.value = 'Ссылка скопирована — отправьте коллеге'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    sharing.value = false
  }
}

function sourceLabel(c: CaseItem) {
  if (c.visibilityLabel) return c.visibilityLabel
  switch (c.source) {
    case 'BUILTIN': return 'базовый'
    case 'MANUAL': return 'ручной'
    case 'AI_PROMPT': return 'из промпта'
    case 'AI_AUTO': return 'авто'
    case 'SHARED_COPY': return 'полученный'
    default: return ''
  }
}
</script>

<template>
  <div class="page-scroll">
    <div class="page-head">
      <h2 class="section-label left-scroll-label">Библиотека кейсов</h2>
      <p class="page-sub cases-lib-hint-desktop">
        Здесь учебные сценарии для практики: общие — доступны всем, ваши — только вам.
        Поделитесь своим кейсом ссылкой с коллегой. Перед стартом приёма можно уточнить
        пол и возраст клиента или оставить «Неважно».
      </p>
      <p class="page-sub cases-lib-hint-mobile">Выберите кейс, чтобы начать приём.</p>
    </div>
    <p v-if="error" class="error-text">{{ error }}</p>
    <p v-if="notice" class="ok-text">{{ notice }}</p>

    <div
      v-for="c in cases"
      :key="c.id"
      class="list-item"
    >
      <div class="row list-row">
        <div>
          <strong>{{ c.title }}</strong>
          <div class="muted">{{ c.presentingRequest }}</div>
          <div class="tags">
            <span class="tag">{{ difficultyLabel(c.difficulty, c.difficultyLabelRu) }}</span>
            <span class="tag" v-for="t in c.suggestedTags" :key="tagCode(t)">{{ tagLabel(t) }}</span>
            <span class="tag">{{ sourceLabel(c) }}</span>
          </div>
          <div v-if="c.notes" class="muted note-extra">{{ c.notes }}</div>
        </div>
        <div class="case-actions">
          <button
            v-if="c.canShare"
            class="icon-btn secondary"
            type="button"
            title="Поделиться ссылкой"
            aria-label="Поделиться ссылкой"
            :disabled="busy || sharing"
            @click="copyShareLink(c)"
          >
            <span class="material-symbols-outlined" aria-hidden="true">share</span>
          </button>
          <button
            class="icon-btn"
            type="button"
            title="Начать"
            aria-label="Начать"
            :disabled="busy"
            @click="openStart(c)"
          >
            <span class="material-symbols-outlined" aria-hidden="true">play_arrow</span>
          </button>
        </div>
      </div>
    </div>
    <p v-if="!cases.length" class="empty-hint">Пока нет кейсов.</p>

    <Teleport to="body">
      <div
        v-if="startFor"
        class="case-start-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="case-start-title"
      >
        <div class="case-start-card" :class="{ 'is-working': busy }">
          <div class="case-start-card-body">
            <div class="section-label">Новый приём</div>
            <h3 id="case-start-title">{{ startFor.title }}</h3>

            <AiWorkingBlock v-if="busy" compact />

            <template v-else>

              <div class="case-start-field">
                <span class="section-label">Пол</span>
                <div class="case-start-seg" role="group" aria-label="Пол">
                  <button type="button" :class="{ active: startGender === 'MALE' }" @click="startGender = 'MALE'">М</button>
                  <button type="button" :class="{ active: startGender === 'FEMALE' }" @click="startGender = 'FEMALE'">Ж</button>
                  <button type="button" :class="{ active: startGender === 'ANY' }" @click="startGender = 'ANY'">Неважно</button>
                </div>
              </div>

              <div class="case-start-field">
                <span class="section-label">Возраст (18–80)</span>
                <div class="case-start-seg" role="group" aria-label="Возраст режим">
                  <button type="button" :class="{ active: !ageAny }" @click="ageAny = false">Указать</button>
                  <button type="button" :class="{ active: ageAny }" @click="ageAny = true">Неважно</button>
                </div>
                <input
                  v-model.number="startAge"
                  class="field case-start-age-input"
                  type="number"
                  min="18"
                  max="80"
                  inputmode="numeric"
                  :disabled="ageAny"
                  :aria-hidden="ageAny"
                  @blur="clampAge"
                />
              </div>
            </template>
          </div>

          <div class="case-start-actions">
            <button type="button" class="toolbar-btn secondary" :disabled="busy" @click="closeStart">Отмена</button>
            <button
              type="button"
              class="toolbar-btn"
              :class="{ analyzing: busy }"
              :disabled="busy"
              @click="confirmStart"
            >
              <span
                class="material-symbols-outlined"
                :class="{ 'spin-slow': busy }"
                aria-hidden="true"
              >{{ busy ? 'progress_activity' : 'play_arrow' }}</span>
              <span>{{ busy ? 'Создаём…' : 'Начать' }}</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>
