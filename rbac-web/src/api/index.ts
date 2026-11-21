import { raw, get } from './http'
import type { IdentityVO, MeVO, UiResourceVO } from './types'

export { get, post, patch, put, del, raw } from './http'
export const authApi = {
  login: (username: string, password: string) => raw<{ accessToken: string; tokenType: 'Bearer'; expiresIn: number }>({ method: 'POST', url: '/auth/login', data: { username, password, systems: ['rbac-center'] } }),
  logout: () => raw<void>({ method: 'POST', url: '/auth/logout' }),
  me: () => raw<IdentityVO>({ method: 'GET', url: '/auth/me' }),
  profile: () => get<MeVO>('/me'),
  menus: () => get<UiResourceVO[]>('/menus/me'),
  async canCall(operation: string) {
    const result = await raw<{ operationId: string; allowed: boolean }>({ method: 'GET', url: '/auth/api-permissions/' + encodeURIComponent(operation) })
    return result.allowed
  },
}
