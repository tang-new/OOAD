<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { get, post, patch, put, del } from '@/api'
import type { UiResourceVO, ApiOperationVO, Page, CoverageVO, MappingVO } from '@/api/types'
import RecordForm, { type Field } from '@/components/RecordForm.vue'
import { useWorkspace, text, choices, flatten } from './workspace'
const route = useRoute(), kind = computed(() => route.path)
const tree = ref<UiResourceVO[]>([]), apiRows = ref<Page<ApiOperationVO>>({ page: 1, pageSize: 20, total: 0, items: [] }), coverage = ref<CoverageVO>({ mappings: [], unmappedUi: [], unmappedApi: [] })
const mappings = ref<MappingVO[]>([]), uiId = ref(''), apiId = ref(''), keyword = ref(''), page = ref(1)
const { auth, form, error, message, loading, load, mutate, run, allowed } = useWorkspace(reload)
const uiRows = computed(() => flatten(tree.value)), base = computed(() => `/systems/${encodeURIComponent(auth.targetSystem)}`)
async function reload() {
  if (kind.value !== '/api-operations') tree.value = await get<UiResourceVO[]>(`${base.value}/ui-resources`)
  if (kind.value !== '/ui-resources') apiRows.value = await get<Page<ApiOperationVO>>(`${base.value}/api-operations`, { keyword: keyword.value || undefined, page: page.value, pageSize: 20 })
  if (kind.value === '/mappings') { coverage.value = await get<CoverageVO>(`${base.value}/ui-api-mappings`); mappings.value = coverage.value.mappings.map(m => ({ ...m })) }
}
function uiForm(row?: UiResourceVO) {
  const fields: Field[] = [{ key: 'parentId', label: '父菜单（空为根）', type: 'select', options: uiRows.value.filter(r => r.type === 'MENU' && r.id !== row?.id).map(r => ({ value: r.id, label: r.name })) }, choices('type', '资源类型', ['MENU', 'BUTTON']), text('code', '资源码'), text('name', '名称'), text('route', '前端路由', false), text('component', '组件标识', false), { key: 'sortOrder', label: '排序', type: 'number' }, text('permissionCode', 'UI 权限码'), choices('status', '状态', ['ENABLED', 'DISABLED'])]
  const initial: Record<string, unknown> = row ? Object.fromEntries(fields.map(f => [f.key, row[f.key as keyof UiResourceVO]])) : { type: 'MENU', status: 'ENABLED', sortOrder: 0 }
  form.value = { title: row ? '编辑菜单 / 按钮' : '创建菜单 / 按钮', fields, initial, submit: data => mutate(() => row ? patch(`/ui-resources/${row.id}`, data) : post(`${base.value}/ui-resources`, data)) }
}
function apiForm(row?: ApiOperationVO) {
  const fields: Field[] = [text('operationId', 'operationId'), choices('method', 'HTTP 方法', ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']), text('pathTemplate', '路径模板'), text('permissionCode', 'API 权限码（可空）', false), text('requiredRole', 'requiredRole（可空）', false), choices('accessPolicy', '访问策略', ['PUBLIC', 'AUTHENTICATED', 'PROTECTED']), { key: 'enabled', label: '启用', type: 'checkbox' }]
  const initial: Record<string, unknown> = row ? Object.fromEntries(fields.map(f => [f.key, row[f.key as keyof ApiOperationVO]])) : { method: 'GET', accessPolicy: 'PROTECTED', enabled: true }
  form.value = { title: row ? '编辑 API 操作' : '登记 API 操作', fields, initial, submit: data => mutate(() => row ? patch(`/api-operations/${row.id}`, data) : post(`${base.value}/api-operations`, data)) }
}
function addMapping() { const m = { uiResourceId: Number(uiId.value), apiOperationId: Number(apiId.value) }; if (!mappings.value.some(item => item.uiResourceId === m.uiResourceId && item.apiOperationId === m.apiOperationId)) mappings.value.push(m) }
watch(() => auth.targetSystem, () => { tree.value = []; apiRows.value.items = []; mappings.value = []; page.value = 1; void load() })
onMounted(load)
</script>
<template>
  <p v-if="error" role="alert" class="error">{{ error }}</p><p v-if="loading">加载中…</p><p role="status">{{ message }}</p>
  <template v-if="kind === '/ui-resources'"><p class="hint">父菜单可见不赋予子按钮；API 授权独立配置。</p><button :disabled="!allowed('createUiResource')" @click="uiForm()">创建菜单 / 按钮</button><div class="table-scroll"><table><thead><tr><th>类型</th><th>名称</th><th>父节点</th><th>路由 / 权限码</th><th>状态 / 排序</th><th>操作</th></tr></thead><tbody><tr v-for="row in uiRows" :key="row.id"><td>{{ row.type }}</td><td>{{ row.name }}</td><td>{{ uiRows.find(p => p.id === row.parentId)?.name ?? '根节点' }}</td><td>{{ row.route }}<br /><code>{{ row.permissionCode }}</code></td><td>{{ row.status }} / {{ row.sortOrder }}</td><td><button :disabled="!allowed('updateUiResource')" @click="uiForm(row)">编辑</button><button :disabled="!allowed('deleteUiResource')" @click="run(() => del(`/ui-resources/${row.id}`))">删除</button></td></tr></tbody></table></div><p v-if="!uiRows.length && !loading">暂无资源</p></template>
  <template v-else><form class="toolbar" @submit.prevent="page = 1; load()"><label>API 检索<input v-model="keyword" /></label><button>检索</button><button v-if="kind === '/api-operations'" type="button" :disabled="!allowed('createApiOperation')" @click="apiForm()">登记 API 操作</button></form><div class="table-scroll"><table><thead><tr><th>operationId</th><th>方法 / 路径</th><th>权限 / requiredRole</th><th>访问策略</th><th>启用</th><th v-if="kind === '/api-operations'">操作</th></tr></thead><tbody><tr v-for="row in apiRows.items" :key="row.id"><td>{{ row.operationId }}</td><td>{{ row.method }} {{ row.pathTemplate }}</td><td>{{ row.permissionCode ?? '无' }} / {{ row.requiredRole ?? '无' }}</td><td>{{ row.accessPolicy }}</td><td>{{ row.enabled ? '是' : '否' }}</td><td v-if="kind === '/api-operations'"><button :disabled="!allowed('updateApiOperation')" @click="apiForm(row)">编辑 API</button></td></tr></tbody></table></div><div class="pager"><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ apiRows.page }} 页 · {{ apiRows.total }} 条</span><button :disabled="page * 20 >= apiRows.total || loading" @click="page++; load()">下一页</button></div></template>
  <section v-if="kind === '/mappings'" class="panel"><h3>显式多对多映射</h3><p>映射仅作覆盖追踪，不隐式授予 API 权限。API 选择使用上方分页检索结果。</p><form class="toolbar" @submit.prevent="addMapping"><label>UI 资源<select v-model="uiId" required><option value="">请选择</option><option v-for="u in uiRows" :key="u.id" :value="u.id">{{ u.name }} · {{ u.code }}</option></select></label><label>API 操作<select v-model="apiId" required><option value="">请选择</option><option v-for="a in apiRows.items" :key="a.id" :value="a.id">{{ a.operationId }}</option></select></label><button :disabled="!allowed('replaceUiApiMappings')">加入待保存映射</button></form><ul><li v-for="(m, i) in mappings" :key="`${m.uiResourceId}-${m.apiOperationId}`">UI #{{ m.uiResourceId }} → API #{{ m.apiOperationId }} <button :disabled="!allowed('replaceUiApiMappings')" @click="mappings.splice(i, 1)">移除待保存映射</button></li></ul><button :disabled="!allowed('replaceUiApiMappings')" @click="run(() => put(`${base}/ui-api-mappings`, { mappings }))">保存全部映射</button><h3>覆盖报告</h3><h4>未映射 UI（{{ coverage.unmappedUi.length }}）</h4><ul><li v-for="u in coverage.unmappedUi" :key="u.id">{{ u.name }} · {{ u.code }}</li></ul><h4>未映射 API（{{ coverage.unmappedApi.length }}）</h4><ul><li v-for="a in coverage.unmappedApi" :key="a.id">{{ a.operationId }}</li></ul></section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
</template>
