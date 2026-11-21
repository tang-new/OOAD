<script setup lang="ts">
import { NAlert, NButton, NEmpty, NSpin } from 'naive-ui'
defineProps<{ loading?: boolean; error?: string; empty?: boolean; description?: string }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <NAlert v-if="error" type="error" title="暂时无法加载" role="alert">
    {{ error }} <NButton text type="primary" @click="$emit('retry')">重试</NButton>
  </NAlert>
  <div v-else-if="loading" class="read-state" role="status"><NSpin size="small" /> 加载中…</div>
  <NEmpty v-else-if="empty" class="read-state" :description="description ?? '暂无数据'" />
</template>
