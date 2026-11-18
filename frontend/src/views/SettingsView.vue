<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api/client'
import { logout } from '../auth/keycloak'
import {
  canInstallPwa,
  isPwaInstalled,
  promptPwaInstall,
  showIosInstallHint
} from '../pwa'
import {
  THEMES,
  currentTheme,
  isSystemTheme,
  setSystemTheme,
  setTheme,
  type ThemeId
} from '../theme'
import type { Me } from '../types'

const me = ref<Me | null>(null)
const installBusy = ref(false)
const installMsg = ref('')

onMounted(async () => {
  try {
    me.value = await api.me()
  } catch {
    me.value = null
  }
})

function pickTheme(id: ThemeId) {
  setTheme(id)
}

function pickSystemTheme() {
  setSystemTheme()
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
  <div class="settings-page page-scroll">
    <div class="page-head compact">
      <h2 class="section-label left-scroll-label">Настройки</h2>
      <p class="page-sub">Тема тетради и аккаунт.</p>
    </div>

    <section class="settings-section">
      <div class="section-label">Аккаунт</div>
      <div class="settings-account">
        <div class="settings-avatar" aria-hidden="true">
          <span class="material-symbols-outlined">person</span>
        </div>
        <div class="settings-account-copy">
          <strong>{{ me?.displayName || 'Терапевт' }}</strong>
          <span v-if="me?.email" class="muted">{{ me.email }}</span>
        </div>
      </div>
    </section>

    <section class="settings-section">
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

    <section class="settings-section settings-install">
      <div class="section-label">Ярлык на телефон</div>
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
    </section>

    <section class="settings-section">
      <div class="section-label">Сессия</div>
      <button type="button" class="toolbar-btn danger-outline" @click="logout">
        <span class="material-symbols-outlined" aria-hidden="true">logout</span>
        Выйти из аккаунта
      </button>
    </section>
  </div>
</template>
