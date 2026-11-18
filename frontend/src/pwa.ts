import { computed, ref } from 'vue'

/** Chromium `beforeinstallprompt` event (not in all TS libs). */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)
const installed = ref(false)

function isStandaloneDisplay(): boolean {
  if (typeof window === 'undefined') return false
  const mq = window.matchMedia?.('(display-mode: standalone)')?.matches
  const iosStandalone = 'standalone' in navigator && (navigator as Navigator & { standalone?: boolean }).standalone === true
  return Boolean(mq || iosStandalone)
}

export function initPwaInstall() {
  if (typeof window === 'undefined') return
  installed.value = isStandaloneDisplay()

  window.addEventListener('beforeinstallprompt', (e: Event) => {
    e.preventDefault()
    deferredPrompt.value = e as BeforeInstallPromptEvent
  })

  window.addEventListener('appinstalled', () => {
    installed.value = true
    deferredPrompt.value = null
  })
}

export function isIosDevice() {
  if (typeof navigator === 'undefined') return false
  return /iphone|ipad|ipod/i.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
}

export const canInstallPwa = computed(() => !installed.value && !!deferredPrompt.value)
export const showIosInstallHint = computed(() => !installed.value && isIosDevice() && !isStandaloneDisplay())
export const isPwaInstalled = computed(() => installed.value)

export async function promptPwaInstall(): Promise<'accepted' | 'dismissed' | 'unavailable'> {
  const ev = deferredPrompt.value
  if (!ev) return 'unavailable'
  await ev.prompt()
  const { outcome } = await ev.userChoice
  deferredPrompt.value = null
  if (outcome === 'accepted') installed.value = true
  return outcome
}
