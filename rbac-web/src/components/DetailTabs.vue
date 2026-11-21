<script setup lang="ts">
import { ref, useId } from 'vue'

const props = defineProps<{ value: string; tabs: { key: string; label: string }[]; label: string }>()
const emit = defineEmits<{ change: [string] }>()
const id = useId(), list = ref<HTMLElement>()
function onKey(event: KeyboardEvent) {
  const keys = props.tabs.map(tab => tab.key)
  const current = keys.indexOf(props.value)
  const index = event.key === 'ArrowRight' ? (current + 1) % keys.length
    : event.key === 'ArrowLeft' ? (current - 1 + keys.length) % keys.length
    : event.key === 'Home' ? 0 : event.key === 'End' ? keys.length - 1 : -1
  if (index < 0) return
  event.preventDefault()
  emit('change', keys[index])
  list.value?.querySelectorAll('button')[index]?.focus()
}
</script>
<template>
  <div ref="list" class="detail-tabs" role="tablist" :aria-label="label" @keydown="onKey">
    <button v-for="tab in tabs" :id="id + '-' + tab.key" :key="tab.key" type="button" role="tab"
      :aria-selected="tab.key === value" :aria-controls="id + '-panel-' + tab.key" :tabindex="tab.key === value ? 0 : -1"
      @click="$emit('change', tab.key)">{{ tab.label }}</button>
  </div>
  <div v-for="tab in tabs" :id="id + '-panel-' + tab.key" :key="tab.key" v-show="tab.key === value" role="tabpanel" :aria-labelledby="id + '-' + tab.key" tabindex="0">
    <slot v-if="tab.key === value" :name="tab.key" />
  </div>
</template>
