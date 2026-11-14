import { beforeEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import type { IdentityVO, MeVO, UiResourceVO } from '@/api/types'
vi.mock('@/api', () => ({ authApi: { login: vi.fn(), logout: vi.fn(), me: vi.fn(), profile: vi.fn(), menus: vi.fn() } }))
const who: IdentityVO = { subject: 'rbac-u1', roles: ['admin'], permissions: ['rbac:ui:users:view'], name: '虚构人员', no: 'test' }
const mine = { user: { name: '虚构人员', departmentName: '总部' }, effective: { directRoles: [], effectiveRoles: [], permissions: [], sources: [] } } as unknown as MeVO
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks(); vi.mocked(authApi.me).mockResolvedValue(who); vi.mocked(authApi.profile).mockResolvedValue(mine); vi.mocked(authApi.menus).mockResolvedValue([]) })
describe('身份与权限状态', () => {
  it('没有身份默认无权，admin 不能通配，登录读取全部三个协议入口', async () => {
    const store = useAuthStore(); expect(store.loaded).toBe(false); expect(store.has('any')).toBe(false)
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'dummy', tokenType: 'Bearer', expiresIn: 600 })
    await store.login('test', 'not-real')
    expect(authApi.login).toHaveBeenCalledWith('test', 'not-real')
    expect(store.token).toBe('dummy'); expect(localStorage.getItem('rbac.token')).toBe('dummy')
    expect(store.profile).toEqual(mine); expect(store.loaded).toBe(true)
    expect(store.has('rbac:ui:users:view')).toBe(true); expect(store.has('rbac:ui:users')).toBe(false); expect(store.has('*')).toBe(false)
    expect(store.targetSystem).toBe('rbac-center')
    store.targetSystem = 'course-project-management'; expect(store.token).toBe('dummy')
  })
  it('授权刷新替换菜单与权限而不是并集', async () => {
    const store = useAuthStore(); await store.refresh()
    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [] })
    vi.mocked(authApi.menus).mockResolvedValue([{ id: 8 }] as UiResourceVO[])
    await store.refresh(); expect(store.has('rbac:ui:users:view')).toBe(false); expect(store.menus).toEqual([{ id: 8 }])
  })
  it('登录失败不制造会话，logout 即使网络失败也清理权限', async () => {
    const store = useAuthStore(); vi.mocked(authApi.login).mockRejectedValue(new Error('凭据错误'))
    await expect(store.login('x', 'x')).rejects.toThrow('凭据错误'); expect(store.token).toBeNull()
    store.setToken('dummy'); await store.refresh(); vi.mocked(authApi.logout).mockRejectedValue(new Error('offline'))
    await expect(store.logout()).rejects.toThrow(); expect(store.token).toBeNull(); expect(store.profile).toBeNull(); expect(store.identity).toBeNull(); expect(store.menus).toEqual([]); expect(store.loadedAt).toBe(0)
    await store.logout(); expect(authApi.logout).toHaveBeenCalledTimes(1)
  })
  it('存储被禁用仍可用内存会话，已有 token 能恢复', () => {
    localStorage.setItem('rbac.token', 'restored'); expect(useAuthStore().token).toBe('restored')
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('storage') })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage') })
    setActivePinia(createPinia()); const store = useAuthStore(); expect(store.token).toBeNull(); store.setToken('memory'); expect(store.token).toBe('memory'); store.clear()
    get.mockRestore(); set.mockRestore()
  })
})
