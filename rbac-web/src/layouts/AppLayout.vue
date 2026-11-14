<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { visibleMenus, pages } from '@/router/routes'
import { get } from '@/api'
import type { SystemVO } from '@/api/types'
const auth = useAuthStore(), router = useRouter()
const systems = ref<SystemVO[]>([]), error = ref('')
const menus = computed(() => visibleMenus(auth.menus, auth.has))
const title = (route: string | null, name: string) => pages.find(page => page.path === route)?.title ?? name
onMounted(async () => { try { systems.value = await get<SystemVO[]>('/systems') } catch (e) { error.value = (e as Error).message } })
async function logout() { try { await auth.logout() } finally { await router.push('/login') } }
</script>
<template>
  <div class="shell">
    <aside><h1>统一权限中心</h1><small>RBAC1 · 管理控制台</small>
      <nav aria-label="主导航"><RouterLink to="/me">本人资料</RouterLink><RouterLink v-for="menu in menus" :key="menu.id" :to="menu.route!">{{ title(menu.route, menu.name) }}</RouterLink></nav>
    </aside>
    <div class="workspace"><header><span>{{ auth.identity?.name }}</span><label>管理目标系统 <select v-model="auth.targetSystem" aria-label="管理目标系统"><option v-for="system in systems" :key="system.id" :value="system.code">{{ system.name }} · {{ system.status }}</option></select></label><button @click="auth.refresh().catch(e => error = e.message)">刷新权限</button><button @click="logout">退出登录</button></header>
      <p class="hint">管理令牌固定为 rbac-center；目标系统仅为管理过滤，不切换登录 audience。</p><p v-if="error" role="alert" class="error">{{ error }}</p>
      <main><RouterView :key="$route.path" /></main>
    </div>
  </div>
</template>
