<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { post } from '@/api'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
const auth = useAuthStore(), oldPassword = ref(''), newPassword = ref(''), message = ref('')
async function changePassword() { try { await post('/auth/password', { oldPassword: oldPassword.value, newPassword: newPassword.value }); auth.clear(); message.value = '密码已更改，请重新登录' } catch (e) { message.value = (e as Error).message } finally { oldPassword.value = ''; newPassword.value = '' } }
</script>
<template><h2>本人资料</h2><dl v-if="auth.profile"><dt>账号 / 姓名</dt><dd>{{ auth.profile.user.username }} / {{ auth.profile.user.name }}</dd><dt>唯一所属部门</dt><dd>{{ auth.profile.user.departmentName }}</dd><dt>统一身份</dt><dd>{{ auth.identity?.subject }}</dd><dt>身份角色</dt><dd>{{ auth.identity?.roles.join('、') || '无' }}</dd></dl><EffectivePermissions v-if="auth.profile" :value="auth.profile.effective" /><details><summary>修改密码</summary><form @submit.prevent="changePassword"><label>原密码<input v-model="oldPassword" type="password" autocomplete="current-password" required /></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" minlength="12" required /></label><button>更新密码</button></form></details><p role="status">{{ message }}</p></template>
