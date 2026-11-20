<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  ariaLabel?: string
  /** Disallow picking a calendar day after today. */
  disableFuture?: boolean
  /** Absolute max as local `YYYY-MM-DDTHH:mm`. */
  max?: string
}>(), {
  placeholder: 'Выберите дату',
  ariaLabel: 'Дата и время',
  disableFuture: false,
  max: ''
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const WEEKDAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']
const MONTHS = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь'
]

const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)
const viewYear = ref(new Date().getFullYear())
const viewMonth = ref(new Date().getMonth())
const draftHour = ref(0)
const draftMinute = ref(0)
const draftDay = ref<number | null>(null)

const hours = Array.from({ length: 24 }, (_, i) => i)
const minutes = Array.from({ length: 12 }, (_, i) => i * 5)

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function parseValue(value: string): Date | null {
  if (!value) return null
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? null : d
}

function toValue(y: number, m: number, day: number, h: number, min: number) {
  return `${y}-${pad(m + 1)}-${pad(day)}T${pad(h)}:${pad(min)}`
}

function startOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

function endOfDay(d: Date) {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 0, 0)
}

/** Latest allowed instant: end of today and/or explicit max. */
function resolveMax(): Date | null {
  const candidates: Date[] = []
  if (props.disableFuture) candidates.push(endOfDay(new Date()))
  if (props.max) {
    const d = parseValue(props.max)
    if (d) candidates.push(d)
  }
  if (!candidates.length) return null
  return candidates.reduce((a, b) => (a.getTime() <= b.getTime() ? a : b))
}

function clampDateParts(y: number, m: number, day: number, h: number, min: number) {
  const max = resolveMax()
  let next = new Date(y, m, day, h, min, 0, 0)
  if (max && next.getTime() > max.getTime()) {
    next = new Date(max)
    next.setSeconds(0, 0)
    next.setMinutes(Math.floor(next.getMinutes() / 5) * 5)
  }
  return {
    y: next.getFullYear(),
    m: next.getMonth(),
    day: next.getDate(),
    h: next.getHours(),
    min: next.getMinutes()
  }
}

const selectedDate = computed(() => parseValue(props.modelValue))

const displayLabel = computed(() => {
  const d = selectedDate.value
  if (!d) return props.placeholder
  // Fixed-width numeric format so С/По stay visually aligned.
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

const calendarCells = computed(() => {
  const first = new Date(viewYear.value, viewMonth.value, 1)
  // Monday-first
  const startOffset = (first.getDay() + 6) % 7
  const daysInMonth = new Date(viewYear.value, viewMonth.value + 1, 0).getDate()
  const prevDays = new Date(viewYear.value, viewMonth.value, 0).getDate()
  const cells: { day: number; inMonth: boolean; key: string; date: Date; disabled: boolean }[] = []

  for (let i = 0; i < startOffset; i++) {
    const day = prevDays - startOffset + i + 1
    const date = new Date(viewYear.value, viewMonth.value - 1, day)
    cells.push({ day, inMonth: false, key: `p-${day}`, date, disabled: isDayDisabled(date) })
  }
  for (let day = 1; day <= daysInMonth; day++) {
    const date = new Date(viewYear.value, viewMonth.value, day)
    cells.push({ day, inMonth: true, key: `c-${day}`, date, disabled: isDayDisabled(date) })
  }
  while (cells.length % 7 !== 0 || cells.length < 42) {
    const day = cells.length - (startOffset + daysInMonth) + 1
    const date = new Date(viewYear.value, viewMonth.value + 1, day)
    cells.push({ day, inMonth: false, key: `n-${day}`, date, disabled: isDayDisabled(date) })
  }
  return cells
})

const monthTitle = computed(() => `${MONTHS[viewMonth.value]} ${viewYear.value}`)

const canGoNextMonth = computed(() => {
  const max = resolveMax()
  if (!max) return true
  const next = new Date(viewYear.value, viewMonth.value + 1, 1)
  return startOfDay(next).getTime() <= startOfDay(max).getTime()
})

function isSameDay(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth()
    && a.getDate() === b.getDate()
}

function isDayDisabled(date: Date) {
  const max = resolveMax()
  if (!max) return false
  return startOfDay(date).getTime() > startOfDay(max).getTime()
}

function isSelected(date: Date) {
  const s = selectedDate.value
  return !!s && isSameDay(s, date)
}

function isToday(date: Date) {
  return isSameDay(date, new Date())
}

function syncDraftFromValue() {
  const raw = selectedDate.value || new Date()
  const clamped = clampDateParts(
    raw.getFullYear(),
    raw.getMonth(),
    raw.getDate(),
    raw.getHours(),
    Math.round(raw.getMinutes() / 5) * 5 % 60
  )
  viewYear.value = clamped.y
  viewMonth.value = clamped.m
  draftDay.value = clamped.day
  draftHour.value = clamped.h
  draftMinute.value = clamped.min
}

watch(open, value => {
  if (value) syncDraftFromValue()
})

watch(
  () => props.modelValue,
  value => {
    if (!value || (!props.disableFuture && !props.max)) return
    const parsed = parseValue(value)
    const max = resolveMax()
    if (!parsed || !max || parsed.getTime() <= max.getTime()) return
    const clamped = clampDateParts(
      parsed.getFullYear(),
      parsed.getMonth(),
      parsed.getDate(),
      parsed.getHours(),
      parsed.getMinutes()
    )
    emit('update:modelValue', toValue(clamped.y, clamped.m, clamped.day, clamped.h, clamped.min))
  },
  { immediate: true }
)

function prevMonth() {
  if (viewMonth.value === 0) {
    viewMonth.value = 11
    viewYear.value -= 1
  } else {
    viewMonth.value -= 1
  }
}

function nextMonth() {
  if (!canGoNextMonth.value) return
  if (viewMonth.value === 11) {
    viewMonth.value = 0
    viewYear.value += 1
  } else {
    viewMonth.value += 1
  }
}

function pickDay(cell: { day: number; inMonth: boolean; date: Date; disabled: boolean }) {
  if (cell.disabled) return
  viewYear.value = cell.date.getFullYear()
  viewMonth.value = cell.date.getMonth()
  draftDay.value = cell.date.getDate()
  commit()
}

function commit() {
  if (draftDay.value == null) return
  const clamped = clampDateParts(
    viewYear.value,
    viewMonth.value,
    draftDay.value,
    draftHour.value,
    draftMinute.value
  )
  viewYear.value = clamped.y
  viewMonth.value = clamped.m
  draftDay.value = clamped.day
  draftHour.value = clamped.h
  draftMinute.value = clamped.min
  emit('update:modelValue', toValue(clamped.y, clamped.m, clamped.day, clamped.h, clamped.min))
}

function applyTime() {
  commit()
  open.value = false
}

function toggle() {
  open.value = !open.value
}

function close() {
  open.value = false
}

function onDocPointer(e: PointerEvent) {
  if (!open.value || !rootEl.value) return
  if (e.target instanceof Node && !rootEl.value.contains(e.target)) close()
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape' && open.value) {
    e.preventDefault()
    close()
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer)
  document.addEventListener('keydown', onKey)
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointer)
  document.removeEventListener('keydown', onKey)
})
</script>

