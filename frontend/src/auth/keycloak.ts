import Keycloak from 'keycloak-js'
import { ref } from 'vue'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8082',
  realm: import.meta.env.VITE_KEYCLOAK_REALM || 'psy',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'psy-web'
})

const TOKEN_STORAGE_KEY = 'psy.kc.tokens'

export const authenticated = ref(false)
/** Realm role `admin` from the current JWT — drives Admin bookmark/route. */
export const isAdmin = ref(false)

type StoredTokens = {
  access_token: string
  refresh_token: string
  id_token?: string
}

function authServerUrl(): string {
  return (import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8082').replace(/\/$/, '')
}

function realmName(): string {
  return import.meta.env.VITE_KEYCLOAK_REALM || 'psy'
}

function clientId(): string {
  return import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'psy-web'
}

function loadStoredTokens(): StoredTokens | null {
  try {
    const raw = sessionStorage.getItem(TOKEN_STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as StoredTokens
    if (!parsed?.access_token || !parsed?.refresh_token) return null
    return parsed
  } catch {
    return null
  }
}

function persistTokens() {
  if (!keycloak.token || !keycloak.refreshToken) {
    sessionStorage.removeItem(TOKEN_STORAGE_KEY)
    return
  }
  const payload: StoredTokens = {
    access_token: keycloak.token,
    refresh_token: keycloak.refreshToken,
    id_token: keycloak.idToken
  }
  sessionStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(payload))
}

function clearStoredTokens() {
  sessionStorage.removeItem(TOKEN_STORAGE_KEY)
}

function decodeJwt<T extends Record<string, unknown> = Record<string, unknown>>(token: string): T {
  const payload = token.split('.')[1]
  if (!payload) throw new Error('Некорректный токен')
  const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4)
  const json = atob(padded)
  return JSON.parse(json) as T
}

function rolesFromToken(): string[] {
  const realm = keycloak.realmAccess?.roles || []
  const parsed = (keycloak.tokenParsed as { realm_access?: { roles?: string[] } } | undefined)
    ?.realm_access?.roles || []
  const resource = keycloak.resourceAccess || {}
  const resourceRoles = Object.values(resource).flatMap(ra => ra?.roles || [])
  return [...realm, ...parsed, ...resourceRoles]
}

export function hasAdminRole(): boolean {
  return rolesFromToken().some(r => r.toLowerCase() === 'admin')
}

function refreshAdminFlag() {
  isAdmin.value = hasAdminRole()
}

function applyTokens(data: StoredTokens) {
  keycloak.token = data.access_token
  keycloak.refreshToken = data.refresh_token
  keycloak.idToken = data.id_token
  keycloak.authenticated = true

  const access = decodeJwt(data.access_token)
  keycloak.tokenParsed = access as typeof keycloak.tokenParsed
  keycloak.subject = typeof access.sub === 'string' ? access.sub : undefined
  keycloak.sessionId = typeof access.sid === 'string' ? access.sid : undefined
  keycloak.realmAccess = access.realm_access as typeof keycloak.realmAccess
  keycloak.resourceAccess = access.resource_access as typeof keycloak.resourceAccess

  if (data.refresh_token) {
    try {
      keycloak.refreshTokenParsed = decodeJwt(data.refresh_token) as typeof keycloak.refreshTokenParsed
    } catch {
      keycloak.refreshTokenParsed = undefined
    }
  }
  if (data.id_token) {
    try {
      keycloak.idTokenParsed = decodeJwt(data.id_token) as typeof keycloak.idTokenParsed
    } catch {
      keycloak.idTokenParsed = undefined
    }
  }

  const iat = typeof access.iat === 'number' ? access.iat : Math.floor(Date.now() / 1000)
  keycloak.timeSkew = Math.floor(Date.now() / 1000) - iat
  persistTokens()
  authenticated.value = true
  refreshAdminFlag()
}

let refreshTimer: number | undefined

function startRefreshLoop() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (!keycloak.authenticated) return
    keycloak.updateToken(30)
      .then(() => {
        persistTokens()
        refreshAdminFlag()
      })
      .catch(() => {
        clearStoredTokens()
        authenticated.value = false
        isAdmin.value = false
        keycloak.authenticated = false
      })
  }, 20000)
}

function clearAuthHashFromUrl() {
  const hash = window.location.hash || ''
  if (
    hash.includes('error=') ||
    (hash.includes('state=') && hash.includes('iss='))
  ) {
    const url = window.location.pathname + window.location.search
    window.history.replaceState(null, '', url || '/')
  }
}

export async function initKeycloak() {
  // Must clear before init: keycloak-js parses #error=login_required from a prior
  // check-sso redirect and would wipe an otherwise valid password-grant session.
  clearAuthHashFromUrl()

  const saved = loadStoredTokens()
  try {
    // No onLoad/check-sso: login is our LoginView + password grant.
    await keycloak.init({
      pkceMethod: 'S256',
      checkLoginIframe: false,
      ...(saved
        ? {
            token: saved.access_token,
            refreshToken: saved.refresh_token,
            idToken: saved.id_token
          }
        : {})
    })
  } catch {
    // Tokens may still be valid even if init threw because of a stale callback hash.
    if (saved) {
      try {
        applyTokens(saved)
        await keycloak.updateToken(-1)
        persistTokens()
        startRefreshLoop()
        return keycloak
      } catch {
        /* fall through */
      }
    }
    clearStoredTokens()
    authenticated.value = false
    clearAuthHashFromUrl()
    return keycloak
  }

  clearAuthHashFromUrl()

  if (keycloak.authenticated) {
    authenticated.value = true
    refreshAdminFlag()
    persistTokens()
    startRefreshLoop()
    return keycloak
  }

  // Init succeeded but reported logged-out; restore password-grant tokens if present.
  if (saved) {
    try {
      applyTokens(saved)
      await keycloak.updateToken(30)
      persistTokens()
      startRefreshLoop()
      return keycloak
    } catch {
      /* tokens expired */
    }
  }

  clearStoredTokens()
  authenticated.value = false
  return keycloak
}

export async function loginWithPassword(username: string, password: string) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: clientId(),
    username: username.trim(),
    password
  })

  const res = await fetch(
    `${authServerUrl()}/realms/${realmName()}/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body
    }
  )

  if (!res.ok) {
    let message = 'Неверный логин или пароль'
    try {
      const err = await res.json()
      if (typeof err.error_description === 'string' && err.error_description) {
        message = err.error_description
      }
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  const data = (await res.json()) as StoredTokens & {
    access_token: string
    refresh_token: string
    id_token?: string
  }

  applyTokens({
    access_token: data.access_token,
    refresh_token: data.refresh_token,
    id_token: data.id_token
  })
  startRefreshLoop()
}

export function getToken(): string | undefined {
  return keycloak.token
}

export function logout() {
  clearStoredTokens()
  try {
    sessionStorage.removeItem('psy.lastClientId')
  } catch {
    /* ignore */
  }
  authenticated.value = false
  isAdmin.value = false
  if (refreshTimer) window.clearInterval(refreshTimer)
  keycloak.token = undefined
  keycloak.refreshToken = undefined
  keycloak.idToken = undefined
  keycloak.authenticated = false
  keycloak.tokenParsed = undefined
  keycloak.idTokenParsed = undefined
  keycloak.refreshTokenParsed = undefined
  keycloak.realmAccess = undefined
  keycloak.resourceAccess = undefined
  window.location.assign('/')
}

export function getUsername(): string {
  return keycloak.tokenParsed?.preferred_username || keycloak.tokenParsed?.name || 'therapist'
}

export { keycloak }
