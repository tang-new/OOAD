import { createRouter, createWebHistory, type Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { homePath, pageByPath, pages } from './routes'

export function signedInDestination(router: Router, redirect: unknown, auth: ReturnType<typeof useAuthStore>) {
  if (typeof redirect === 'string' && /^\/(?![\/\\])/.test(redirect)) {
    const target = router.resolve(redirect)
    if (target.path === '/me' || auth.canVisit(target.path)) return target.fullPath
  }
  return homePath(auth.navigation)
}

export function createAppRouter() {
  const router = createRouter({ history: createWebHistory(), routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
      { path: '', component: () => import('@/views/MeView.vue') },
      { path: 'me', component: () => import('@/views/MeView.vue'), meta: { title: '本人资料' } },
      ...pages.map(page => ({ path: page.path, component: page.view, meta: { title: page.title, group: page.group } })),
      { path: '/403', component: () => import('@/views/StatusView.vue'), props: { status: 403 }, meta: { title: '暂无访问权限' } },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/StatusView.vue'), props: { status: 404 }, meta: { title: '页面未找到' } },
    ] },
  ] })
  router.beforeEach(async (to, from) => {
    const auth = useAuthStore()
    if (!auth.token) return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
    const management = pageByPath.has(to.path)
    if (!auth.loaded || Date.now() - auth.loadedAt > 30000 || (management && to.path !== from.path)) {
      try {
        await auth.refresh()
      } catch {
        if (auth.token && auth.loaded) return management ? '/403' : true
        return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
      }
    }
    if (!auth.token) return to.path === '/login' ? true : '/login'
    if (to.path === '/login' || to.path === '/') return signedInDestination(router, to.query.redirect, auth)
    if (management && !auth.canVisit(to.path)) return '/403'
    return true
  })
  router.afterEach(to => { document.title = `${String(to.meta.title ?? '统一权限中心')} · RBAC1` })
  return router
}
