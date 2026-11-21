<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { signedInDestination } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { errorText } from './workspace'

const auth = useAuthStore(), router = useRouter(), route = useRoute()
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false)
const changed = route.query.password === 'updated'
async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    await router.replace(signedInDestination(router, route.query.redirect, auth))
  } catch (cause) { error.value = errorText(cause) }
  finally { password.value = ''; busy.value = false }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-brand" aria-label="统一权限中心">
      <div class="login-wordmark"><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span> 统一权限中心</div>
      <div class="login-art" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="orbit orbit-three"></div><span></span><b></b><i></i></div>
      <span class="login-model">RBAC1</span>
    </section>
    <section class="login-content">
      <form class="login-form" @submit.prevent="login">
        <h1>登录控制台</h1>
        <p>使用统一身份账号登录权限管理控制台。</p>
        <p v-if="changed" class="login-success" role="status">密码已修改，请使用新密码登录。</p>
        <label for="username">账号</label>
        <input id="username" v-model="username" autocomplete="username" maxlength="128" placeholder="输入登录账号" required />
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" maxlength="256" placeholder="输入密码" required />
        <p v-if="error" class="field-error" role="alert">{{ error }}</p>
        <NButton type="primary" size="large" attr-type="submit" block :loading="busy">{{ busy ? '登录中…' : '登录' }}</NButton>
      </form>
    </section>
  </main>
</template>
