import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'
export function errorText(error: unknown) { return error instanceof ApiError && error.code === 'ROLE_CYCLE' ? '角色继承形成循环，已拒绝保存（ROLE_CYCLE）' : (error as Error).message }
import type { Field } from '@/components/RecordForm.vue'
export function useWorkspace(reload: () => Promise<void>) {
  const auth = useAuthStore(), error = ref(''), loading = ref(false), message = ref('')
  const form = ref<{ title: string; fields: Field[]; initial?: Record<string, unknown>; submit: (data: Record<string, unknown>) => Promise<void> } | null>(null)
  async function load() { loading.value = true; error.value = ''; try { await reload() } catch (e) { error.value = errorText(e) } finally { loading.value = false } }
  async function mutate(action: () => Promise<unknown>) { await action(); await auth.refresh(); await reload(); message.value = '操作成功，菜单与有效权限已刷新' }
  async function run(action: () => Promise<unknown>) { error.value = ''; try { await mutate(action) } catch (e) { error.value = errorText(e) } }
  function allowed(operationId: string) { return auth.has(`rbac:ui:${operationId}`) }
  return { auth, error, loading, message, form, load, mutate, run, allowed }
}
export const text = (key: string, label: string, required = true): Field => ({ key, label, required })
export const choices = (key: string, label: string, values: string[]): Field => ({ key, label, required: true, type: 'select', options: values.map(value => ({ value, label: value })) })
export function flatten<T extends { children: T[] }>(rows: T[]): T[] { return rows.flatMap(row => [row, ...flatten(row.children ?? [])]) }
