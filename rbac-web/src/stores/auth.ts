import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import type { IdentityVO, MeVO, UiResourceVO } from '@/api/types'
const KEY = 'rbac.token'
function readToken() { try { return localStorage.getItem(KEY) } catch { return null } }
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  const identity = ref<IdentityVO | null>(null)
  const profile = ref<MeVO | null>(null)
  const menus = ref<UiResourceVO[]>([])
  const loadedAt = ref(0)
  const targetSystem = ref('rbac-center')
  const loaded = computed(() => identity.value !== null)
  const permissions = computed(() => new Set(identity.value?.permissions ?? []))
  // identityRole 仅用于展示，绝不据此通配 UI 权限。
  function has(code: string) { return permissions.value.has(code) }
  function setToken(value: string | null) {
    token.value = value
    try { if (value) localStorage.setItem(KEY, value); else localStorage.removeItem(KEY) } catch { /* 禁用存储时只用内存 */ }
  }
  function clear() { setToken(null); identity.value = null; profile.value = null; menus.value = []; loadedAt.value = 0 }
  async function refresh() {
    const [who, mine, navigation] = await Promise.all([authApi.me(), authApi.profile(), authApi.menus()])
    identity.value = who; profile.value = mine; menus.value = navigation; loadedAt.value = Date.now()
  }
  async function login(username: string, password: string) { const result = await authApi.login(username, password); setToken(result.accessToken); await refresh() }
  async function logout() { try { if (token.value) await authApi.logout() } finally { clear() } }
  return { token, identity, profile, menus, loadedAt, targetSystem, loaded, permissions, has, setToken, clear, refresh, login, logout }
})
