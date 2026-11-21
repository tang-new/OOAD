<script setup lang="ts">
import { reactive, ref } from 'vue'
import { NButton, NModal, NSelect, NTreeSelect, type TreeSelectOption } from 'naive-ui'
import { errorText } from '@/views/workspace'

export interface Field {
  key: string
  label: string
  type?: 'text' | 'password' | 'number' | 'select' | 'tree' | 'checkbox'
  required?: boolean
  options?: { value: string | number; label: string }[]
  tree?: TreeSelectOption[]
  maxLength?: number
  minLength?: number
  hint?: string
  validate?: (value: unknown) => string | undefined
}
export interface RecordEditor {
  title: string
  description?: string
  fields: Field[]
  initial?: Record<string, unknown>
  submit: (data: Record<string, unknown>) => Promise<void>
}

const props = defineProps<RecordEditor>()
const emit = defineEmits<{ close: [] }>()
const model = reactive({ ...props.initial })
const issues = ref<Record<string, string>>({})
const busy = ref(false), error = ref('')
function selection(field: Field) {
  const value = model[field.key]
  return typeof value === 'number' || typeof value === 'string' ? value : null
}
function rootKeys(field: Field) {
  return field.tree?.flatMap(option => option.key === undefined ? [] : [option.key])
}
function update(field: Field, value: unknown) {
  model[field.key] = value
  delete issues.value[field.key]
}
async function save() {
  if (busy.value) return
  error.value = ''
  const data: Record<string, unknown> = {}
  const validation: Record<string, string> = {}
  for (const field of props.fields) {
    const value = model[field.key]
    const empty = value === undefined || value === null || value === '' || (typeof value === 'string' && field.type !== 'password' && !value.trim())
    const issue = field.required && empty ? '请填写或选择此项'
      : !empty && field.minLength && String(value).length < field.minLength ? `至少输入 ${field.minLength} 个字符`
      : !empty && field.type === 'number' && !Number.isFinite(Number(value)) ? '请输入数字'
      : field.validate?.(value)
    if (issue) validation[field.key] = issue
    if (!empty) data[field.key] = field.type === 'number' ? Number(value) : value
    else if (props.initial && field.key in props.initial) data[field.key] = null
  }
  issues.value = validation
  if (Object.keys(validation).length) return
  busy.value = true
  try {
    await props.submit(data)
    emit('close')
  } catch (cause) {
    error.value = errorText(cause)
  } finally {
    busy.value = false
    for (const field of props.fields) if (field.type === 'password') model[field.key] = ''
  }
}
</script>

<template>
  <NModal :show="true" preset="card" :title="title" class="editor-modal" :mask-closable="false" :close-on-esc="!busy" :closable="!busy" @update:show="value => { if (!value && !busy) $emit('close') }">
    <form class="record-form" novalidate @submit.prevent="save">
      <p v-if="description" class="muted">{{ description }}</p>
      <div v-for="field in fields" :key="field.key" class="field">
        <label :for="'editor-' + field.key">{{ field.label }}<span v-if="field.required" aria-hidden="true" class="required"> *</span></label>
        <NTreeSelect v-if="field.type === 'tree'" :id="'editor-' + field.key" :value="selection(field)" :options="field.tree" :default-expanded-keys="rootKeys(field)" :aria-label="field.label" :status="issues[field.key] ? 'error' : undefined" filterable clearable placeholder="选择部门" @update:value="update(field, $event)" />
        <NSelect v-else-if="field.type === 'select'" :id="'editor-' + field.key" :value="selection(field)" :options="field.options" :aria-label="field.label" :status="issues[field.key] ? 'error' : undefined" filterable :clearable="!field.required" placeholder="请选择" @update:value="update(field, $event)" />
        <input v-else-if="field.type === 'checkbox'" :id="'editor-' + field.key" :aria-label="field.label" :checked="Boolean(model[field.key])" type="checkbox" @change="update(field, ($event.target as HTMLInputElement).checked)" />
        <input v-else :id="'editor-' + field.key" :aria-label="field.label" :aria-invalid="Boolean(issues[field.key])" :aria-describedby="issues[field.key] ? 'issue-' + field.key : undefined" :value="model[field.key]" :type="field.type ?? 'text'" :maxlength="field.maxLength" :required="field.required" :autocomplete="field.type === 'password' ? 'new-password' : 'off'" @input="update(field, ($event.target as HTMLInputElement).value)" />
        <small v-if="issues[field.key]" :id="'issue-' + field.key" class="field-error" role="alert">{{ issues[field.key] }}</small>
        <small v-else-if="field.hint" class="muted">{{ field.hint }}</small>
      </div>
      <p v-if="error" class="field-error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <NButton :disabled="busy" @click="$emit('close')">取消</NButton>
        <NButton type="primary" attr-type="submit" :loading="busy">保存</NButton>
      </div>
    </form>
  </NModal>
</template>
