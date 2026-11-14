import { beforeEach, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { flattenMenus, visibleMenus, homePath } from '@/router/routes'
import type { UiResourceVO } from '@/api/types'
const menu = (id: number, route: string | null, type: 'MENU' | 'BUTTON' = 'MENU', children: UiResourceVO[] = []): UiResourceVO => ({ id, systemCode: 'rbac-center', parentId: null, type, code: `m${id}`, name: '菜单', route, component: 'Users', sortOrder: 0, status: 'ENABLED', permissionCode: `p${id}`, children })
beforeEach(() => { setActivePinia(createPinia()) })
it('任意深度菜单精确权限；父可见不赋子按钮，home 空集到本人', () => {
  const tree = [menu(1, null, 'MENU', [menu(2, '/users'), menu(3, '/x', 'BUTTON')]), { ...menu(4, '/off'), status: 'DISABLED' as const }, menu(5, '/no')]
  expect(flattenMenus(tree)).toHaveLength(5)
  expect(visibleMenus(tree, code => ['p2', 'p3', 'p4'].includes(code)).map(m => m.id)).toEqual([2])
  expect(homePath(tree)).toBe('/users'); expect(homePath([])).toBe('/me')
  expect(flattenMenus([{ ...menu(6, '/unknown'), children: undefined } as unknown as UiResourceVO])).toHaveLength(1)
})
it('未登录深链返回登录，登录本身公开', async () => {
  const router = createAppRouter(); await router.push('/users'); expect(router.currentRoute.value.path).toBe('/login'); expect(router.currentRoute.value.query.redirect).toBe('/users')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/login')
})
it('加载身份后精确守卫，404 不误作 403，有权才进入管理页', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  const refresh = vi.spyOn(auth, 'refresh').mockImplementation(async () => { auth.identity = { subject: 'x', name: 'x', no: '', roles: ['admin'], permissions: ['p2'] }; auth.menus = [menu(2, '/users')]; auth.loadedAt = Date.now() })
  const router = createAppRouter(); await router.push('/users'); expect(router.currentRoute.value.path).toBe('/users'); expect(refresh).toHaveBeenCalledTimes(1)
  await router.push('/roles'); expect(router.currentRoute.value.path).toBe('/403')
  await router.push('/not-exist'); expect(router.currentRoute.value.path).toBe('/not-exist')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/me')
  await router.push('/not-exist'); auth.loadedAt = 0; await router.push('/me'); expect(refresh).toHaveBeenCalledTimes(2)
  await router.push('/'); expect(router.currentRoute.value.path).toBe('/me')
})
it('刷新失败不放行受保护页，也不形成登录重定向循环', async () => {
  const auth = useAuthStore(); auth.setToken('dummy'); vi.spyOn(auth, 'refresh').mockRejectedValue(new Error('503'))
  const router = createAppRouter(); await router.push('/users'); expect(router.currentRoute.value.path).toBe('/login')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/login')
})
