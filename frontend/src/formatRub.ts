/** Format ₽ amounts; keep enough precision for tiny embedding costs. */
export function formatRub(n?: number | null): string {
  if (n == null || Number.isNaN(Number(n))) return '—'
  const v = Number(n)
  if (v === 0) return '0 ₽'
  const abs = Math.abs(v)
  const digits = abs < 0.01 ? 6 : abs < 1 ? 4 : 2
  return `${v.toLocaleString('ru-RU', {
    minimumFractionDigits: 0,
    maximumFractionDigits: digits
  })} ₽`
}
