/** Shown when balance blocks LLM (local gate or HTTP 402). */
export const INSUFFICIENT_FUNDS_MSG =
  'Недостаточно средств на балансе. Обратитесь к администратору для пополнения.'

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export function isInsufficientFundsError(e: unknown): boolean {
  if (e instanceof ApiError && e.status === 402) return true
  const msg = e instanceof Error ? e.message : String(e)
  return /недостаточно средств|баланс отрицательн|payment.?required|402/i.test(msg)
}

export function apiErrorMessage(e: unknown, fallback = 'Ошибка запроса'): string {
  if (isInsufficientFundsError(e)) return INSUFFICIENT_FUNDS_MSG
  if (e instanceof Error && e.message.trim()) return e.message.trim()
  const raw = String(e ?? '').trim()
  return raw || fallback
}
