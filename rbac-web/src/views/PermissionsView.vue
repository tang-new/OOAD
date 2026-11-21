<script setup lang="ts">
import { ref, watch } from 'vue'
import { NButton, NTag } from 'naive-ui'
import { get, post, put } from '@/api'
import type { Page, PermissionVO } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import RecordForm from '@/components/RecordForm.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { choices, text, useRead, useWorkspace } from './workspace'

const list = useRead<Page<PermissionVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] })), rows = list.data
const page = ref(1), keyword = ref(''), searchValue = ref(''), kind = ref('')
const { auth, form, confirmation, error, message, stale, allowed, confirm, mutate, refresh, reset } = useWorkspace(load, () => auth.targetSystem)
function load() {
  return list.read(() => get<Page<PermissionVO>>('/permissions', {
    systemCode: auth.targetSystem, keyword: searchValue.value || undefined, kind: kind.value || undefined, page: page.value, pageSize: 20,
  }))
}
function changePage(next: number) { page.value = next; return load() }
function search() { searchValue.value = keyword.value.trim(); void changePage(1) }
function create() {
  const systemCode = auth.targetSystem
  form.value = {
    title: '创建权限', fields: [text('name', '权限名称'), text('code', '权限码'), choices('kind', '权限种类', ['UI', 'API'])],
    initial: kind.value ? { kind: kind.value } : undefined,
    submit: data => mutate(() => post('/permissions', { ...data, systemCode })),
  }
}
function changeState(permission: PermissionVO) {
  const status = permission.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  confirm(status === 'DISABLED' ? '停用权限' : '启用权限',
    status === 'DISABLED' ? `停用「${permission.name}」将从当前有效授权中移除此权限。` : `启用「${permission.name}」后，相关角色将重新计算有效授权。`,
    () => put(`/permissions/${permission.id}/status`, { status }))
}
watch(() => auth.targetSystem, () => { reset(); list.reset(); page.value = 1; void load() }, { immediate: true })
</script>
<template>
  <PageHeader title="权限目录" description="维护目标系统的 UI 权限与 API 权限。">
    <template #actions><NButton v-if="allowed('createPermission')" type="primary" @click="create">创建权限</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <section class="surface">
    <form class="toolbar" @submit.prevent="search">
      <div class="field wide"><label for="permission-keyword">权限检索</label><input id="permission-keyword" v-model="keyword" placeholder="名称或权限码" /></div>
      <div class="field"><label for="permission-kind">种类</label><select id="permission-kind" v-model="kind" @change="search"><option value="">全部</option><option>UI</option><option>API</option></select></div>
      <NButton type="primary" attr-type="submit">检索</NButton><NButton @click="keyword = ''; kind = ''; search()">重置</NButton>
    </form>
    <div class="table-scroll" tabindex="0" aria-label="权限列表"><table class="data-table"><thead><tr><th>名称</th><th>权限码</th><th>种类</th><th>状态</th><th v-if="allowed('setPermissionStatus')">操作</th></tr></thead>
      <tbody><tr v-for="permission in rows.items" :key="permission.id"><td><strong>{{ permission.name }}</strong></td><td><TechText :value="permission.code" /></td><td><NTag size="small" :type="permission.kind === 'UI' ? 'info' : 'default'">{{ permission.kind }}</NTag></td><td><StatusTag :value="permission.status" /></td><td v-if="allowed('setPermissionStatus')"><NButton text type="primary" @click="changeState(permission)">{{ permission.status === 'ENABLED' ? '停用' : '启用' }}</NButton></td></tr></tbody>
    </table></div>
    <ReadState :loading="list.loading.value" :error="list.error.value" :empty="!rows.items.length" description="未找到匹配的权限" @retry="load" />
    <Pager :page="page" :page-size="rows.pageSize" :total="rows.total" :loading="list.loading.value" @change="changePage" />
  </section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
