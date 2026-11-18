import { ref } from 'vue'

const TOKEN_STORAGE_KEY = 'psy.auth.token'
const API_BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8081').replace(/\/$/, '')

export const authenticated = ref(false)
/** Role ADMIN from JWT — drives Admin bookmark/route. */
export const isAdmin = ref(false)

type StoredAuth = {
  accessToken: string
  expiresAt?: number
}

let accessToken: string | undefined
let tokenParsed: Record<string, unknown> | undefined
let refreshTimer: number | undefined

function decodeJwt(token: string): Record<string, unknown> {
  const payload = token.split('.')[1]
  if (!payload) throw new Error('Некорректный токен')
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
  return JSON.parse(atob(padded)) as Record<string, unknown>
}

function rolesFromToken(): string[] {
  const roles = tokenParsed?.roles
  if (!Array.isArray(roles)) return []
  return roles.filter((r): r is string => typeof r === 'string')
}

export function hasAdminRole(): boolean {
  return rolesFromToken().some(r => r.toUpperCase() === 'ADMIN')
}

function refreshAdminFlag() {
  isAdmin.value = hasAdminRole()
}

function isExpired(expiresAt?: number): boolean {
  if (!expiresAt) return false
  return Date.now() >= expiresAt - 5_000
}

function persistToken(token: string, expiresIn?: number) {
  const expiresAt =
    typeof expiresIn === 'number' && expiresIn > 0
      ? Date.now() + expiresIn * 1000
      : (() => {
          const exp = tokenParsed?.exp
          return typeof exp === 'number' ? exp * 1000 : undefined
        })()
  const payload: StoredAuth = { accessToken: token, expiresAt }
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(payload))
}

function clearStoredToken() {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
}

function loadStored(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(TOKEN_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredAuth
    if (!parsed?.accessToken) return null
    return parsed
  } catch {
    return null
  }
}

function applyToken(token: string, expiresIn?: number) {
  accessToken = token
  tokenParsed = decodeJwt(token)
  if (isExpired(
    typeof expiresIn === 'number'
      ? Date.now() + expiresIn * 1000
      : typeof tokenParsed.exp === 'number'
        ? tokenParsed.exp * 1000
        : undefined
  )) {
    throw new Error('Сессия истекла')
  }
  persistToken(token, expiresIn)
  authenticated.value = true
  refreshAdminFlag()
}

function startExpiryWatch() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    const exp = tokenParsed?.exp
    if (typeof exp === 'number' && Date.now() >= exp * 1000 - 5_000) {
      logout()
    }
  }, 30_000)
}

export async function initAuth() {
  const saved = loadStored()
  if (!saved) {
    authenticated.value = false
    isAdmin.value = false
    return
  }
  try {
    if (isExpired(saved.expiresAt)) {
      clearStoredToken()
      authenticated.value = false
      return
    }
    applyToken(saved.accessToken)
    startExpiryWatch()
  } catch {
    clearStoredToken()
    authenticated.value = false
    isAdmin.value = false
  }
}

export async function loginWithPassword(username: string, password: string) {
  const res = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: username.trim(), password })
  })

  if (!res.ok) {
    let message = 'Неверный логин или пароль'
    try {
      const err = await res.json()
      if (typeof err.detail === 'string' && err.detail) message = err.detail
      else if (typeof err.message === 'string' && err.message) message = err.message
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  const data = (await res.json()) as {
    accessToken: string
    expiresIn?: number
  }
  if (!data.accessToken) {
    throw new Error('Сервер не вернул токен')
  }
  applyToken(data.accessToken, data.expiresIn)
  startExpiryWatch()
}

export function getToken(): string | undefined {
  return accessToken
}

export function logout() {
  clearStoredToken()
  try {
    sessionStorage.removeItem('psy.lastClientId')
  } catch {
    /* ignore */
  }
  accessToken = undefined
  tokenParsed = undefined
  authenticated.value = false
  isAdmin.value = false
  if (refreshTimer) window.clearInterval(refreshTimer)
  window.location.assign('/')
}

export function getUsername(): string {
  const preferred = tokenParsed?.preferred_username
  const name = tokenParsed?.name
  if (typeof preferred === 'string' && preferred) return preferred
  if (typeof name === 'string' && name) return name
  return 'therapist'
}
