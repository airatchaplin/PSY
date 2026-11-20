import type { RouteLocationRaw } from 'vue-router'
import { requestSessionReview } from './sessionUi'

export type UsageSessionNav = {
  sessionId?: string | null
  status?: string | null
  hasAnalysis?: boolean
}

/** Open live chat or a closed session review; optionally jump to analysis panel. */
export function usageChatRoute(
  clientId: string,
  session?: UsageSessionNav | null,
  view?: 'analysis' | null
): RouteLocationRaw {
  if (!session?.sessionId) {
    return `/clients/${clientId}`
  }
  const status = String(session.status || '').toUpperCase()
  // Live chat only when explicitly opening an active session without analysis view.
  if (status === 'ACTIVE' && view !== 'analysis') {
    return `/clients/${clientId}`
  }
  // Always persist intent before navigation (survives App.vue / ChatView remount races).
  requestSessionReview(session.sessionId, view === 'analysis')
  const query: Record<string, string> = { session: session.sessionId }
  if (view === 'analysis') {
    query.view = 'analysis'
  }
  return { path: `/clients/${clientId}`, query }
}
