<script setup lang="ts">
import { NButton } from 'naive-ui'
import Notice from './Notice.vue'
defineProps<{ error: string; message: string; stale: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <Notice v-if="error" type="error" title="操作失败" urgent>{{ error }}</Notice>
  <Notice v-else-if="message" :type="stale ? 'warning' : 'success'">
    {{ message }}
    <NButton v-if="stale" text type="primary" @click="$emit('retry')">重试刷新</NButton>
  </Notice>
</template>
