import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { initKeycloak } from './auth/keycloak'
import { initTheme } from './theme'
import { initPwaInstall } from './pwa'
import './styles.css'
import './themes.css'

initTheme()
initPwaInstall()

async function bootstrap() {
  try {
    await initKeycloak()
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

