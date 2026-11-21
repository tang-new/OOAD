<script setup lang="ts">
import { NTag } from 'naive-ui'
import { label, tone } from '@/views/workspace'
defineProps<{ value: string }>()
</script>
<template><NTag size="small" :type="tone(value)" :bordered="false" class="status-tag">{{ label(value) }}</NTag></template>
