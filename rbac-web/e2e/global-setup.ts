import { createConnection, type Connection } from 'mysql2/promise'
import { randomBytes } from 'node:crypto'
import { spawn, execFileSync, type ChildProcess } from 'node:child_process'
import { mkdirSync, openSync, closeSync, existsSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { environment } from './environment'

/** 只拥有本次新建的唯一数据库和子进程；任何失败也不清空其它库或 Redis。 */
export default async function globalSetup() {
  const env = environment()
  const name = `rbac1_e2e_${env.module.replace('rbac-', '')}_${Date.now()}`
  const bootstrap = `E2e!${randomBytes(20).toString('hex')}Z`
  const children: ChildProcess[] = [], handles: number[] = []
  let connection: Connection | undefined, created = false
  mkdirSync(env.logDir, { recursive: true })
  const stop = async () => {
    for (const child of children.reverse()) {
      if (!child.pid) continue
      if (process.platform === 'win32') { try { execFileSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' }) } catch { /* 已退出的自有进程 */ } }
      else child.kill('SIGTERM')
    }
    for (const handle of handles) closeSync(handle)
    if (connection) { if (created) await connection.query(`DROP DATABASE \`${name}\``); await connection.end() }
  }
  function start(command: string, args: string[], extra: NodeJS.ProcessEnv, label: string) {
    const fd = openSync(join(env.logDir, `m8-web-${env.module}-${label}.log`), 'w'); handles.push(fd)
    const child = spawn(command, args, { cwd: resolve('.'), env: { ...process.env, ...extra }, stdio: ['ignore', fd, fd], windowsHide: true })
    children.push(child)
    child.on('error', () => { /* 轮询通过退出状态报告启动失败，不输出环境凭据 */ })
    return child
  }
  async function wait(url: string, child: ChildProcess) {
    const deadline = Date.now() + 120000
    while (Date.now() < deadline) {
      if (child.exitCode !== null || !child.pid) throw new Error('E2E 子进程启动失败，见仓库外 m8-web 日志')
      try { const result = await fetch(url, { signal: AbortSignal.timeout(2000) }); if (result.ok) return } catch { /* 等待健康状态，不放行异常 */ }
      await new Promise(resolve => setTimeout(resolve, 500))
    }
    throw new Error(`等待健康检查失败: ${url}`)
  }
  try {
    const jar = resolve('..', env.module, 'target', `${env.module}.jar`)
    if (!existsSync(jar)) throw new Error(`缺少 ${env.module} jar，请先构建对应后端`)
    // 端口已有服务时拒绝复用，以免把测试数据写入其它运行实例。
    for (const url of [env.api, env.web]) { let occupied = false; try { await fetch(url, { signal: AbortSignal.timeout(1000) }); occupied = true } catch { /* 空端口 */ } if (occupied) throw new Error(`测试端口已有服务，拒绝复用: ${url}`) }
    connection = await createConnection({ host: env.host, port: env.port, user: process.env.E2E_DB_USER ?? 'root', password: process.env.E2E_DB_PASSWORD ?? 'root' })
    await connection.query(`CREATE DATABASE \`${name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`); created = true
    process.env.E2E_BOOTSTRAP_PASSWORD = bootstrap
    process.env.E2E_CREATED_DB = name
    const java = process.env.JAVA_BIN ?? (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java')
    const backend = start(java, ['-jar', jar], {
      SERVER_ADDRESS: '127.0.0.1', SERVER_PORT: String(env.apiPort),
      DB_URL: `jdbc:mysql://127.0.0.1:13306/${name}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false`,
      DB_USER: process.env.E2E_DB_USER ?? 'root', DB_PASSWORD: process.env.E2E_DB_PASSWORD ?? 'root',
      REDIS_HOST: '127.0.0.1', REDIS_PORT: '16379', RBAC_CACHE_NAMESPACE: `${env.module}:${name}`,
      RBAC_JWT_ALLOW_EPHEMERAL: 'true', RBAC_BOOTSTRAP_PASSWORD: bootstrap,
      RBAC_AUDIT_SERVICE_KEY: `BackendOnly!${randomBytes(24).toString('hex')}`,
    }, 'backend')
    await wait(`${env.api}/api/v1/actuator/health`, backend)
    const web = start(process.execPath, [resolve('node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', String(env.webPort), '--strictPort'], { RBAC_API: env.api }, 'vite')
    await wait(env.web, web)
    return stop
  } catch (error) { await stop(); throw error }
}
