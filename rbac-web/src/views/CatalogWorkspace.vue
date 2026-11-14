<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { get, post, patch, put, del } from '@/api'
import { toInstant } from '@/utils/format'
import type { DepartmentVO, SystemVO, RoleVO, PermissionVO, Page, AuditVO } from '@/api/types'
import RecordForm from '@/components/RecordForm.vue'
import { useWorkspace, text, choices, flatten } from './workspace'
const route = useRoute(), path = computed(() => route.path)
const systems = ref<SystemVO[]>([]), departments = ref<DepartmentVO[]>([]), departmentRows = computed(() => flatten(departments.value))
const permissionRows = ref<Page<PermissionVO>>({ page: 1, pageSize: 20, total: 0, items: [] }), audits = ref<Page<AuditVO>>({ page: 1, pageSize: 20, total: 0, items: [] }), audit = ref<AuditVO | null>(null)
const keyword = ref(''), kind = ref(''), page = ref(1), counts = ref({ users: 0, roles: 0, systems: 0 })
const actor = ref(''), actorId = ref(''), target = ref(''), from = ref(''), to = ref('')
const eligibleDept = ref<DepartmentVO | null>(null), eligibleIds = ref<number[]>([]), roles = ref<RoleVO[]>([])
const subjectId = ref(''), operationId = ref(''), permission = ref(''), checkKind = ref('API'), decision = ref<unknown>(null)
const { auth, form, error, message, loading, load, mutate, run, allowed } = useWorkspace(reload)
async function reload() {
  switch (path.value) {
    case '/dashboard': counts.value = await get('/dashboard'); break
    case '/systems': systems.value = await get('/systems'); break
    case '/departments': departments.value = await get('/departments/tree'); if (eligibleDept.value) await eligibility(eligibleDept.value); break
    case '/permissions': permissionRows.value = await get('/permissions', { systemCode: auth.targetSystem, keyword: keyword.value || undefined, kind: kind.value || undefined, page: page.value, pageSize: 20 }); break
    case '/audit': audits.value = await get('/audit-logs', { systemCode: auth.targetSystem, actor: actor.value || undefined, actorId: actorId.value || undefined, target: target.value || undefined, from: toInstant(from.value), to: toInstant(to.value), page: page.value, pageSize: 20 }); break
  }
}
function systemForm(row?: SystemVO) { form.value = { title: row ? '编辑系统' : '登记系统', initial: row ? { name: row.name } : undefined, fields: row ? [text('name', '名称')] : [text('code', '系统码'), text('name', '名称')], submit: data => mutate(() => row ? patch(`/systems/${row.id}`, data) : post('/systems', data)) } }
function systemState(row: SystemVO) { form.value = { title: '独立系统启停', initial: { status: row.status }, fields: [choices('status', '系统状态', ['ENABLED', 'DISABLED'])], submit: data => mutate(() => put(`/systems/${row.id}/status`, data)) } }
function departmentForm(row?: DepartmentVO) {
  form.value = { title: row ? '编辑部门' : '创建部门', initial: row ? { name: row.name } : undefined, fields: row ? [text('name', '名称')] : [text('code', '部门码'), text('name', '名称'), { key: 'parentId', label: '上级部门（可空）', type: 'select', options: departmentRows.value.map(d => ({ value: d.id, label: d.name })) }], submit: data => mutate(() => row ? patch(`/departments/${row.id}`, data) : post('/departments', data)) }
}
async function eligibility(dept: DepartmentVO) {
  eligibleDept.value = dept
  const [approved, hierarchy] = await Promise.all([get<RoleVO[]>(`/departments/${dept.id}/assignable-roles`, { systemCode: auth.targetSystem }), get<{ roles: RoleVO[] }>(`/systems/${encodeURIComponent(auth.targetSystem)}/role-hierarchy`)])
  roles.value = hierarchy.roles
  // PUT 是全系统原子替换。保留其它系统准入，不得在系统过滤后误删。
  const all = await get<RoleVO[]>(`/departments/${dept.id}/assignable-roles`)
  eligibleIds.value = [...new Set([...all.map(r => r.id), ...approved.map(r => r.id)])]
}
function permissionForm() { form.value = { title: '创建权限', fields: [text('code', '权限码'), text('name', '名称'), choices('kind', '权限种类', ['UI', 'API'])], submit: data => mutate(() => post('/permissions', { ...data, systemCode: auth.targetSystem })) } }
function permissionState(row: PermissionVO) { form.value = { title: '独立权限启停', initial: { status: row.status }, fields: [choices('status', '权限状态', ['ENABLED', 'DISABLED'])], submit: data => mutate(() => put(`/permissions/${row.id}/status`, data)) } }
async function check(explain: boolean) { error.value = ''; try { decision.value = await post(`/authorization/${explain ? 'explain' : 'check'}`, { systemCode: auth.targetSystem, ...(subjectId.value ? { userId: Number(subjectId.value) } : {}), kind: checkKind.value, ...(checkKind.value === 'API' ? { operationId: operationId.value } : { permission: permission.value }) }) } catch (e) { error.value = (e as Error).message } }
watch(() => auth.targetSystem, () => { page.value = 1; audit.value = null; decision.value = null; void load() })
onMounted(load)
</script>
<template>
  <p v-if="error" class="error" role="alert">{{ error }}</p><p v-if="loading">加载中…</p><p role="status">{{ message }}</p>
  <div v-if="path === '/dashboard'" class="counts"><article><h3>用户</h3><strong>{{ counts.users }}</strong></article><article><h3>角色</h3><strong>{{ counts.roles }}</strong></article><article><h3>系统</h3><strong>{{ counts.systems }}</strong></article></div>
  <template v-if="path === '/systems'"><button :disabled="!allowed('createSystem')" @click="systemForm()">登记系统</button><div class="table-scroll"><table><thead><tr><th>系统码</th><th>名称</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="s in systems" :key="s.id"><td>{{ s.code }}</td><td>{{ s.name }}</td><td>{{ s.status }}</td><td><button :disabled="!allowed('updateSystem')" @click="systemForm(s)">编辑</button><button :disabled="!allowed('setSystemStatus')" @click="systemState(s)">独立启停</button></td></tr></tbody></table></div></template>
  <template v-if="path === '/departments'"><p>部门是人员唯一归属与直接角色准入，不是权限数据范围。</p><button :disabled="!allowed('createDepartment')" @click="departmentForm()">创建部门</button><div class="table-scroll"><table><thead><tr><th>部门</th><th>路径 / 父部门</th><th>操作</th></tr></thead><tbody><tr v-for="d in departmentRows" :key="d.id"><td :style="{ paddingLeft: `${d.path.split('/').filter(Boolean).length * 14}px` }">{{ d.name }} · {{ d.code }}</td><td>{{ d.path }} / {{ departmentRows.find(p => p.id === d.parentId)?.name ?? '根' }}</td><td><button :disabled="!allowed('updateDepartment')" @click="departmentForm(d)">编辑</button><button :disabled="!allowed('deleteDepartment')" @click="run(() => del(`/departments/${d.id}`))">删除</button><button @click="eligibility(d).catch(e => error = e.message)">角色准入</button></td></tr></tbody></table></div><section v-if="eligibleDept" class="panel"><h3>{{ eligibleDept.name }} · {{ auth.targetSystem }} 角色准入</h3><p class="warning">取消准入将回收当前部门用户不合资格的角色指派；不会修改共享角色权限。</p><div class="checks"><label v-for="role in roles" :key="role.id"><input v-model="eligibleIds" type="checkbox" :value="role.id" />{{ role.name }} · {{ role.code }}</label></div><button :disabled="!allowed('replaceDepartmentAssignableRoles')" @click="run(() => put(`/departments/${eligibleDept!.id}/assignable-roles`, { roleIds: eligibleIds }))">保存准入（保留其它系统）</button></section></template>
  <template v-if="path === '/permissions'"><form class="toolbar" @submit.prevent="page = 1; load()"><label>权限检索<input v-model="keyword" /></label><label>种类<select v-model="kind"><option value="">全部</option><option>UI</option><option>API</option></select></label><button>检索</button><button type="button" :disabled="!allowed('createPermission')" @click="permissionForm">创建权限</button></form><div class="table-scroll"><table><thead><tr><th>权限码</th><th>名称</th><th>种类</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="p in permissionRows.items" :key="p.id"><td>{{ p.code }}</td><td>{{ p.name }}</td><td>{{ p.kind }}</td><td>{{ p.status }}</td><td><button :disabled="!allowed('setPermissionStatus')" @click="permissionState(p)">启停</button></td></tr></tbody></table></div><div class="pager"><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>{{ permissionRows.page }} 页 · {{ permissionRows.total }} 条</span><button :disabled="page * 20 >= permissionRows.total || loading" @click="page++; load()">下一页</button></div></template>
  <template v-if="path === '/audit'"><form class="toolbar" @submit.prevent="page = 1; load()"><label>操作者<input v-model="actor" /></label><label>操作者 ID<input v-model="actorId" type="number" min="1" /></label><label>目标<input v-model="target" /></label><label>从（本地时区）<input v-model="from" type="datetime-local" /></label><label>至（本地时区）<input v-model="to" type="datetime-local" /></label><button>查询审计</button></form><div class="table-scroll"><table><thead><tr><th>时间（UTC）</th><th>操作者</th><th>事件</th><th>目标</th><th>详情</th></tr></thead><tbody><tr v-for="a in audits.items" :key="a.id"><td>{{ a.at }}</td><td>{{ a.actor.name }}{{ a.actor.deleted ? '（已删除）' : '' }}</td><td>{{ a.type }}</td><td>{{ a.target }}</td><td><button @click="audit = a">详情</button></td></tr></tbody></table><p v-if="!loading && !audits.items.length">无匹配审计</p></div><div class="pager"><button :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>{{ audits.page }} 页 · {{ audits.total }} 条</span><button :disabled="page * 20 >= audits.total || loading" @click="page++; load()">下一页</button></div><section v-if="audit" class="panel"><h3>审计 #{{ audit.id }} · {{ audit.target }}</h3><p>操作者角色：{{ audit.actor.roles.join('、') }}</p><h4>变更前</h4><pre>{{ JSON.stringify(audit.before, null, 2) }}</pre><h4>变更后</h4><pre>{{ JSON.stringify(audit.after, null, 2) }}</pre></section></template>
  <template v-if="path === '/authorization'"><form @submit.prevent="check(false)"><label>用户 ID（留空为本人）<input v-model="subjectId" type="number" min="1" /></label><label>鉴权种类<select v-model="checkKind"><option>API</option><option>UI</option></select></label><label v-if="checkKind === 'API'">operationId<input v-model="operationId" required /></label><label v-else>UI 权限码<input v-model="permission" required /></label><button :disabled="!allowed('checkAuthorization')">检查</button><button type="button" :disabled="!allowed('explainAuthorization')" @click="check(true)">解释来源</button></form><p class="hint">allowed:false 是正常拒绝，不是服务故障。</p><pre v-if="decision">{{ JSON.stringify(decision, null, 2) }}</pre></template>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
</template>
