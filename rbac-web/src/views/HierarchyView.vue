<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSelect, NTag } from 'naive-ui'
import { del, get, post } from '@/api'
import type { HierarchyVO } from '@/api/types'
import Notice from '@/components/Notice.vue'
import PageHeader from '@/components/PageHeader.vue'
import ReadState from '@/components/ReadState.vue'
import RoleDag from '@/components/RoleDag.vue'
import RecordForm from '@/components/RecordForm.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { edgeId, inheritancePaths } from './hierarchy'
import { positiveId, useRead, useWorkspace } from './workspace'

const route = useRoute(), router = useRouter()
const selection = computed(() => positiveId(route.query.roleId))
const graphState = useRead<HierarchyVO>(() => ({ roles: [], edges: [] })), graph = graphState.data
const dag = ref<InstanceType<typeof RoleDag> | null>(null)
const target = ref<number | null>(null), activePath = ref<number | null>(null)
const { auth, form, confirmation, error, message, stale, confirm, mutate, refresh, reset, allowed } = useWorkspace(load, () => auth.targetSystem)
function load() { return graphState.read(() => get<HierarchyVO>(`/systems/${encodeURIComponent(auth.targetSystem)}/role-hierarchy`)) }
const byId = computed(() => new Map(graph.value.roles.map(role => [role.id, role])))
const selected = computed(() => selection.value === null ? null : byId.value.get(selection.value))
const roleOptions = computed(() => graph.value.roles.map(role => ({ label: role.name + ' · ' + role.code, value: role.id })))
const related = computed(() => graph.value.edges.filter(edge => edge.seniorRoleId === selection.value || edge.juniorRoleId === selection.value))
const paths = computed(() => selection.value !== null && target.value !== null ? inheritancePaths(graph.value, selection.value, target.value) : null)
const highlighted = computed(() => paths.value
  ? activePath.value === null ? paths.value.everyEdge : paths.value.paths[activePath.value]?.edges ?? paths.value.everyEdge
  : new Set(related.value.map(edge => edgeId(edge.seniorRoleId, edge.juniorRoleId))))
const roleName = (id: number) => byId.value.get(id)?.name ?? '#' + id
function select(id: number | null, focus = false) {
  void router.replace({ path: '/hierarchy', query: { roleId: id, system: auth.targetSystem } })
  if (id !== null && focus) dag.value?.locate(id)
}
function add() {
  form.value = {
    title: '添加继承关系',
    description: '高级角色将继承被继承角色的权限。两个角色须属于当前目标系统。',
    initial: selection.value === null ? undefined : { seniorId: selection.value },
    fields: [
      { key: 'seniorId', label: '高级角色', type: 'select', options: roleOptions.value, required: true },
      { key: 'juniorId', label: '被继承角色', type: 'select', options: roleOptions.value, required: true },
    ],
    submit: data => mutate(() => post(`/roles/${data.seniorId}/inherits/${data.juniorId}`)),
  }
}
function remove(senior: number, junior: number) {
  confirm('移除继承关系', `将移除「${roleName(senior)} → ${roleName(junior)}」，可能回收通过这条路径继承的权限。`, () => del(`/roles/${senior}/inherits/${junior}`))
}
watch(() => auth.targetSystem, () => { reset(); graphState.reset(); target.value = null; activePath.value = null; void load() }, { immediate: true })
watch(selection, () => { target.value = null; activePath.value = null })
watch(target, () => { activePath.value = null })
</script>

