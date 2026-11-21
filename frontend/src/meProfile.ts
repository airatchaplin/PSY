import { computed, ref } from 'vue'
import type { Me } from './types'

/** Shared profile for balance / LLM gate across views. */
export const meProfile = ref<Me | null>(null)

export const llmAllowed = computed(() => {
  const me = meProfile.value
  if (!me) return true
  if (typeof me.llmAllowed === 'boolean') return me.llmAllowed
  const balance = Number(me.balanceRub ?? 0)
  return Number.isFinite(balance) ? balance > 0 : true
})

/** User preference from profile (admin no longer gates availability). */
export const ttsEnabled = computed(() => !!meProfile.value?.ttsEnabled)

export const sttEnabled = computed(() => !!meProfile.value?.sttEnabled)

export const balanceRub = computed(() => {
  const n = Number(meProfile.value?.balanceRub ?? 0)
  return Number.isFinite(n) ? n : 0
})

/** Zero balance — warn (yellow). Negative — block styling (red). */
export const balanceIsZero = computed(() => balanceRub.value === 0)
export const balanceIsNegative = computed(() => balanceRub.value < 0)

export const appBalanceRub = computed(() => {
  const n = meProfile.value?.appBalanceRub
  if (n == null) return null
  const v = Number(n)
  return Number.isFinite(v) ? v : null
})

export const usersBalanceTotalRub = computed(() => {
  const n = meProfile.value?.usersBalanceTotalRub
  if (n == null) return null
  const v = Number(n)
  return Number.isFinite(v) ? v : null
})

export function setMeProfile(me: Me | null) {
  meProfile.value = me
}

export function formatBalanceRub(n?: number | null): string {
  const v = Number(n ?? 0)
  if (!Number.isFinite(v)) return '—'
  return `${v.toLocaleString('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 2 })} ₽`
}
