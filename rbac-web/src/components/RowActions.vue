<script setup lang="ts">
import { NButton, NDropdown } from 'naive-ui'
defineProps<{ options: { key: string; label: string; disabled?: boolean }[]; label: string; text?: string }>()
const emit = defineEmits<{ select: [string] }>()
function select(key: string | number) { emit('select', String(key)) }
</script>
<template>
  <NDropdown v-if="options.length" :options="options" trigger="click" @select="select">
    <NButton size="small" :aria-label="label">{{ text ?? '更多' }}</NButton>
  </NDropdown>
</template>
