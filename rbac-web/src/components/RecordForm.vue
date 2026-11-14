<script setup lang="ts">
import { reactive, ref } from 'vue'
export interface Field { key: string; label: string; type?: 'text' | 'password' | 'number' | 'select' | 'checkbox'; required?: boolean; options?: { value: string | number; label: string }[] }
const props = defineProps<{ title: string; fields: Field[]; initial?: Record<string, unknown>; submit: (data: Record<string, unknown>) => Promise<void> }>()
const emit = defineEmits<{ close: [] }>()
const model = reactive<Record<string, unknown>>({ ...props.initial }), busy = ref(false), error = ref('')
async function save() {
  error.value = ''; busy.value = true
  const data: Record<string, unknown> = {}
  for (const f of props.fields) {
    const value = model[f.key]
    if (value !== undefined && value !== '') data[f.key] = f.type === 'number' ? Number(value) : value
    else if (props.initial && f.key in props.initial) data[f.key] = null
  }
  try { await props.submit(data); emit('close') } catch (e) { error.value = (e as Error).message } finally { busy.value = false; for (const f of props.fields) if (f.type === 'password') model[f.key] = '' }
}
</script>
<template><div class="modal" role="dialog" aria-modal="true" :aria-label="title"><form @submit.prevent="save"><h2>{{ title }}</h2><label v-for="field in fields" :key="field.key">{{ field.label }}<select v-if="field.type === 'select'" v-model="model[field.key]" :required="field.required"><option value="">请选择</option><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select><input v-else-if="field.type === 'checkbox'" v-model="model[field.key]" type="checkbox" /><input v-else v-model="model[field.key]" :type="field.type ?? 'text'" :required="field.required" :autocomplete="field.type === 'password' ? 'new-password' : 'off'" /></label><p v-if="error" role="alert" class="error">{{ error }}</p><footer><button type="button" @click="emit('close')" :disabled="busy">取消</button><button type="submit" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button></footer></form></div></template>
