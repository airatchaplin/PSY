import type { AdminUsageStats } from './types'

export type GeneralUsageItem = {
  refId?: string | null
  label: string
  createdAt?: string | null
  calls: number
  tokens: number
  costRub: number
}

export type GeneralUsageCard = {
  source: string
  calls: number
  tokens: number
  costRub: number
  items: GeneralUsageItem[]
}

const CHAT_PURPOSES = new Set(['chat', 'stream', 'reception', 'session_return'])

/** Purposes shown in «Общее». */
export const GENERAL_PURPOSES = new Set([
  'analysis_session',
  'analysis_global',
  'chat',
  'stream',
  'reception',
  'session_return',
  'supervisor',
  'progress',
  'case',
  'embed',
  'tts',
  'stt'
])

const GENERAL_ORDER = [
  'analysis_session',
  'analysis_global',
  'chat',
  'supervisor',
  'progress',
  'case',
  'embed',
  'tts',
  'stt'
] as const

function normalizePurpose(purpose: string) {
  return CHAT_PURPOSES.has(purpose) ? 'chat' : purpose
}

function isExpandable(source: string) {
  return source !== 'embed'
    && source !== 'analysis_session'
    && source !== 'chat'
    && source !== 'supervisor'
    && source !== 'progress'
    && source !== 'tts'
    && source !== 'stt'
}

export function generalItemUnit(source: string) {
  if (source === 'case') return 'кейс.'
  if (source === 'analysis_global') return 'анализ.'
  if (source === 'tts') return 'озвуч.'
  if (source === 'stt') return 'голос.'
  return 'сеанс.'
}

export function canExpandGeneral(card: GeneralUsageCard) {
  return isExpandable(card.source) && card.items.length > 0
}

/** Build «Общее» cards: notebook/case/embed from API + session-scoped purposes from clients. */
export function buildGeneralUsageCards(stats: AdminUsageStats | null | undefined): GeneralUsageCard[] {
  const map = new Map<string, GeneralUsageCard>()
  for (const source of GENERAL_ORDER) {
    map.set(source, { source, calls: 0, tokens: 0, costRub: 0, items: [] })
  }

  for (const row of stats?.byGeneral || []) {
    const key = normalizePurpose(row.source)
    const card = map.get(key)
    if (!card) continue
    card.calls += row.calls
    card.tokens += row.tokens
    card.costRub += Number(row.costRub) || 0
    if (key === 'analysis_global' || key === 'case') {
      for (const item of row.items || []) {
        card.items.push({ ...item, costRub: Number(item.costRub) || 0 })
      }
    }
  }

  // Session-scoped purposes live on clients; fold into Общее items.
  for (const client of stats?.byClient || []) {
    if (!client.clientId) continue
    for (const session of client.bySession || []) {
      for (const purpose of session.byPurpose || []) {
        const key = normalizePurpose(purpose.source)
        const card = map.get(key)
        if (!card || key === 'analysis_global' || key === 'case' || key === 'embed') continue
        const calls = purpose.calls
        const tokens = purpose.tokens
        const costRub = Number(purpose.costRub) || 0
        card.calls += calls
        card.tokens += tokens
        card.costRub += costRub
        if (!isExpandable(key)) continue
        const sessionBit = session.sessionLabel || 'Сеанс'
        card.items.push({
          refId: session.sessionId || purpose.source,
          label: `${client.clientName} · ${sessionBit}`,
          createdAt: session.startedAt,
          calls,
          tokens,
          costRub
        })
      }
    }
  }

  // Fill gaps from bySource totals (e.g. embed without items, or legacy rows).
  for (const row of stats?.bySource || []) {
    const key = normalizePurpose(row.source)
    const card = map.get(key)
    if (!card) continue
    const srcCalls = row.calls
    const srcTokens = row.tokens
    const srcCost = Number(row.costRub) || 0
    if (card.calls < srcCalls) card.calls = srcCalls
    if (card.tokens < srcTokens) card.tokens = srcTokens
    if (card.costRub < srcCost) card.costRub = srcCost
  }

  for (const card of map.values()) {
    card.items.sort((a, b) => {
      const ca = a.createdAt ? Date.parse(a.createdAt) : 0
      const cb = b.createdAt ? Date.parse(b.createdAt) : 0
      return cb - ca
    })
  }

  return GENERAL_ORDER
    .map(source => map.get(source)!)
    .filter(card => card.calls > 0 || card.costRub > 0)
}
