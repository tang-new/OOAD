<script setup lang="ts">
withDefaults(defineProps<{ type?: 'info' | 'success' | 'warning' | 'error'; title?: string; urgent?: boolean }>(), { type: 'info', urgent: false })
</script>
<template>
  <div class="notice" :class="'notice-' + type" :role="urgent ? 'alert' : 'status'">
    <span class="notice-icon" aria-hidden="true">{{ type === 'success' ? '✓' : type === 'info' ? 'i' : '!' }}</span>
    <div><strong v-if="title">{{ title }}</strong><div><slot /></div></div>
  </div>
</template>
