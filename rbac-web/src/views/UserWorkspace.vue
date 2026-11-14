<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { get, post, patch, put, del } from '@/api'
import type { Page, UserVO, DepartmentVO, RoleVO, AssignmentVO, EffectiveVO } from '@/api/types'
import RecordForm, { type Field } from '@/components/RecordForm.vue'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import { useWorkspace, text, choices, flatten } from './workspace'
const rows = ref<Page<UserVO>>({ page: 1, pageSize: 20, total: 0, items: [] }), keyword = ref(''), departmentId = ref(''), status = ref(''), page = ref(1)
const departments = ref<DepartmentVO[]>([]), selected = ref<UserVO | null>(null), assignments = ref<AssignmentVO[]>([]), eligible = ref<RoleVO[]>([]), roleId = ref(''), effective = ref<EffectiveVO | null>(null)
const { auth, error, loading, message, form, load, mutate, run, allowed } = useWorkspace(reload)
const deptOptions = computed(() => flatten(departments.value).map(d => ({ value: d.id, label: d.name })))
const departmentField = (): Field => ({ key: 'departmentId', label: '所属部门', type: 'select', required: true, options: deptOptions.value })
async function reload() {
  const result = await get<Page<UserVO>>('/users', { keyword: keyword.value || undefined, departmentId: departmentId.value || undefined, status: status.value || undefined, page: page.value, pageSize: 20 })
  rows.value = result
  if (selected.value) await selectUser(await get<UserVO>(`/users/${selected.value.id}`))
}
async function selectUser(user: UserVO) {
  selected.value = user; roleId.value = ''
  const code = encodeURIComponent(auth.targetSystem)
  const [links, result, options] = await Promise.all([get<AssignmentVO[]>(`/users/${user.id}/role-assignments`, { systemCode: auth.targetSystem }), get<EffectiveVO>(`/users/${user.id}/systems/${code}/permissions`), get<RoleVO[]>(`/departments/${user.departmentId}/assignable-roles`, { systemCode: auth.targetSystem })])
  assignments.value = links; effective.value = result; eligible.value = options.filter(role => role.status === 'ENABLED')
}
function create() { form.value = { title: '创建用户', fields: [text('username', '账号'), text('name', '姓名'), text('no', '学工号', false), text('phone', '电话', false), departmentField(), { key: 'password', label: '初始密码', type: 'password', required: true }], submit: data => mutate(() => post('/users', data)) } }
function edit(user: UserVO) { form.value = { title: '编辑用户资料（不更改状态和部门）', initial: { name: user.name, no: user.no }, fields: [text('name', '姓名'), text('no', '学工号', false), text('phone', '新电话（留空不更改）', false)], submit: data => mutate(() => patch(`/users/${user.id}`, { ...data, version: user.version })) } }
function changeStatus(user: UserVO) { form.value = { title: '独立用户状态操作', initial: { status: user.status }, fields: [choices('status', '状态', ['ACTIVE', 'DISABLED', 'DELETED'])], submit: data => mutate(() => put(`/users/${user.id}/status`, data)) } }
function move(user: UserVO) { form.value = { title: '迁移部门：不准入的角色指派将被回收，共享角色权限不变', initial: { departmentId: user.departmentId }, fields: [departmentField()], submit: data => mutate(() => put(`/users/${user.id}/department`, { ...data, version: user.version })) } }
function remove(user: UserVO) { if (confirm(`确认逻辑删除用户 ${user.name} 并清理角色指派？`)) void run(() => del(`/users/${user.id}`)) }
function search() { page.value = 1; void load() }
onMounted(async () => { await load(); try { departments.value = await get<DepartmentVO[]>('/departments/tree') } catch (e) { error.value = (e as Error).message } })
watch(() => auth.targetSystem, () => { effective.value = null; assignments.value = []; eligible.value = []; void load() })
</script>
<template>
  <form class="toolbar" @submit.prevent="search"><label>姓名 / 账号<input v-model="keyword" /></label><label>部门<select v-model="departmentId"><option value="">全部</option><option v-for="d in deptOptions" :key="d.value" :value="d.value">{{ d.label }}</option></select></label><label>用户状态<select v-model="status"><option value="">全部</option><option>ACTIVE</option><option>DISABLED</option><option>DELETED</option></select></label><button>检索</button><button type="button" :disabled="!allowed('createUser') || !deptOptions.length" @click="create">创建用户</button></form>
  <p v-if="loading" role="status">加载中…</p><p v-if="error" role="alert" class="error">{{ error }}</p><p role="status">{{ message }}</p>
  <div class="table-scroll"><table><thead><tr><th>姓名</th><th>账号</th><th>部门</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="user in rows.items" :key="user.id"><td>{{ user.name }}</td><td>{{ user.username }}</td><td>{{ user.departmentName }}</td><td>{{ user.status }}</td><td class="actions"><button @click="selectUser(user).catch(e => error = e.message)">角色 / 有效权限</button><button :disabled="!allowed('updateUser')" @click="edit(user)">编辑</button><button :disabled="!allowed('setUserStatus')" @click="changeStatus(user)">状态</button><button :disabled="!allowed('changeUserDepartment') || !deptOptions.length" @click="move(user)">迁移部门</button><button :disabled="!allowed('deleteUser')" @click="remove(user)">删除</button></td></tr></tbody></table><p v-if="!loading && !rows.items.length">暂无用户</p></div>
  <div class="pager"><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ rows.page }} 页 · 共 {{ rows.total }} 条</span><button :disabled="page * rows.pageSize >= rows.total || loading" @click="page++; load()">下一页</button></div>
  <section v-if="selected" class="panel"><h3>{{ selected.name }} · {{ auth.targetSystem }} 的角色指派</h3><p class="hint">仅列出用户当前部门后端准入清单；请求不携带伪造部门或数据范围。</p><label>可指派角色<select v-model="roleId"><option value="">请选择</option><option v-for="role in eligible" :key="role.id" :value="role.id">{{ role.name }} ({{ role.code }})</option></select></label><button :disabled="!roleId || !allowed('createUserRoleAssignment')" @click="run(() => post(`/users/${selected!.id}/role-assignments`, { roleId: Number(roleId) }))">指派角色</button><ul><li v-for="link in assignments" :key="link.id">{{ link.roleName }} <button :disabled="!allowed('deleteUserRoleAssignment')" @click="run(() => del(`/users/${selected!.id}/role-assignments/${link.id}`))">回收</button></li></ul><EffectivePermissions v-if="effective" :value="effective" /></section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
</template>
