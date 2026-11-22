import { createRouter, createWebHistory, type Router } from 'vue-router'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { homePath, pageByPath, pages } from './routes'

const refreshFailurePath = '/auth-refresh-failed'

export function signedInDestination(router: Router, redirect: unknown, auth: ReturnType<typeof useAuthStore>) {
  if (typeof redirect === 'string' && /^\/(?![\/\\])/.test(redirect)) {
    const target = router.resolve(redirect)
    const matchedPath = target.matched.at(-1)?.path
    if (target.path === '/me' || (matchedPath && auth.canVisit(matchedPath))) return target.fullPath
  }
  return homePath(auth.menus, auth.has)
}

export function createAppRouter() {
  const router = createRouter({ history: createWebHistory(), routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
      { path: '', component: () => import('@/views/MeView.vue') },
      { path: 'me', component: () => import('@/views/MeView.vue'), meta: { title: '本人资料' } },
      ...pages.map(page => ({ path: page.path, component: page.view, meta: { title: page.title, group: page.group } })),
      { path: '/403', component: () => import('@/views/StatusView.vue'), props: { status: 403 }, meta: { title: '暂无访问权限' } },
      { path: refreshFailurePath, component: () => import('@/views/StatusView.vue'), props: { status: 'refresh-failed' }, meta: { title: '权限刷新失败' } },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/StatusView.vue'), props: { status: 404 }, meta: { title: '页面未找到' } },
    ] },
  ] })
  router.beforeEach(async (to, from) => {
    const matchedPath = to.matched.at(-1)?.path
    if (matchedPath && matchedPath !== to.path && pageByPath.has(matchedPath)) {
      return { path: matchedPath, query: to.query, hash: to.hash }
    }
    const auth = useAuthStore()
    if (!auth.token) return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
    if (to.path === refreshFailurePath || (to.path === '/403' && to.redirectedFrom)) return true
    const management = pageByPath.has(to.path)
    if (!auth.loaded || Date.now() - auth.loadedAt > 30000 || (management && to.path !== from.path)) {
      try {
        await auth.refresh()
      } catch (error) {
        if (auth.token && management) {
          if (error instanceof ApiError && error.status === 403) return '/403'
          return { path: refreshFailurePath, query: { redirect: to.fullPath } }
        }
        if (auth.token && auth.loaded) return true
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
