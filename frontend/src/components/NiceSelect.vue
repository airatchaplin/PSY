<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'

export interface NiceSelectOption {
  value: string
  label: string
  hint?: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  options: NiceSelectOption[]
  placeholder?: string
  ariaLabel?: string
}>(), {
  placeholder: 'Выберите…',
  ariaLabel: 'Список'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const open = ref(false)
const rootEl = ref<HTMLElement | null>(null)
const listEl = ref<HTMLElement | null>(null)
const activeIndex = ref(-1)

const selected = computed(() =>
  props.options.find(o => o.value === props.modelValue) || null
)

const displayLabel = computed(() => selected.value?.label || props.placeholder)

watch(open, async value => {
  if (!value) {
    activeIndex.value = -1
    return
  }
  const idx = props.options.findIndex(o => o.value === props.modelValue)
  activeIndex.value = idx >= 0 ? idx : 0
  await nextTick()
  const active = listEl.value?.querySelector<HTMLElement>('[data-active="true"]')
  active?.scrollIntoView({ block: 'nearest' })
})

function toggle() {
  open.value = !open.value
}

function close() {
  open.value = false
}

function pick(value: string) {
  emit('update:modelValue', value)
  close()
}

function onDocPointer(e: PointerEvent) {
  if (!open.value || !rootEl.value) return
  if (e.target instanceof Node && !rootEl.value.contains(e.target)) {
    close()
  }
}

function onKey(e: KeyboardEvent) {
  if (!open.value) {
    if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      open.value = true
    }
    return
  }
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    return
  }
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(props.options.length - 1, Math.max(0, activeIndex.value) + 1)
    return
  }
  if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(0, activeIndex.value - 1)
    return
  }
  if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault()
    const opt = props.options[activeIndex.value]
    if (opt) pick(opt.value)
  }
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocPointer)
})
onUnmounted(() => {
  document.removeEventListener('pointerdown', onDocPointer)
})
</script>

<template>
  <div
    ref="rootEl"
    class="nice-select"
    :class="{ open, 'has-value': !!selected }"
  >
    <button
      type="button"
      class="nice-select-trigger"
      :aria-label="ariaLabel"
      :aria-expanded="open"
      aria-haspopup="listbox"
      @click="toggle"
      @keydown="onKey"
    >
      <span class="nice-select-value">{{ displayLabel }}</span>
      <span class="material-symbols-outlined nice-select-chevron" aria-hidden="true">expand_more</span>
    </button>

    <div
      v-show="open"
      ref="listEl"
      class="nice-select-menu"
      role="listbox"
      :aria-label="ariaLabel"
    >
      <button
        v-for="(opt, i) in options"
        :key="opt.value || `empty-${i}`"
        type="button"
        class="nice-select-option"
        role="option"
        :aria-selected="opt.value === modelValue"
        :data-active="i === activeIndex"
        :class="{
          selected: opt.value === modelValue,
          active: i === activeIndex
        }"
        @click="pick(opt.value)"
        @mouseenter="activeIndex = i"
      >
        <span class="nice-select-option-copy">
          <span class="nice-select-option-label">{{ opt.label }}</span>
          <span v-if="opt.hint" class="nice-select-option-hint">{{ opt.hint }}</span>
        </span>
        <span
          v-if="opt.value === modelValue"
          class="material-symbols-outlined nice-select-check"
          aria-hidden="true"
        >check</span>
      </button>
    </div>
  </div>
</template>
