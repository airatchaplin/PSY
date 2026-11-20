/**
 * Keep the app shell height equal to the visual viewport.
 * Do NOT follow visualViewport.offsetTop — on iOS that fights Safari's own
 * keyboard scroll and leaves a huge gap above the keyboard.
 */

function syncAppViewport() {
  if (typeof window === 'undefined') return
  const vv = window.visualViewport
  const height = Math.round(vv?.height ?? window.innerHeight)
  document.documentElement.style.setProperty('--app-height', `${height}px`)
  // Cancel Safari scroll jump when the keyboard opens.
  if (window.scrollY !== 0 || window.scrollX !== 0) {
    window.scrollTo(0, 0)
  }
}

let started = false

export function startAppViewportWatch() {
  if (typeof window === 'undefined' || started) return
  started = true
  syncAppViewport()
  window.visualViewport?.addEventListener('resize', syncAppViewport)
  window.visualViewport?.addEventListener('scroll', syncAppViewport)
  window.addEventListener('resize', syncAppViewport)
  window.addEventListener('orientationchange', syncAppViewport)
}

export function stopAppViewportWatch() {
  if (!started) return
  started = false
  window.visualViewport?.removeEventListener('resize', syncAppViewport)
  window.visualViewport?.removeEventListener('scroll', syncAppViewport)
  window.removeEventListener('resize', syncAppViewport)
  window.removeEventListener('orientationchange', syncAppViewport)
}
