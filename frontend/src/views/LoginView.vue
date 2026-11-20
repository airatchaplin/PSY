<script setup lang="ts">
import { ref } from 'vue'
import { loginWithPassword } from '../auth/session'

const emit = defineEmits<{
  success: []
}>()

const username = ref('')
const password = ref('')
const showPassword = ref(false)
const busy = ref(false)
const error = ref('')

async function submit() {
  if (busy.value) return
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = 'Введите логин и пароль'
    return
  }
  busy.value = true
  try {
    await loginWithPassword(username.value, password.value)
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="notebook auth-notebook">
    <div class="page-left auth-left">
      <div class="auth-brand-pane">
        <div class="auth-mark" aria-hidden="true">PSY</div>
        <h2>Тетрадь практики</h2>
        <p class="muted">
          Учебный симулятор гештальт-приёмов: клиент, супервизор и разбор сессии — в одной тетради.
        </p>
        <ul class="auth-points">
          <li>Практика контакта и феноменологии</li>
          <li>Супервизия в отдельной панели</li>
          <li>Анализ по гештальт-рубрике</li>
        </ul>
      </div>
    </div>

    <div class="notebook-spine" aria-hidden="true"></div>

    <div class="page-right auth-right">
      <form class="auth-card" @submit.prevent="submit">
        <div class="auth-mobile-mark" aria-hidden="true">PSY</div>

        <label class="auth-field">
          <span>Логин</span>
          <input
            v-model="username"
            type="text"
            name="username"
            autocomplete="username"
            autocapitalize="off"
            spellcheck="false"
            placeholder="логин"
            :disabled="busy"
          />
        </label>

        <label class="auth-field">
          <span>Пароль</span>
          <div class="auth-password-row">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              name="password"
              autocomplete="current-password"
              placeholder="••••••••"
              :disabled="busy"
            />
            <button
              class="icon-btn secondary auth-eye"
              type="button"
              :title="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
              :aria-label="showPassword ? 'Скрыть пароль' : 'Показать пароль'"
              :disabled="busy"
              @click="showPassword = !showPassword"
            >
              <span class="material-symbols-outlined" aria-hidden="true">
                {{ showPassword ? 'visibility_off' : 'visibility' }}
              </span>
            </button>
          </div>
        </label>

        <p v-if="error" class="error-text auth-error">{{ error }}</p>

        <button class="toolbar-btn auth-submit" type="submit" :disabled="busy">
          <span
            class="material-symbols-outlined"
            :class="{ 'spin-slow': busy }"
            aria-hidden="true"
          >{{ busy ? 'progress_activity' : 'login' }}</span>
          {{ busy ? 'Входим…' : 'Войти' }}
        </button>
      </form>
    </div>
  </div>
</template>
