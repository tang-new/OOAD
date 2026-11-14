<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { get, post, patch, put, del } from '@/api'
import type { Page, RoleVO, PermissionVO, EffectiveVO, HierarchyVO } from '@/api/types'
import RecordForm from '@/components/RecordForm.vue'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import RoleDag from '@/components/RoleDag.vue'
import { useWorkspace, text, choices } from './workspace'
const rows = ref<Page<RoleVO>>({ page: 1, pageSize: 20, total: 0, items: [] }), page = ref(1), keyword = ref(''), status = ref('')
const graph = ref<HierarchyVO>({ roles: [], edges: [] }), seniorId = ref(''), juniorId = ref('')
const selected = ref<RoleVO | null>(null), directIds = ref<number[]>([]), permissions = ref<PermissionVO[]>([]), permissionPage = ref(1), permissionTotal = ref(0), permissionKeyword = ref(''), effective = ref<EffectiveVO | null>(null)
const { auth, form, error, message, loading, load, mutate, run, allowed } = useWorkspace(reload)
async function reload() {
  const [list, dag] = await Promise.all([get<Page<RoleVO>>('/roles', { systemCode: auth.targetSystem, page: page.value, pageSize: 20, keyword: keyword.value || undefined, status: status.value || undefined }), get<HierarchyVO>(`/systems/${encodeURIComponent(auth.targetSystem)}/role-hierarchy`)])
  rows.value = list; graph.value = dag
  if (selected.value) await detail(selected.value)
}
async function loadPermissions() {
  const result = await get<Page<PermissionVO>>('/permissions', { systemCode: auth.targetSystem, page: permissionPage.value, pageSize: 20, keyword: permissionKeyword.value || undefined })
  permissions.value = result.items; permissionTotal.value = result.total
}
async function detail(role: RoleVO) {
  selected.value = role
  const [direct, result] = await Promise.all([get<PermissionVO[]>(`/roles/${role.id}/permissions`), get<EffectiveVO>(`/roles/${role.id}/effective-permissions`), loadPermissions()])
  directIds.value = direct.map(p => p.id); effective.value = result
}
function create() { form.value = { title: '创建角色', fields: [text('code', '角色码'), text('name', '名称'), text('description', '说明', false), { ...choices('identityRole', '身份投影（可空）', ['student', 'teacher', 'admin']), required: false }], submit: data => mutate(() => post('/roles', { ...data, systemCode: auth.targetSystem })) } }
function edit(role: RoleVO) { form.value = { title: '编辑角色（不修改状态）', initial: { name: role.name, description: role.description }, fields: [text('name', '名称'), text('description', '说明', false)], submit: data => mutate(() => patch(`/roles/${role.id}`, { ...data, version: role.version })) } }
function state(role: RoleVO) { form.value = { title: '独立角色启停', initial: { status: role.status }, fields: [choices('status', '状态', ['ENABLED', 'DISABLED'])], submit: data => mutate(() => put(`/roles/${role.id}/status`, data)) } }
function remove(role: RoleVO) { if (confirm(`确认退役 ${role.name}？将清理指派、继承、准入和权限，历史审计保留。`)) void run(() => del(`/roles/${role.id}`)) }
function search() { page.value = 1; void load() }
watch(() => auth.targetSystem, () => { selected.value = null; effective.value = null; graph.value = { roles: [], edges: [] }; seniorId.value = ''; juniorId.value = ''; page.value = 1; void load() })
onMounted(load)
</script>
<template>
  <form class="toolbar" @submit.prevent="search"><label>角色检索<input v-model="keyword" /></label><label>角色状态<select v-model="status"><option value="">全部</option><option>ENABLED</option><option>DISABLED</option></select></label><button>检索</button><button type="button" :disabled="!allowed('createRole')" @click="create">创建角色</button></form>
  <p v-if="loading">加载中…</p><p v-if="error" class="error" role="alert">{{ error }}</p><p role="status">{{ message }}</p>
  <div class="table-scroll"><table><thead><tr><th>角色码</th><th>名称</th><th>状态</th><th>身份投影</th><th>操作</th></tr></thead><tbody><tr v-for="role in rows.items" :key="role.id"><td>{{ role.code }}</td><td>{{ role.name }}</td><td>{{ role.status }}</td><td>{{ role.identityRole ?? '无' }}</td><td class="actions"><button @click="detail(role).catch(e => error = e.message)">直接 / 有效权限</button><button :disabled="!allowed('updateRole')" @click="edit(role)">编辑</button><button :disabled="!allowed('setRoleStatus')" @click="state(role)">独立启停</button><button :disabled="!allowed('deleteRole')" @click="remove(role)">删除</button></td></tr></tbody></table><p v-if="!loading && !rows.items.length">暂无角色</p></div>
  <div class="pager"><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ rows.page }} 页 · 共 {{ rows.total }} 条</span><button :disabled="page * 20 >= rows.total || loading" @click="page++; load()">下一页</button></div>
  <section class="panel"><h3>角色继承 DAG</h3><p>高级角色 → 被继承角色；允许多父与多路径。环由后端原子拒绝（ROLE_CYCLE）。</p><RoleDag :graph="graph" /><form class="toolbar" @submit.prevent="run(() => post(`/roles/${seniorId}/inherits/${juniorId}`))"><label>高级角色<select v-model="seniorId" required><option value="">请选择</option><option v-for="r in graph.roles" :key="r.id" :value="r.id">{{ r.name }} · {{ r.code }}</option></select></label><label>被继承角色<select v-model="juniorId" required><option value="">请选择</option><option v-for="r in graph.roles" :key="r.id" :value="r.id">{{ r.name }} · {{ r.code }}</option></select></label><button :disabled="!allowed('addRoleInheritance')">添加继承边</button></form><ul><li v-for="edge in graph.edges" :key="`${edge.seniorRoleId}-${edge.juniorRoleId}`">{{ graph.roles.find(r => r.id === edge.seniorRoleId)?.name }} → {{ graph.roles.find(r => r.id === edge.juniorRoleId)?.name }} <button :disabled="!allowed('deleteRoleInheritance')" @click="run(() => del(`/roles/${edge.seniorRoleId}/inherits/${edge.juniorRoleId}`))">删除继承边</button></li></ul></section>
  <section v-if="selected" class="panel"><h3>{{ selected.name }} · 直接权限</h3><form class="toolbar" @submit.prevent="permissionPage = 1; loadPermissions().catch(e => error = e.message)"><label>检索权限<input v-model="permissionKeyword" /></label><button>检索权限</button></form><div class="checks"><label v-for="p in permissions" :key="p.id"><input v-model="directIds" type="checkbox" :value="p.id" />{{ p.kind }} · {{ p.code }} · {{ p.name }}</label></div><div class="pager"><button :disabled="permissionPage <= 1" @click="permissionPage--; loadPermissions()">前页权限</button><span>{{ permissionPage }} / {{ Math.max(1, Math.ceil(permissionTotal / 20)) }}</span><button :disabled="permissionPage * 20 >= permissionTotal" @click="permissionPage++; loadPermissions()">后页权限</button></div><p class="hint">跨页保留已选直接权限；继承权限不能在此误作直接授权删除。</p><button :disabled="!allowed('replaceRolePermissions')" @click="run(() => put(`/roles/${selected!.id}/permissions`, { permissionIds: directIds }))">保存直接权限</button><EffectivePermissions v-if="effective" :value="effective" /></section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
</template>
