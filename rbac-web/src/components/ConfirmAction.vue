<script setup lang="ts">
import { ref } from 'vue'
import { NModal, NButton } from 'naive-ui'
import { errorText } from '@/views/workspace'
const props = defineProps<{ title: string; description: string; submit: () => Promise<void> }>()
const emit = defineEmits<{ close: [] }>()
const busy = ref(false), error = ref('')
async function save() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try { await props.submit(); emit('close') }
  catch (cause) { error.value = errorText(cause) }
  finally { busy.value = false }
}
</script>
<template>
  <NModal :show="true" preset="card" :title="title" class="editor-modal" :mask-closable="false" :close-on-esc="!busy" :closable="!busy" @update:show="value => { if (!value && !busy) $emit('close') }">
    <p>{{ description }}</p>
    <p v-if="error" class="field-error" role="alert">{{ error }}</p>
    <template #footer>
      <div class="dialog-actions">
        <NButton :disabled="busy" @click="$emit('close')">取消</NButton>
        <NButton type="error" :loading="busy" @click="save">确认</NButton>
      </div>
    </template>
  </NModal>
</template>
