<script setup lang="ts">
import { computed, h } from 'vue'
import { NButton, NDataTable, type DataTableColumns } from 'naive-ui'
import { get, patch, post, put } from '@/api'
import type { SystemVO } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import ReadState from '@/components/ReadState.vue'
import RecordForm from '@/components/RecordForm.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { text, useRead, useWorkspace } from './workspace'

const systems = useRead<SystemVO[]>(() => [])
const { auth, form, confirmation, error, message, stale, allowed, confirm, mutate, refresh } = useWorkspace(load)
function load() { return systems.read(() => get<SystemVO[]>('/systems')) }
function edit(row?: SystemVO) {
  form.value = {
    title: row ? '编辑系统名称' : '登记系统',
    initial: row ? { name: row.name } : undefined,
    fields: row ? [text('name', '系统名称')] : [text('code', '系统码'), text('name', '系统名称')],
    submit: data => mutate(() => row ? patch(`/systems/${row.id}`, data) : post('/systems', data)),
  }
}
function changeState(row: SystemVO) {
  const status = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  confirm(status === 'DISABLED' ? '停用系统' : '启用系统',
    status === 'DISABLED' ? `停用「${row.name}」将暂停该系统的登录和有效授权。` : `启用「${row.name}」将重新开放该系统的登录和鉴权。`,
    () => put(`/systems/${row.id}/status`, { status }))
}
const columns = computed<DataTableColumns<SystemVO>>(() => [
  { title: '名称', key: 'name', minWidth: 180, render: row => h('strong', row.name) },
  { title: '系统码', key: 'code', minWidth: 210, render: row => h(TechText, { value: row.code }) },
  { title: '状态', key: 'status', width: 96, render: row => h(StatusTag, { value: row.status }) },
  { title: '目标系统', key: 'current', width: 108, render: row => auth.targetSystem === row.code ? '当前目标' : '—' },
  { title: '操作', key: 'actions', width: 170, render: row => h('div', { class: 'row-actions' }, [
    allowed('updateSystem') ? h(NButton, { size: 'small', text: true, type: 'primary', onClick: () => edit(row) }, { default: () => '编辑' }) : null,
    allowed('setSystemStatus') ? h(NButton, { size: 'small', onClick: () => changeState(row) }, { default: () => row.status === 'ENABLED' ? '停用' : '启用' }) : null,
  ]) },
])
void load()
</script>
<template>
  <PageHeader title="系统登记" description="查看已登记的系统，并管理其启用状态。">
    <template #actions><NButton v-if="allowed('createSystem')" type="primary" @click="edit()">登记系统</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <section class="surface">
    <ReadState v-if="systems.loading.value || systems.error.value" :loading="systems.loading.value" :error="systems.error.value" @retry="load" />
    <NDataTable :columns="columns" :data="systems.data.value" :row-key="row => row.id" :bordered="false" :scroll-x="760" />
  </section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