<template>
  <div
    ref="rootEl"
    class="nice-dt"
    :class="{ open, 'has-value': !!selectedDate }"
  >
    <button
      type="button"
      class="nice-dt-trigger"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      aria-haspopup="dialog"
      @click="toggle"
    >
      <span class="material-symbols-outlined nice-dt-icon" aria-hidden="true">calendar_month</span>
      <span class="nice-dt-value">{{ displayLabel }}</span>
      <span class="material-symbols-outlined nice-dt-chevron" aria-hidden="true">expand_more</span>
    </button>

    <div
      v-show="open"
      class="nice-dt-pop"
      role="dialog"
      :aria-label="ariaLabel"
    >
      <div class="nice-dt-monthbar">
        <button type="button" class="nice-dt-nav" aria-label="Предыдущий месяц" @click="prevMonth">
          <span class="material-symbols-outlined" aria-hidden="true">chevron_left</span>
        </button>
        <div class="nice-dt-month-title">{{ monthTitle }}</div>
        <button
          type="button"
          class="nice-dt-nav"
          aria-label="Следующий месяц"
          :disabled="!canGoNextMonth"
          @click="nextMonth"
        >
          <span class="material-symbols-outlined" aria-hidden="true">chevron_right</span>
        </button>
      </div>

      <div class="nice-dt-weekdays" aria-hidden="true">
        <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
      </div>

      <div class="nice-dt-grid" role="grid">
        <button
          v-for="cell in calendarCells"
          :key="cell.key"
          type="button"
          class="nice-dt-day"
          :class="{
            muted: !cell.inMonth,
            selected: isSelected(cell.date),
            today: isToday(cell.date),
            disabled: cell.disabled
          }"
          :disabled="cell.disabled"
          @click="pickDay(cell)"
        >
          {{ cell.day }}
        </button>
      </div>

      <div class="nice-dt-time">
        <div class="nice-dt-time-row">
          <label class="nice-dt-time-field">
            <span>Часы</span>
            <select v-model.number="draftHour" class="nice-dt-time-select" @change="commit">
              <option v-for="h in hours" :key="h" :value="h">{{ pad(h) }}</option>
            </select>
          </label>
          <span class="nice-dt-time-sep" aria-hidden="true">:</span>
          <label class="nice-dt-time-field">
            <span>Минуты</span>
            <select v-model.number="draftMinute" class="nice-dt-time-select" @change="commit">
              <option v-for="m in minutes" :key="m" :value="m">{{ pad(m) }}</option>
            </select>
          </label>
        </div>
        <button type="button" class="toolbar-btn nice-dt-apply" @click="applyTime">
          Готово
        </button>
      </div>
    </div>
  </div>
</template>
