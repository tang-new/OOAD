import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { flattenMenus, pages } from './routes'
export function createAppRouter() {
  const router = createRouter({ history: createWebHistory(), routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
      { path: '', redirect: '/me' },
      { path: 'me', component: () => import('@/views/MeView.vue'), meta: { title: '本人资料' } },
      ...pages.map(page => ({ path: page.path, component: () => import('@/views/ManagementView.vue'), meta: { title: page.title, permission: page.permission } })),
      { path: '/403', component: () => import('@/views/StatusView.vue'), props: { status: 403 } },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/StatusView.vue'), props: { status: 404 } },
    ] },
  ] })
  router.beforeEach(async to => {
    const auth = useAuthStore()
    if (!auth.token) return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
    if (!auth.loaded || Date.now() - auth.loadedAt > 30000) {
      try { await auth.refresh() } catch { return to.path === '/login' ? true : '/login' }
    }
    if (to.path === '/login') return '/me'
    const menu = flattenMenus(auth.menus).find(node => node.type === 'MENU' && node.route === to.path)
    const required = menu?.permissionCode ?? to.meta.permission as string | undefined
    if (required && !auth.has(required)) return '/403'
    return true
  })
  router.afterEach(to => { document.title = `${String(to.meta.title ?? '统一权限中心')} · RBAC1` })
  return router
}
