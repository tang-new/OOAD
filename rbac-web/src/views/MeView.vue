<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { post } from '@/api'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import DetailTabs from '@/components/DetailTabs.vue'
import PageHeader from '@/components/PageHeader.vue'
import RecordForm from '@/components/RecordForm.vue'
import type { Field } from '@/components/RecordForm.vue'

const auth = useAuthStore(), route = useRoute(), router = useRouter()
const panel = ref('profile')
const passwordOpen = computed(() => route.query.password === '1')
const fields: Field[] = [
  { key: 'oldPassword', label: '原密码', type: 'password', required: true, maxLength: 256 },
  { key: 'newPassword', label: '新密码', type: 'password', required: true, minLength: 12, maxLength: 256, hint: '至少 12 个字符，包含大小写字母、数字和特殊字符' },
]
async function changePassword(data: Record<string, unknown>) {
  await post('/auth/password', data)
  auth.clear()
  await router.replace({ path: '/login', query: { password: 'updated' } })
}
</script>

<template>
  <PageHeader title="本人资料">
    <template #actions><NButton @click="router.replace({ path: '/me', query: { password: '1' } })">修改密码</NButton></template>
  </PageHeader>
  <div class="surface padded">
    <DetailTabs :value="panel" :tabs="[{ key: 'profile', label: '基本资料' }, { key: 'effective', label: '有效权限' }]" label="本人资料" @change="panel = $event">
      <template #profile>
        <dl v-if="auth.profile" class="description-grid">
          <div><dt>姓名</dt><dd>{{ auth.profile.user.name }}</dd></div><div><dt>账号</dt><dd>{{ auth.profile.user.username }}</dd></div>
          <div><dt>所属部门</dt><dd>{{ auth.profile.user.departmentName }}</dd></div><div><dt>工号</dt><dd>{{ auth.profile.user.no || '—' }}</dd></div>
          <div><dt>统一身份标识</dt><dd><code>{{ auth.identity?.subject }}</code></dd></div><div><dt>身份角色</dt><dd>{{ auth.identity?.roles.join('、') || '无' }}</dd></div>
        </dl>
      </template>
      <template #effective><EffectivePermissions v-if="auth.profile?.effective" :value="auth.profile.effective" /></template>
    </DetailTabs>
  </div>
  <RecordForm v-if="passwordOpen" title="修改密码" :fields="fields" :submit="changePassword" @close="router.replace('/me')" />
</template>
