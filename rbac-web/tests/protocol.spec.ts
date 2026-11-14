import { it, expect, vi } from 'vitest'
import { authApi } from '@/api'
import { identity, management } from '@/api/http'
it('认证根路径、单一管理 aud、裸 token、logout 无 body；me 与 menus 独立管理包装', async () => {
  const raw = vi.fn(async config => ({ data: { accessToken: 'dummy', roles: ['teacher'], allowed: false }, status: 200, statusText: 'OK', headers: {}, config }))
  const wrapped = vi.fn(async config => ({ data: { code: 'OK', data: { user: {}, effective: {} } }, status: 200, statusText: 'OK', headers: {}, config }))
  identity.defaults.adapter = raw; management.defaults.adapter = wrapped
  expect((await authApi.login('virtual', 'fictional')).accessToken).toBe('dummy')
  expect(JSON.parse(raw.mock.calls[0][0].data)).toEqual({ username: 'virtual', password: 'fictional', systems: ['rbac-center'] })
  await authApi.logout(); expect(raw.mock.calls[1][0].url).toBe('/auth/logout'); expect(raw.mock.calls[1][0].data).toBeUndefined()
  await authApi.me(); expect(raw.mock.calls[2][0].url).toBe('/auth/me')
  await authApi.profile(); await authApi.menus(); expect(wrapped.mock.calls.map(([c]) => c.url)).toEqual(['/me', '/menus/me'])
})
