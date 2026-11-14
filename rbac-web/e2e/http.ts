/** E2E API 请求必须拒绝重定向，避免 bearer/虚构口令跟随 Location 离开专用后端。 */
export interface HttpOptions { method?: string; headers?: Record<string, string>; data?: unknown }
export function strictHttp() {
  async function send(url: string, options: HttpOptions = {}) {
    const response = await fetch(url, {
      method: options.method ?? 'GET', redirect: 'error', signal: AbortSignal.timeout(20000),
      headers: { ...(options.data === undefined ? {} : { 'Content-Type': 'application/json' }), ...options.headers },
      ...(options.data === undefined ? {} : { body: JSON.stringify(options.data) }),
    })
    return { status: () => response.status, ok: () => response.ok, json: () => response.json() }
  }
  return { fetch: send, get: (url: string, options?: HttpOptions) => send(url, { ...options, method: 'GET' }), post: (url: string, options?: HttpOptions) => send(url, { ...options, method: 'POST' }) }
}
export type StrictHttp = ReturnType<typeof strictHttp>
