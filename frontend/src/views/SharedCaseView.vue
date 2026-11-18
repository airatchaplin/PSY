<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/client'
import {
  bumpCasesVersion,
  casesShareNotice,
  setCasesMobileTab,
  stashPendingShareToken
} from '../casesUi'
import { authenticated } from '../auth/session'

const route = useRoute()
const router = useRouter()
const status = ref('Добавляем кейс…')
const error = ref('')

async function importToken(token: string) {
  const result = await api.importSharedCase(token)
  bumpCasesVersion()
  setCasesMobileTab('library')
  casesShareNotice.value = result.alreadyHad
    ? `Кейс «${result.caseItem.title}» уже есть в вашей библиотеке`
    : `Кейс «${result.caseItem.title}» добавлен в библиотеку`
  await router.replace('/cases')
}

onMounted(async () => {
  const raw = route.params.token
  const token = typeof raw === 'string' ? raw.trim() : ''
  if (!token) {
    error.value = 'Некорректная ссылка'
    return
  }
  if (!authenticated.value) {
    stashPendingShareToken(token)
    status.value = 'Войдите, чтобы получить кейс'
    return
  }
  try {
    await importToken(token)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
    status.value = ''
  }
})
</script>

<template>
  <div class="page-scroll">
    <div class="page-head">
      <div class="section-label">Кейс по ссылке</div>
      <h2 class="page-title">Общий кейс</h2>
      <p v-if="status && !error" class="page-sub">{{ status }}</p>
      <p v-if="error" class="error-text">{{ error }}</p>
      <button
        v-if="error"
        type="button"
        class="toolbar-btn secondary"
        @click="router.push('/cases')"
      >
        К библиотеке
      </button>
    </div>
  </div>
</template>
