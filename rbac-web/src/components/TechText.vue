<script setup lang="ts">
import { ref } from 'vue'
const props = defineProps<{ value: string }>()
const feedback = ref('')
async function copy() {
  try {
    await navigator.clipboard.writeText(props.value)
    feedback.value = '已复制'
  } catch {
    feedback.value = '复制失败，请选中文字复制'
  }
}
</script>
<template>
  <span class="tech-value">
    <code :title="value">{{ value }}</code>
    <button class="copy-button" type="button" :aria-label="'复制 ' + value" title="复制" @click="copy"><span aria-hidden="true">⧉</span></button>
    <span v-if="feedback" class="copy-feedback" role="status">{{ feedback }}</span>
  </span>
</template>
