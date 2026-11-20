export type UsageDayRow = {
  day: string
  calls: number
  tokens: number
  costRub: number
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function localDayKey(d: Date) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function localHourKey(d: Date) {
  return `${localDayKey(d)} ${pad2(d.getHours())}:00`
}

function parseLocalInput(value?: string | null): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

/**
 * Fill every day (or hour) in the selected filter range, zeroing buckets
 * that have no LLM usage yet.
 */
export function fillUsageChartSeries(
  rows: UsageDayRow[] | null | undefined,
  fromInput: string,
  toInput: string,
  granularity?: string | null
): UsageDayRow[] {
  const existing = rows || []
  const from = parseLocalInput(fromInput)
  const to = parseLocalInput(toInput)
  if (!from || !to || to < from) {
    return existing
  }

  const byKey = new Map(existing.map(r => [r.day, r]))
  const hourly =
    granularity === 'hour'
    || existing.some(r => r.day.includes(' '))
    || (to.getTime() - from.getTime()) <= 48 * 60 * 60 * 1000

  const out: UsageDayRow[] = []
  const cursor = new Date(from)

  if (hourly) {
    cursor.setMinutes(0, 0, 0)
    let guard = 0
    while (cursor <= to && guard < 24 * 14) {
      const key = localHourKey(cursor)
      const hit = byKey.get(key)
      out.push(hit ? { ...hit } : { day: key, calls: 0, tokens: 0, costRub: 0 })
      cursor.setHours(cursor.getHours() + 1)
      guard += 1
    }
  } else {
    cursor.setHours(0, 0, 0, 0)
    const endDay = new Date(to)
    endDay.setHours(0, 0, 0, 0)
    let guard = 0
    while (cursor <= endDay && guard < 400) {
      const key = localDayKey(cursor)
      const hit = byKey.get(key)
      out.push(hit ? { ...hit } : { day: key, calls: 0, tokens: 0, costRub: 0 })
      cursor.setDate(cursor.getDate() + 1)
      guard += 1
    }
  }

  return out.length ? out : existing
}
