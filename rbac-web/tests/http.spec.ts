import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { management, identity, request, raw, get, post, patch, put, del, setAuthHooks, handleError, ApiError } from '@/api/http'
const hooks = { getToken: vi.fn((): string | null => 'test-token'), onUnauthenticated: vi.fn(), onForbidden: vi.fn() }
function adapter(data: unknown, status = 200) { return vi.fn(async (config: InternalAxiosRequestConfig) => ({ data, status, statusText: 'OK', headers: {}, config })) }
beforeEach(() => { vi.clearAllMocks(); setAuthHooks(hooks); hooks.getToken.mockReturnValue('test-token') })
describe('双协议客户端与安全错误', () => {
  it('management 仅解包 OK，两个 client 共用 bearer，根 identity 保持裸响应', async () => {
    management.defaults.adapter = adapter({ code: 'OK', data: { page: 1, pageSize: 20, total: 0, items: [] } })
    identity.defaults.adapter = adapter({ allowed: false })
    expect(await get('/users', { page: 1 })).toEqual({ page: 1, pageSize: 20, total: 0, items: [] })
    expect(await raw({ url: '/auth/permissions/no' })).toEqual({ allowed: false })
    expect(management.defaults.adapter).toHaveBeenCalledWith(expect.objectContaining({ baseURL: '/api/v1', headers: expect.objectContaining({ Authorization: 'Bearer test-token' }) }))
    expect(identity.defaults.adapter).toHaveBeenCalledWith(expect.objectContaining({ baseURL: '/' }))
    expect(hooks.onForbidden).not.toHaveBeenCalled()
  })
  it('无 token 不造 Authorization，204 与各种 mutation body 保持精确', async () => {
    hooks.getToken.mockReturnValue(null)
    const call = adapter(undefined, 204); management.defaults.adapter = call
    expect(await post('/x', { a: 1 })).toBeUndefined()
    await patch('/x', { name: 'n' }); await put('/x/status', { status: 'DISABLED' }); await del('/x')
    expect(call.mock.calls.map(([c]) => c.method)).toEqual(['post', 'patch', 'put', 'delete'])
    expect(call.mock.calls[0][0].headers.Authorization).toBeUndefined()
  })
  it('不接受旧数字成功码或非 OK 包装，不二次包装 ApiError', async () => {
    management.defaults.adapter = adapter({ code: 'NO', message: '业务拒绝' })
    await expect(request({ url: '/x' })).rejects.toMatchObject({ code: 'NO', message: '业务拒绝', status: 200 })
  })
  it.each([401, 403, 404, 409, 503])('真实 HTTP %i 分类且保留中文说明', async status => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    const err = new AxiosError('unused', 'ERR', config, null, { data: { code: 'DENIED', message: '真实拒绝' }, status, statusText: '', headers: {}, config })
    management.defaults.adapter = async () => { throw err }
    identity.defaults.adapter = async () => { throw err }
    await expect(get('/x')).rejects.toMatchObject({ status, code: 'DENIED', message: '真实拒绝' })
    await expect(raw({ url: '/auth/me' })).rejects.toBeInstanceOf(ApiError)
    expect(hooks.onUnauthenticated).toHaveBeenCalledTimes(status === 401 ? 2 : 0)
    expect(hooks.onForbidden).toHaveBeenCalledTimes(status === 403 ? 2 : 0)
  })
  it('缺失错误 body 与断网明确区分，默认 hooks 安全', () => {
    expect(handleError({}).message).toBe('无法连接权限中心')
    expect(handleError({ response: { status: 403 } })).toMatchObject({ code: 'NETWORK_ERROR', message: '请求失败（HTTP 403）' })
  })
})
