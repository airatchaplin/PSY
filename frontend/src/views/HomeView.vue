<script setup lang="ts">
import { useRouter } from 'vue-router'
import { isMobileUi, requestChatClientsOnEnter } from '../sessionUi'

const router = useRouter()

function goToChat() {
  // Chat → Клиенты (search, +, list) on both mobile and desktop.
  if (isMobileUi()) {
    const last = sessionStorage.getItem('psy.lastClientId')
    requestChatClientsOnEnter()
    router.push(last ? `/clients/${last}` : '/clients')
    return
  }
  router.push('/clients')
}
</script>

<template>
  <div class="welcome-pane">
    <div class="mark">PSY</div>
    <h1>Тетрадь присутствия</h1>
    <p class="welcome-lead">
      Учебный симулятор гештальт-практики: ведите приём с ИИ-клиентом, сверяйтесь с супервизором
      и разбирайте сеанс по феноменологии, контакту и границам.
    </p>
    <p class="welcome-extra">
      Здесь можно тренировать формулировки, замечать фигуру и фон, пробовать эксперименты и копить
      заметки — без риска для реального человека. Это не терапия и не замена живой супервизии.
    </p>
    <div class="row home-actions">
      <button type="button" class="btn-text" @click="goToChat">
        <span class="material-symbols-outlined" aria-hidden="true">chat</span>
        Чат
      </button>
      <RouterLink to="/cases">
        <button class="btn-text secondary" type="button" tabindex="-1">
          <span class="material-symbols-outlined" aria-hidden="true">menu_book</span>
          Кейсы
        </button>
      </RouterLink>
      <RouterLink to="/progress">
        <button class="btn-text secondary" type="button" tabindex="-1">
          <span class="material-symbols-outlined" aria-hidden="true">monitoring</span>
          Прогресс
        </button>
      </RouterLink>
    </div>
  </div>
</template>
