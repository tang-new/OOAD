import axios, { type AxiosError, type AxiosRequestConfig } from 'axios'
import type { Envelope } from './types'

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}
export interface AuthHooks { getToken: () => string | null; onUnauthenticated: () => void; onForbidden: () => void }
let hooks: AuthHooks = { getToken: () => null, onUnauthenticated: () => {}, onForbidden: () => {} }
export function setAuthHooks(value: AuthHooks) { hooks = value }
export const management = axios.create({ baseURL: '/api/v1', timeout: 20000 })
export const identity = axios.create({ baseURL: '/', timeout: 20000 })
for (const client of [management, identity]) {
  client.interceptors.request.use(config => {
    const token = hooks.getToken()
    if (token) config.headers.set('Authorization', `Bearer ${token}`)
    return config
  })
}
export function handleError(error: unknown): ApiError {
  const err = error as AxiosError<{ code?: string; message?: string }>
  const status = err.response?.status ?? 0
  const bearer = err.config?.headers?.Authorization
  const current = !bearer || bearer === `Bearer ${hooks.getToken()}`
  if (current && status === 401 && err.config?.url !== '/auth/login') hooks.onUnauthenticated()
  if (current && status === 403) hooks.onForbidden()
  return new ApiError(status, err.response?.data?.code ?? 'NETWORK_ERROR', err.response?.data?.message ?? (status ? `请求失败（HTTP ${status}）` : '无法连接权限中心'))
}
/** 不将 allowed:false 解释成网络异常，不将身份裸响应错误地解包。 */
export async function raw<T>(options: AxiosRequestConfig): Promise<T> {
  try { return (await identity.request<T>(options)).data } catch (error) { throw handleError(error) }
}
export async function request<T>(options: AxiosRequestConfig): Promise<T> {
  try {
    const response = await management.request<Envelope<T>>(options)
    if (response.status === 204) return undefined as T
    if (response.data.code !== 'OK') throw new ApiError(response.status, response.data.code, response.data.message)
    return response.data.data
  } catch (error) { if (error instanceof ApiError) throw error; throw handleError(error) }
}
export const get = <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params })
export const post = <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data })
export const patch = <T>(url: string, data: unknown) => request<T>({ method: 'PATCH', url, data })
export const put = <T>(url: string, data: unknown) => request<T>({ method: 'PUT', url, data })
export const del = <T>(url: string) => request<T>({ method: 'DELETE', url })
