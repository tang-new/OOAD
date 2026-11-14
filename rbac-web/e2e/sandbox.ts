import { spawnSync, type ChildProcess } from 'node:child_process'
import { existsSync } from 'node:fs'
import { isAbsolute, join, resolve } from 'node:path'

/** 只传操作系统启动所需变量；绝不传播上游 Spring、DB、Redis、JVM 或 Node 注入配置。 */
export function systemEnvironment(source: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  const names = new Set(['SYSTEMROOT', 'WINDIR', 'COMSPEC', 'PATHEXT', 'PATH', 'TEMP', 'TMP', 'HOME', 'USERPROFILE', 'LOCALAPPDATA', 'APPDATA', 'PROGRAMDATA', 'OS', 'PROCESSOR_ARCHITECTURE', 'NUMBER_OF_PROCESSORS', 'LANG', 'LC_ALL', 'TZ'])
  return Object.fromEntries(Object.entries(source).filter(([key, value]) => names.has(key.toUpperCase()) && value !== undefined))
}

/** 不接受 PATH 上的 Java 或 JAVA_BIN；必须显式提供并验证 Java21 安装。 */
export function java21(source: NodeJS.ProcessEnv, base: NodeJS.ProcessEnv): string {
  if (source.JAVA_BIN) throw new Error('E2E 禁止 JAVA_BIN；请显式设置 Java21 JAVA_HOME')
  if (!source.JAVA_HOME || !isAbsolute(source.JAVA_HOME)) throw new Error('E2E 必须显式设置 Java21 JAVA_HOME 绝对路径')
  const executable = join(resolve(source.JAVA_HOME), 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  if (!existsSync(executable)) throw new Error('JAVA_HOME 下缺少 Java 可执行文件')
  const result = spawnSync(executable, ['-version'], { env: base, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 10000, windowsHide: true })
  if (result.error || result.status !== 0 || !/version\s+"21(?:\.|\")/.test(`${result.stdout}\n${result.stderr}`)) throw new Error('JAVA_HOME 必须指向已验证的 Java21')
  return executable
}

export interface SandboxConfig { module: string; database: string; apiPort: number; user: string; password: string; bootstrap: string; auditKey: string }
export function springConfiguration(config: SandboxConfig): string {
  if (!new RegExp(`^rbac1_e2e_${config.module.replace('rbac-', '')}_[0-9]+$`).test(config.database) || !['rbac-structured', 'rbac-ddd'].includes(config.module)) throw new Error('拒绝非本次隔离数据库名称')
  const url = `jdbc:mysql://127.0.0.1:13306/${config.database}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false`
  return JSON.stringify({
    server: { address: '127.0.0.1', port: config.apiPort },
    spring: {
      profiles: { active: 'dev' },
      datasource: { url, username: config.user, password: config.password },
      flyway: { url, user: config.user, password: config.password, locations: ['classpath:db/migration'], 'clean-disabled': true },
      data: { redis: { host: '127.0.0.1', port: 16379, database: 0, timeout: '500ms' } },
    },
    management: { endpoint: { health: { probes: { enabled: true } } } },
    rbac: {
      cache: { namespace: `${config.module}:${config.database}` },
      jwt: { 'allow-ephemeral': true, 'private-key-file': '', 'public-key-file': '', 'previous-public-keys': '', issuer: 'rbac-center' },
      'bootstrap-password': config.bootstrap, 'audit-service-key': config.auditKey,
    },
    // DDD 引导器使用这个精确属性名；只放在本次 JSON 中，不继承环境版本。
    RBAC_BOOTSTRAP_PASSWORD: config.bootstrap,
  })
}

export interface OwnedProcess { child: ChildProcess; completed: Promise<void>; exited: boolean }
export function ownProcess(child: ChildProcess): OwnedProcess {
  const owned: OwnedProcess = { child, completed: Promise.resolve(), exited: false }
  owned.completed = new Promise<void>(resolve => {
    const finish = () => { owned.exited = true; resolve() }
    child.once('exit', finish)
    child.once('error', finish)
  })
  return owned
}
export function alive(owned: OwnedProcess): boolean { return !owned.exited && Boolean(owned.child.pid) && owned.child.exitCode === null && owned.child.signalCode === null }
/** ChildProcess.kill 使用本次进程句柄，不使用可能已回收的 PID 调 taskkill。等待退出才允许删库。 */
export async function stopOwned(owned: OwnedProcess): Promise<void> {
  if (!alive(owned)) { await owned.completed; return }
  owned.child.kill('SIGTERM')
  let timer: ReturnType<typeof setTimeout> | undefined
  const exited = await Promise.race([owned.completed.then(() => true), new Promise<false>(resolve => { timer = setTimeout(() => resolve(false), 8000) })])
  if (timer) clearTimeout(timer)
  if (!exited && alive(owned)) owned.child.kill('SIGKILL')
  let forceTimer: ReturnType<typeof setTimeout> | undefined
  try { await Promise.race([owned.completed, new Promise<never>((_, reject) => { forceTimer = setTimeout(() => reject(new Error('自有子进程尚未退出，拒绝删除测试库')), 8000) })]) }
  finally { if (forceTimer) clearTimeout(forceTimer) }
}
