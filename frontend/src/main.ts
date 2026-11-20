import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { initAuth } from './auth/session'
import { initTheme } from './theme'
import { initPwaInstall } from './pwa'
import { startAppViewportWatch } from './appViewport'
import '@fontsource-variable/material-symbols-outlined/standard.css'
import './styles.css'
import './themes.css'

initTheme()
initPwaInstall()
startAppViewportWatch()

async function bootstrap() {
  try {
    await initAuth()
  } catch (err) {
    console.error(err)
  }
  const app = createApp(App)
  app.use(router)
  // Wait for the initial URL to resolve before mount — otherwise layoutMode /
  // Teleport targets can initialize against "/" while the real path is /progress etc.
  await router.isReady()
  app.mount('#app')
}

bootstrap()

