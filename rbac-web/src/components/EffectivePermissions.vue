<script setup lang="ts">
import type { EffectiveVO } from '@/api/types'
import PermissionCode from './PermissionCode.vue'
defineProps<{ value: EffectiveVO }>()
</script>
<template><section><h3>直接角色</h3><p>{{ value.directRoles.map(r => r.name).join('、') || '无' }}</p><h3>有效角色（含继承）</h3><p>{{ value.effectiveRoles.map(r => r.name).join('、') || '无' }}</p><h3>有效权限</h3><ul><li v-for="p in value.permissions" :key="p.id"><PermissionCode :code="p.code" /> · {{ p.kind }} · {{ p.name }}</li></ul><h3>权限来源</h3><div class="table-scroll"><table><thead><tr><th>权限码</th><th>角色</th><th>来源</th><th>继承路径</th></tr></thead><tbody><tr v-for="(s, i) in value.sources" :key="i"><td>{{ s.permissionCode }}</td><td>{{ s.roleCode }}</td><td>{{ s.inherited ? '继承' : '直接' }}</td><td>{{ s.path.join(' → ') }}</td></tr></tbody></table></div></section></template>
