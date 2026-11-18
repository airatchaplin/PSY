import { getToken } from '../auth/session'
import type {
  AdminModelOption,
  AdminOverview,
  AdminUsageQuery,
  AdminUsageStats,
  Analysis,
  CaseItem,
  Client,
  CreateCasePayload,
  CreateAppUserPayload,
  AppUser,
  Me,
  CaseShareLink,
  ImportSharedCaseResult,
  ShareCaseResult,
  StartReceptionPayload,
  TherapistBrief,
  Message,
  Note,
  Progress,
  PromptDetail,
  PromptSummary,
  Reception,
  Session,
  SessionSummary
} from '../types'

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081'

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers || {})
  headers.set('Authorization', `Bearer ${getToken()}`)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(`${API_URL}${path}`, { ...init, headers })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || res.statusText)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return res.json() as Promise<T>
}

export const api = {
  me: () => request<Me>('/api/me'),
  acceptDisclaimer: () => request<Me>('/api/me/accept-disclaimer', { method: 'POST' }),
  clients: () => request<Client[]>('/api/clients'),
  client: (id: string) => request<Client>(`/api/clients/${id}`),
  startReception: (body?: StartReceptionPayload) =>
    request<Reception>('/api/receptions/start', { method: 'POST', body: JSON.stringify(body || {}) }),
  continueSession: (clientId: string) =>
    request<Session>(`/api/clients/${clientId}/sessions/start`, { method: 'POST' }),
  endSession: (sessionId: string, elapsedSeconds?: number) =>
    request<Session>(`/api/sessions/${sessionId}/end`, {
      method: 'POST',
      body: JSON.stringify({ elapsedSeconds: elapsedSeconds ?? null, paused: true })
    }),
  updateTimer: (sessionId: string, body: { elapsedSeconds: number; paused: boolean }) =>
    request<Session>(`/api/sessions/${sessionId}/timer`, {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  sessions: (clientId: string) =>
    request<SessionSummary[]>(`/api/clients/${clientId}/sessions`),
  messages: (clientId: string) => request<Message[]>(`/api/clients/${clientId}/messages`),
  sessionMessages: (sessionId: string) =>
    request<Message[]>(`/api/sessions/${sessionId}/messages`),
  sendMessage: (sessionId: string, content: string) =>
    request<Message>(`/api/sessions/${sessionId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content })
    }),
  /** Starts a session if needed (therapist initiates) and sends the message. */
  sendToClient: (clientId: string, content: string) =>
    request<Message>(`/api/clients/${clientId}/messages`, {
      method: 'POST',
      body: JSON.stringify({ content })
    }),
  notes: (clientId: string) => request<Note[]>(`/api/clients/${clientId}/notes`),
  createNote: (clientId: string, content: string, sessionId?: string) =>
    request<Note>(`/api/clients/${clientId}/notes`, {
      method: 'POST',
      body: JSON.stringify({ content, sessionId })
    }),
  supervisorMessages: (clientId: string) =>
    request<Message[]>(`/api/clients/${clientId}/supervisor/messages`),
  askSupervisor: (clientId: string, content: string) =>
    request<Message>(`/api/clients/${clientId}/supervisor/messages`, {
      method: 'POST',
      body: JSON.stringify({ content })
    }),
  getSessionAnalysis: (sessionId: string) =>
    request<Analysis>(`/api/sessions/${sessionId}/analysis`),
  analyzeSession: (sessionId: string) =>
    request<Analysis>(`/api/sessions/${sessionId}/analysis`, { method: 'POST' }),
  analyzeGlobal: () => request<Analysis>('/api/analysis/global', { method: 'POST' }),
  progress: () => request<Progress>('/api/progress'),
  cases: () => request<CaseItem[]>('/api/cases'),
  casePeers: () => request<TherapistBrief[]>('/api/cases/peers'),
  createCase: (body: CreateCasePayload) =>
    request<CaseItem>('/api/cases', { method: 'POST', body: JSON.stringify(body) }),
  generateCase: (prompt?: string) =>
    request<CaseItem>('/api/cases/generate', {
      method: 'POST',
      body: JSON.stringify({ prompt: prompt || null })
    }),
  shareCase: (id: string, body: { therapistIds?: string[]; all?: boolean }) =>
    request<ShareCaseResult>(`/api/cases/${id}/share`, {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  createCaseShareLink: (id: string) =>
    request<CaseShareLink>(`/api/cases/${id}/share-link`, { method: 'POST' }),
  importSharedCase: (token: string) =>
    request<ImportSharedCaseResult>(`/api/cases/shared/${encodeURIComponent(token)}/import`, {
      method: 'POST'
    }),
  adminOverview: () => request<AdminOverview>('/api/admin/overview'),
  adminModels: () => request<AdminModelOption[]>('/api/admin/models'),
  adminSetModel: (model: string) =>
    request<AdminOverview>('/api/admin/model', {
      method: 'PUT',
      body: JSON.stringify({ model })
    }),
  adminSetConnection: (body: { baseUrl: string; apiKey?: string; embeddingModel?: string }) =>
    request<AdminOverview>('/api/admin/connection', {
      method: 'PUT',
      body: JSON.stringify(body)
    }),
  adminPrompts: () => request<PromptSummary[]>('/api/admin/prompts'),
  adminPrompt: (id: string) => request<PromptDetail>(`/api/admin/prompts/${encodeURIComponent(id)}`),
  adminSavePrompt: (id: string, content: string) =>
    request<PromptDetail>(`/api/admin/prompts/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify({ content })
    }),
  adminUsage: (query: AdminUsageQuery | number = 30) => {
    const params = new URLSearchParams()
    if (typeof query === 'number') {
      params.set('days', String(query))
    } else {
      if (query.days != null) params.set('days', String(query.days))
      if (query.from) params.set('from', query.from)
      if (query.to) params.set('to', query.to)
      if (query.model) params.set('model', query.model)
      if (query.source) params.set('source', query.source)
    }
    const qs = params.toString()
    return request<AdminUsageStats>(`/api/admin/usage${qs ? `?${qs}` : ''}`)
  },
  adminUsers: () => request<AppUser[]>('/api/admin/users'),
  adminCreateUser: (body: CreateAppUserPayload) =>
    request<AppUser>('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify(body)
    })
}
