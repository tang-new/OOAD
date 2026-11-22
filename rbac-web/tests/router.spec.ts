import { afterEach, beforeEach, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createAppRouter, signedInDestination } from '@/router'
import { authApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { flattenMenus, visibleMenus } from '@/router/routes'
import type { UiResourceVO } from '@/api/types'

const menu = (id: number, route: string | null, type: 'MENU' | 'BUTTON' = 'MENU', children: UiResourceVO[] = []): UiResourceVO => ({
  id, systemCode: 'rbac-center', parentId: null, type, code: `m${id}`, name: '菜单', route, component: 'Users', sortOrder: 0, status: 'ENABLED', permissionCode: `p${id}`, children,
})
beforeEach(() => { setActivePinia(createPinia()) })
afterEach(() => { vi.restoreAllMocks() })
it('多层投影只接受启用路由及精确权限，并保留同一路由获准的其中一个入口', () => {
  const tree = [menu(1, null, 'MENU', [menu(2, '/users'), menu(3, '/x', 'BUTTON')]),
    { ...menu(4, '/roles', 'MENU', [menu(6, '/departments')]), status: 'DISABLED' as const }, menu(5, '/users'), menu(7, '/dashboard'), menu(8, '/custom')]
  expect(flattenMenus(tree)).toHaveLength(8)
  expect(visibleMenus(tree, code => ['p2', 'p3', 'p4', 'p5', 'p6', 'p8'].includes(code)).map(row => row.id)).toEqual([2])
  expect(visibleMenus(tree, code => code === 'p5').map(row => row.id)).toEqual([5])
  expect(flattenMenus([{ ...menu(9, '/unknown'), children: undefined } as unknown as UiResourceVO])).toHaveLength(1)
})
it('未登录深链返回登录，登录本身公开', async () => {
  const router = createAppRouter(); await router.push('/users?userId=5')
  expect(router.currentRoute.value.path).toBe('/login')
  expect(router.currentRoute.value.query.redirect).toBe('/users?userId=5')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/login')
})
it('首页菜单存在时仍根据服务端仪表盘授权决定首页和深链', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: [], permissions: ['p7', 'p2'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockResolvedValue([menu(7, '/dashboard'), menu(2, '/users')])
  const canCall = vi.spyOn(authApi, 'canCall').mockResolvedValue(false)

  const router = createAppRouter()
  await router.push('/')
  expect(router.currentRoute.value.path).toBe('/me')
  expect(auth.navigation.map(item => item.route)).toEqual(['/users'])
  await router.push('/dashboard')
  expect(router.currentRoute.value.path).toBe('/403')

  canCall.mockResolvedValue(true)
  await router.push('/dashboard')
  expect(router.currentRoute.value.path).toBe('/dashboard')
  expect(auth.canVisit('/dashboard')).toBe(true)
})
it('菜单停用或从投影移除时，旧的精确权限不能直达页面', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  let navigation = [menu(2, '/users'), menu(7, '/dashboard')]
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: ['admin'], permissions: ['p2', 'p7', 'rbac:ui:roles:view'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockImplementation(async () => navigation)
  vi.spyOn(authApi, 'canCall').mockResolvedValue(true)
  const refresh = vi.spyOn(auth, 'refresh')
  const router = createAppRouter()
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/users')
  expect(signedInDestination(router, '/users?userId=3', auth)).toBe('/users?userId=3')
  expect(signedInDestination(router, '//example.com', auth)).toBe('/dashboard')
  expect(signedInDestination(router, '/roles', auth)).toBe('/dashboard')
  await router.push('/roles'); expect(router.currentRoute.value.path).toBe('/403')
  await router.push('/not-exist'); expect(router.currentRoute.value.path).toBe('/not-exist')
  navigation = [menu(7, '/dashboard')]
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/403')
  expect(refresh).toHaveBeenCalledTimes(3)
  await router.push('/'); expect(router.currentRoute.value.path).toBe('/dashboard')
  await router.push('/me'); auth.loadedAt = 0
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/dashboard')
})
it('刷新失败关闭保护路由，初次初始化时允许登录页', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  vi.spyOn(auth, 'refresh').mockRejectedValue(new Error('503'))
  const router = createAppRouter()
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/login')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/login')
  auth.identity = { subject: 'x', name: 'x', no: '', roles: [], permissions: ['p2'] }
  auth.menus = [menu(2, '/users')]; auth.loadedAt = Date.now()
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/403')
})