<template>
  <PageHeader title="角色继承" description="查看角色之间的关系，追踪权限沿哪些路径继承。">
    <template #actions><NButton v-if="allowed('addRoleInheritance')" type="primary" :disabled="graph.roles.length < 2" @click="add">添加继承关系</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <ReadState v-if="graphState.loading.value || graphState.error.value" :loading="graphState.loading.value" :error="graphState.error.value" @retry="load" />
  <section class="surface graph-workspace">
    <div class="graph-toolbar">
      <div class="field"><label for="locate-role">定位角色</label><NSelect id="locate-role" :value="selection" :options="roleOptions" filterable clearable aria-label="定位角色" placeholder="搜索名称或角色码" @update:value="select($event, true)" /></div>
      <div class="graph-legend"><span><i></i> 高级角色 → 被继承角色</span><span><i class="legend-dashed"></i> 路径受停用影响</span><span v-if="selection"><i class="legend-active"></i> 当前高亮</span></div>
    </div>
    <div class="graph-body">
      <RoleDag v-if="graph.roles.length || (!graphState.loading.value && !graphState.error.value)" ref="dag" :graph="graph" :selected="selection" :target="target" :active-edges="highlighted" :filtered="Boolean(selection)" @select="select($event)" />
      <div v-else class="dag-canvas" aria-hidden="true"></div>
      <aside class="graph-details" aria-label="选中角色详情">
        <template v-if="selected">
          <div class="section-heading"><h2>{{ selected.name }}</h2><StatusTag :value="selected.status" /></div>
          <TechText :value="selected.code" />
          <p v-if="selected.description" class="muted graph-description">{{ selected.description }}</p>
          <RouterLink v-if="auth.canVisit('/roles')" :to="{ path: '/roles', query: { roleId: selected.id, system: selected.systemCode, tab: 'effective' } }">查看权限来源 ↗</RouterLink>
          <div class="graph-paths">
            <h3>继承路径</h3>
            <div class="field"><label for="trace-role">终点角色</label><NSelect id="trace-role" v-model:value="target" aria-label="终点角色" :options="roleOptions.filter(option => option.value !== selected?.id)" filterable clearable placeholder="选择被继承角色" /></div>
            <p v-if="!paths" class="help-text">以 {{ selected.name }} 为起点，选择终点查看路径。</p>
            <Notice v-else-if="!paths.paths.length" type="info">两个角色之间没有沿箭头方向的路径。</Notice>
            <template v-else>
              <label class="path-option"><input v-model="activePath" type="radio" :value="null" /> 全部路径</label>
              <label v-for="(path, index) in paths.paths" :key="path.nodes.join(':')" class="path-option">
                <input v-model="activePath" type="radio" :value="index" />
                <span>{{ path.nodes.map(roleName).join(' → ') }}<NTag size="small" :type="path.effective ? 'success' : 'warning'">{{ path.effective ? '路径启用' : '路径中断' }}</NTag></span>
              </label>
              <p v-if="paths.truncated" class="help-text">这里只列出前 {{ paths.paths.length }} 条，选择全部路径可查看完整连线。</p>
            </template>
          </div>
        </template>
        <template v-else><h2>角色详情</h2><p class="muted">从画布或搜索框选择角色，查看关系和路径。</p></template>
      </aside>
    </div>
  </section>
  <section v-if="graph.roles.length" class="surface padded">
    <details>
      <summary>关系列表（{{ graph.edges.length }} 条）</summary>
      <ReadState v-if="!graph.edges.length" empty description="当前系统暂无继承关系" />
      <ul v-else class="compact-list">
        <li v-for="edge in graph.edges" :key="edgeId(edge.seniorRoleId, edge.juniorRoleId)">
          <span><button class="text-button" @click="select(edge.seniorRoleId, true)">{{ roleName(edge.seniorRoleId) }}</button> → <button class="text-button" @click="select(edge.juniorRoleId, true)">{{ roleName(edge.juniorRoleId) }}</button><NTag v-if="byId.get(edge.seniorRoleId)?.status === 'DISABLED' || byId.get(edge.juniorRoleId)?.status === 'DISABLED'" size="small" type="warning">路径中断</NTag></span>
          <NButton v-if="allowed('deleteRoleInheritance')" size="small" @click="remove(edge.seniorRoleId, edge.juniorRoleId)">移除关系</NButton>
        </li>
      </ul>
    </details>
  </section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
