<script setup lang="ts">
import { NPagination } from 'naive-ui'
defineProps<{ page: number; pageSize: number; total: number; loading?: boolean }>()
defineEmits<{ change: [number] }>()
</script>
<template>
  <div class="pager" aria-label="结果分页">
    <span>共 {{ total.toLocaleString('zh-CN') }} 条</span>
    <NPagination :page="page" :page-size="pageSize" :item-count="total" :page-slot="5" :disabled="loading" @update:page="$emit('change', $event)" />
  </div>
</template>
