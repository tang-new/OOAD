import { createConnection, type Connection } from 'mysql2/promise'
import { randomBytes } from 'node:crypto'
import { spawn } from 'node:child_process'
import { createServer } from 'node:net'
import { mkdirSync, openSync, closeSync, existsSync, mkdtempSync, copyFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { environment } from './environment'
import { alive, java21, ownProcess, springConfiguration, stopOwned, systemEnvironment, type OwnedProcess } from './sandbox'

/** TCP 绑定探测不会把 HTTP 重定向/非 HTTP 监听者误认为空端口。 */
async function requireFreePort(port: number) {
  await new Promise<void>((resolve, reject) => {
    const server = createServer()
    server.once('error', () => reject(new Error(`测试端口 ${port} 已占用，拒绝复用`)))
    server.listen(port, '127.0.0.1', () => server.close(error => error ? reject(new Error('端口探测关闭失败')) : resolve()))
  })
}

/** 只拥有本次新建的唯一数据库和进程；终止并等待自有进程后才清理数据库。 */
export default async function globalSetup() {
  const env = environment(), base = systemEnvironment(process.env)
  const runId = process.env.E2E_RUN_ID ?? String(Date.now())
  if (!/^[A-Za-z0-9_-]+$/.test(runId)) throw new Error('非法 E2E_RUN_ID')
  const java = java21(process.env, base)
  const name = `rbac1_e2e_${env.module.replace('rbac-', '')}_${Date.now()}`
  const bootstrap = `E2e!${randomBytes(20).toString('hex')}Z`
  const configuration = springConfiguration({ module: env.module, database: name, apiPort: env.apiPort, user: process.env.E2E_DB_USER ?? 'root', password: process.env.E2E_DB_PASSWORD ?? 'root', bootstrap, auditKey: `BackendOnly!${randomBytes(24).toString('hex')}` })
  const children: OwnedProcess[] = [], handles: number[] = []
  let connection: Connection | undefined, created = false, stopped = false
  let artifactDirectory: string | undefined
  mkdirSync(env.logDir, { recursive: true })
  const stop = async () => {
    if (stopped) return
    // 不以 PID 调用 taskkill；进程句柄已退出时不会尝试杀掉任何其它进程。
    for (const owned of [...children].reverse()) await stopOwned(owned)
    for (const handle of handles.splice(0)) closeSync(handle)
    if (connection) {
      try {
        if (created && new RegExp(`^rbac1_e2e_${env.module.replace('rbac-', '')}_[0-9]+$`).test(name)) { await connection.query(`DROP DATABASE \`${name}\``); created = false }
      } finally { await connection.end(); connection = undefined }
    }
    // 只删除本次 mkdtemp 创建的私有 jar 快照；进程已退出，不会锁住发布 target。
    if (artifactDirectory) { rmSync(artifactDirectory, { recursive: true, force: true }); artifactDirectory = undefined }
    delete process.env.E2E_BOOTSTRAP_PASSWORD
    delete process.env.E2E_CREATED_DB
    stopped = true
  }
  function start(command: string, args: string[], extra: NodeJS.ProcessEnv, label: string) {
    const fd = openSync(resolve(env.logDir, `m8-web-${env.module}-${runId}-${label}.log`), 'w'); handles.push(fd)
    const owned = ownProcess(spawn(command, args, { cwd: resolve('.'), env: { ...base, ...extra }, stdio: ['ignore', fd, fd], windowsHide: true }))
    children.push(owned)
    return owned
  }
  async function wait(url: string, owned: OwnedProcess) {
    const deadline = Date.now() + 240000
    while (Date.now() < deadline) {
      if (!alive(owned)) throw new Error('E2E 子进程启动失败，见仓库外 m8-web 日志')
      try { const result = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(2000) }); if (result.ok) return } catch { /* 等待健康状态，任何重定向都不跟随 */ }
      await new Promise(resolve => setTimeout(resolve, 500))
    }
    throw new Error(`等待健康检查失败: ${url}`)
  }
  try {
    const sourceJar = resolve('..', env.module, 'target', `${env.module}.jar`)
    if (!existsSync(sourceJar)) throw new Error(`缺少 ${env.module} jar，请先构建对应后端`)
    // Windows 运行中的 jar 会锁文件。只运行自有临时副本，不锁 Maven/发布的 target。
    const before = statSync(sourceJar)
    artifactDirectory = mkdtempSync(join(tmpdir(), 'rbac1-e2e-artifact-'))
    const jar = join(artifactDirectory, `${env.module}.jar`)
    copyFileSync(sourceJar, jar)
    const after = statSync(sourceJar)
    if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || statSync(jar).size !== before.size) throw new Error('后端 jar 正在发布，请发布完成后重新运行 E2E')
    await requireFreePort(env.apiPort); await requireFreePort(env.webPort)
    connection = await createConnection({ host: env.host, port: env.port, user: process.env.E2E_DB_USER ?? 'root', password: process.env.E2E_DB_PASSWORD ?? 'root' })
    await connection.query(`CREATE DATABASE \`${name}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`); created = true
    process.env.E2E_BOOTSTRAP_PASSWORD = bootstrap
    process.env.E2E_CREATED_DB = name
    // 密码只通过本次自建 JSON 环境属性传输，绝不放 CLI；只加载 jar classpath 配置。
    const backend = start(java, ['-jar', jar, '--spring.config.location=classpath:/'], { SPRING_APPLICATION_JSON: configuration }, 'backend')
    await wait(`${env.api}/api/v1/actuator/health`, backend)
    // health 可在 ApplicationRunner（路由核验/口令引导）完成之前可达；必须等就绪事件。
    await wait(`${env.api}/api/v1/actuator/health/readiness`, backend)
    // Web 只得到回环代理地址，不继承后端 JSON、DB/JWT/审计密钥。
    const web = start(process.execPath, [resolve('node_modules/vite/bin/vite.js'), '--host', '127.0.0.1', '--port', String(env.webPort), '--strictPort'], { RBAC_API: env.api }, 'vite')
    await wait(env.web, web)
    return stop
  } catch (error) { await stop(); throw error }
}
