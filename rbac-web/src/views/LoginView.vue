<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const auth = useAuthStore(), router = useRouter()
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false)
async function login() {
  busy.value = true; error.value = ''
  try { await auth.login(username.value, password.value); await router.push('/me') } catch (e) { error.value = (e as Error).message } finally { password.value = ''; busy.value = false }
}
</script>
<template><main class="login"><form @submit.prevent="login"><h1>统一权限中心</h1><p>使用统一身份登录权限管理控制台</p><label>账号<input v-model="username" autocomplete="username" maxlength="128" required /></label><label>密码<input v-model="password" type="password" autocomplete="current-password" maxlength="256" required /></label><p v-if="error" role="alert" class="error">{{ error }}</p><button type="submit" :disabled="busy">{{ busy ? '登录中…' : '登录' }}</button></form></main></template>
