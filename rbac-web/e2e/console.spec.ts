import { test, expect, type Page, type Locator } from '@playwright/test'
import { strictHttp, type StrictHttp } from './http'
import { environment } from './environment'
import type { SystemVO, DepartmentVO, RoleVO, PermissionVO, UserVO, ApiOperationVO, Page as ResultPage, EffectiveVO, AssignmentVO, AuditVO, UiResourceVO, HierarchyVO, CoverageVO } from '../src/api/types'

const env = environment()
type Admin = ReturnType<typeof api>
async function login(request: StrictHttp, username: string, password: string, system = 'rbac-center') {
  const result = await request.post(`${env.api}/auth/login`, { data: { username, password, systems: [system] } })
  const body = await result.json()
  expect(result.status(), `POST /auth/login HTTP ${result.status()} code=${String(body.code ?? 'none')}`).toBe(200)
  expect(body.code).toBeUndefined()
  expect(body.tokenType).toBe('Bearer')
  return body.accessToken as string
}
function api(request: StrictHttp, token: string) {
  async function call<T>(method: string, path: string, data?: unknown): Promise<T> {
    const result = await request.fetch(`${env.api}/api/v1${path}`, { method, headers: { Authorization: `Bearer ${token}` }, ...(data === undefined ? {} : { data }) })
    if (result.status() === 204) return undefined as T
    const body = await result.json()
    expect(result.ok(), `${method} ${path}: HTTP ${result.status()} code=${String(body.code ?? 'none')}`).toBe(true)
    expect(body.code).toBe('OK')
    return body.data as T
  }
  return {
    get: <T>(path: string) => call<T>('GET', path),
    post: <T>(path: string, data?: unknown) => call<T>('POST', path, data),
    patch: <T>(path: string, data: unknown) => call<T>('PATCH', path, data),
    put: <T>(path: string, data: unknown) => call<T>('PUT', path, data),
    del: (path: string) => call<void>('DELETE', path),
  }
}
function response(page: Page, path: string, method = 'GET') {
  return page.waitForResponse(result => new URL(result.url()).pathname === path && result.request().method() === method, { timeout: 30000 })
}
async function browserLogin(page: Page, username: string, password: string, destination: string) {
  if (new URL(page.url()).pathname !== '/login') await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill(password)
  const pending = ['/auth/login', '/auth/me', '/api/v1/me', '/api/v1/menus/me'].map((path, index) => response(page, path, index === 0 ? 'POST' : 'GET'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  for (const result of await Promise.all(pending)) expect(result.status(), new URL(result.url()).pathname).toBe(200)
  await expect(page).toHaveURL(new RegExp(destination + '$'))
}
async function browserLogout(page: Page) {
  await page.getByRole('button', { name: '账号菜单' }).click()
  const pending = response(page, '/auth/logout', 'POST')
  await page.getByText('退出登录', { exact: true }).click()
  const result = await pending
  expect(result.status()).toBe(204)
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  const revoked = await strictHttp().get(`${env.api}/auth/me`, { headers: { Authorization: (await result.request().allHeaders()).authorization } })
  expect(revoked.status()).toBe(401)
}
async function capture(page: Page, path: string, expectedAlert = false) {
  await expect(page.getByText('加载中…', { exact: true })).toHaveCount(0)
  if (!expectedAlert) await expect(page.locator('main [role="alert"]')).toHaveCount(0)
  await page.evaluate(async () => { await document.fonts.ready })
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
  await expect.poll(() => page.locator('input[type=password]').evaluateAll(inputs => inputs.every(input => (input as HTMLInputElement).value === ''))).toBe(true)
  await page.screenshot({ path, animations: 'disabled', caret: 'hide' })
}
async function pick(page: Page, scope: Page | Locator, label: string, item: string | RegExp) {
  const field = scope.locator('[aria-label="' + label + '"]').first()
  await field.click()
  const input = field.locator('input').first()
  if (await input.isVisible()) await input.fill(typeof item === 'string' ? item : item.source)
  const popup = page.locator('.n-base-select-menu:visible, .n-tree-select-menu:visible')
  await popup.getByText(item, { exact: typeof item === 'string' }).last().click()
}
async function nav(page: Page, title: string) {
  const link = page.getByRole('navigation', { name: '主导航' }).getByRole('link', { name: title, exact: true })
  const href = await link.getAttribute('href')
  await link.click()
  await page.waitForURL(url => url.pathname === href, { timeout: 30000 })
}
async function seed(admin: Admin, suffix: string) {
  const department = (await admin.get<DepartmentVO[]>('/departments/tree')).find(row => row.parentId === null)!
  const visual = suffix.startsWith('visual')
  const x = await admin.post<DepartmentVO>('/departments', { code: 'X_' + suffix, name: visual ? '教学运行部' : '权限运营部', parentId: department.id })
  const y = await admin.post<DepartmentVO>('/departments', { code: 'Y_' + suffix, name: visual ? '质量评估部' : '平台支持部', parentId: department.id })
  const roles = await Promise.all((visual ? ['教学负责人', '课程审核员', '授课协调员', '课程查看员'] : ['运营负责人', '授权审核员', '平台协调员', '目录查看员']).map((name, index) => admin.post<RoleVO>('/roles', {
    systemCode: 'rbac-center', name, code: `ROLE_${suffix}_${index}`,
  })))
  const [a, b, c, d] = roles
  const longRole = visual ? await admin.post<RoleVO>('/roles', {
    systemCode: 'rbac-center', name: '跨院系课程运行与教学质量复核权限管理员', code: 'LONG_ROLE_' + suffix,
  }) : null
  for (const [senior, junior] of [[a, b], [a, c], [b, d], [c, d]]) await admin.post(`/roles/${senior.id}/inherits/${junior.id}`)
  const permission = await admin.post<PermissionVO>('/permissions', { systemCode: 'rbac-center', code: 'e2e:ui:' + suffix, name: '演示首页', kind: 'UI' })
  const userView = (await admin.get<ResultPage<PermissionVO>>('/permissions?systemCode=rbac-center&kind=API&keyword=rbac:user:view&page=1&pageSize=20')).items.find(item => item.code === 'rbac:user:view')!
  await admin.put(`/roles/${d.id}/permissions`, { permissionIds: [permission.id, userView.id] })
  const menu = await admin.post<UiResourceVO>('/systems/rbac-center/ui-resources', {
    type: 'MENU', code: 'demo_' + suffix, name: '演示首页_' + suffix, route: '/dashboard', component: 'Dashboard',
    sortOrder: 1000, permissionCode: permission.code, status: 'ENABLED',
  })
  await admin.put(`/departments/${x.id}/assignable-roles`, { roleIds: [a.id] })
  const password = `Fictional!${suffix}Z9`
  const user = await admin.post<UserVO>('/users', { username: 'user_' + suffix, name: visual ? '林予安' : '周星野', departmentId: x.id, password })
  const other = await admin.post<UserVO>('/users', { username: 'other_' + suffix, name: visual ? '陈星河' : '许听澜', departmentId: y.id, password })
  return { x, y, a, b, c, d, menu, user, other, password, permission, longRole }
}

test('真实管理流程保留双后端 HTTP、RBAC1 继承和审计行为', async ({ page }, info) => {
  const request = strictHttp(), rootPassword = process.env.E2E_BOOTSTRAP_PASSWORD!
  const token = await login(request, 'root', rootPassword), admin = api(request, token)
  const suffix = 'flow' + Date.now()
  const { x, y, a, b, c, d, menu, user, other, password, permission } = await seed(admin, suffix)
  const systems = await admin.get<SystemVO[]>('/systems')
  expect(systems.map(system => system.code)).toEqual(expect.arrayContaining(['rbac-center', 'course-project-management']))
  const course = await admin.get<ResultPage<RoleVO>>('/roles?systemCode=course-project-management&page=1&pageSize=20')
  const cross = await request.post(`${env.api}/api/v1/roles/${a.id}/inherits/${course.items[0].id}`, { headers: { Authorization: `Bearer ${token}` } })
  expect([400, 403, 409]).toContain(cross.status())
  const deniedAssignment = await request.post(`${env.api}/api/v1/users/${other.id}/role-assignments`, { headers: { Authorization: `Bearer ${token}` }, data: { roleId: a.id } })
  expect([403, 409]).toContain(deniedAssignment.status())

  await browserLogin(page, 'root', rootPassword, '/dashboard')
  const systemSelector = page.getByLabel('管理目标系统')
  await expect(systemSelector).toHaveValue('rbac-center')
  await nav(page, '角色继承')
  const canvas = page.getByRole('region', { name: '角色继承有向无环图' })
  const graph = await admin.get<HierarchyVO>('/systems/rbac-center/role-hierarchy')
  await expect(canvas.locator('.dag-node')).toHaveCount(graph.roles.length)
  await expect(canvas.locator('.vue-flow__edge-path')).toHaveCount(graph.edges.length)
  await pick(page, page, '定位角色', new RegExp(a.name))
  await pick(page, page, '终点角色', new RegExp(d.name))
  await expect(canvas.locator('.path-active')).toHaveCount(4)
  await page.getByRole('radio', { name: new RegExp(b.name) }).check()
  await expect(canvas.locator('.path-active')).toHaveCount(2)
  await capture(page, info.outputPath('diamond-selected.png'))
  await page.getByRole('button', { name: '添加继承关系' }).click()
  const cycleDialog = page.getByRole('dialog')
  await pick(page, cycleDialog, '高级角色', new RegExp(d.name))
  await pick(page, cycleDialog, '被继承角色', new RegExp(a.name))
  const cycle = response(page, `/api/v1/roles/${d.id}/inherits/${a.id}`, 'POST')
  await cycleDialog.getByRole('button', { name: '保存', exact: true }).click()
  expect((await cycle).status()).toBe(409)
  await expect(cycleDialog.getByRole('alert')).toContainText('形成循环')
  await capture(page, info.outputPath('cycle-rejected.png'), true)
  await cycleDialog.getByRole('button', { name: '取消' }).click()
  await systemSelector.selectOption('course-project-management')
  await expect(canvas.locator('.dag-node')).toHaveCount(course.total)
  await systemSelector.selectOption('rbac-center')

  await nav(page, '用户管理')
  await expect(page.getByRole('button', { name: '创建用户', exact: true })).toBeEnabled({ timeout: 30000 })
  await page.getByRole('button', { name: '创建用户', exact: true }).click()
  const create = page.getByRole('dialog')
  await create.getByRole('button', { name: '保存', exact: true }).click()
  await expect(create.getByText('请填写或选择此项').first()).toBeVisible()
  await capture(page, info.outputPath('user-validation.png'), true)
  await create.getByLabel('账号', { exact: true }).fill('browser_' + suffix)
  await create.getByLabel('姓名', { exact: true }).fill('浏览器虚构_' + suffix)
  await pick(page, create, '所属部门', x.name)
  await create.getByLabel('初始密码', { exact: true }).fill(password)
  const created = response(page, '/api/v1/users', 'POST')
  await create.getByRole('button', { name: '保存', exact: true }).click()
  const createdUser = await created
  expect(createdUser.status()).toBe(200)
  expect((await createdUser.json()).data).toMatchObject({ username: 'browser_' + suffix, departmentId: x.id })
  await expect(create).not.toBeVisible()
  await page.getByLabel('姓名 / 账号').fill(user.username)
  await page.getByRole('button', { name: '检索', exact: true }).click()
  const userRow = page.getByRole('row').filter({ hasText: user.username })
  await userRow.getByRole('button', { name: '更多操作 ' + user.name }).click()
  await page.getByText('编辑资料', { exact: true }).click()
  const editor = page.getByRole('dialog')
  await editor.getByLabel('姓名', { exact: true }).fill('编辑姓名_' + suffix)
  const edited = response(page, `/api/v1/users/${user.id}`, 'PATCH')
  await editor.getByRole('button', { name: '保存', exact: true }).click()
  const editResult = await edited
  expect(editResult.ok()).toBeTruthy()
  expect(editResult.request().postDataJSON()).not.toHaveProperty('status')
  expect(editResult.request().postDataJSON()).not.toHaveProperty('departmentId')
  await expect(editor).not.toBeVisible({ timeout: 60000 })
  await expect(userRow).toContainText('编辑姓名_' + suffix)
  await userRow.getByRole('button', { name: '角色指派' }).click()
  await pick(page, page, '可指派角色', new RegExp(a.name))
  const assigned = response(page, `/api/v1/users/${user.id}/role-assignments`, 'POST')
  await page.getByRole('button', { name: '指派角色', exact: true }).click()
  expect((await assigned).ok()).toBeTruthy()
  await expect(page.getByRole('button', { name: '撤销指派' })).toBeVisible()
  await capture(page, info.outputPath('user-assigned.png'))
  await page.getByRole('tab', { name: '角色指派', exact: true }).focus()
  await page.keyboard.press('End')
  await expect(page.getByRole('tab', { name: '有效权限', exact: true })).toBeFocused()
  await expect(page.getByText(permission.code, { exact: true })).toBeVisible()
  const effective = await admin.get<EffectiveVO>(`/users/${user.id}/systems/rbac-center/permissions`)
  expect(effective.sources.filter(source => source.permissionCode === permission.code)).toHaveLength(2)
  await page.getByRole('link', { name: '返回用户列表', exact: false }).click()
  await expect(page.getByLabel('姓名 / 账号')).toHaveValue(user.username)
  await page.getByLabel('姓名 / 账号').fill(other.username)
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await page.getByRole('row').filter({ hasText: other.username }).getByRole('button', { name: '更多操作 ' + other.name }).click()
  await page.getByText('变更状态', { exact: true }).click()
  const userStatus = page.getByRole('dialog')
  await pick(page, userStatus, '状态', '停用')
  const statusChange = response(page, `/api/v1/users/${other.id}/status`, 'PUT')
  await userStatus.getByRole('button', { name: '保存' }).click()
  const statusResult = await statusChange
  expect(statusResult.status()).toBe(200)
  expect(statusResult.request().postDataJSON()).toEqual({ status: 'DISABLED' })
  await expect(userStatus).not.toBeVisible()

  await nav(page, 'UI ↔ API 映射')
  await pick(page, page, 'UI 资源', new RegExp(menu.name))
  await page.getByLabel('查找 API 操作').fill('getDashboard')
  await page.getByRole('button', { name: '检索', exact: true }).click()
  await page.getByRole('checkbox', { name: /getDashboard/ }).check()
  await expect(page.getByText('新增 1', { exact: true })).toBeVisible()
  await capture(page, info.outputPath('mapping-diff.png'))
  const mapPath = '/api/v1/systems/rbac-center/ui-api-mappings'
  await page.getByRole('button', { name: '保存映射' }).click()
  const mapped = response(page, mapPath, 'PUT')
  await page.getByRole('dialog').getByRole('button', { name: '确认' }).click()
  expect((await mapped).ok()).toBeTruthy()
  const coverage = await admin.get<CoverageVO>('/systems/rbac-center/ui-api-mappings')
  const operations = await admin.get<ResultPage<ApiOperationVO>>('/systems/rbac-center/api-operations?keyword=getDashboard&page=1&pageSize=20')
  expect(coverage.mappings).toContainEqual({ uiResourceId: menu.id, apiOperationId: operations.items.find(item => item.operationId === 'getDashboard')!.id })

  await nav(page, '鉴权解释')
  await page.getByLabel('用户 ID', { exact: true }).fill(String(user.id))
  await page.getByLabel('操作标识', { exact: true }).fill('getDashboard')
  await page.getByRole('button', { name: '解释来源' }).click()
  await expect(page.getByText('允许访问', { exact: true })).toBeVisible()
  await capture(page, info.outputPath('decision-allowed.png'))
  await page.getByLabel('操作标识', { exact: true }).fill('listSystems')
  await page.getByRole('button', { name: '检查权限' }).click()
  await expect(page.getByText('拒绝访问', { exact: true })).toBeVisible()
  await capture(page, info.outputPath('decision-denied.png'))

  const userToken = await login(request, user.username, password)
  const userApi = api(request, userToken)
  await browserLogout(page)
  await browserLogin(page, user.username, password, '/dashboard')
  await expect(page.getByLabel('管理目标系统')).toHaveCount(0)
  const scopedUsers = await userApi.get<ResultPage<UserVO>>('/users?page=1&pageSize=1')
  await expect(page.locator('.counts strong')).toHaveText(String(scopedUsers.total))
  await capture(page, info.outputPath('restricted-dashboard.png'))
  await admin.patch(`/ui-resources/${menu.id}`, {
    parentId: menu.parentId, type: menu.type, code: menu.code, name: menu.name, route: menu.route, component: menu.component, sortOrder: menu.sortOrder, permissionCode: menu.permissionCode, status: 'DISABLED',
  })
  await userApi.get('/dashboard')
  await page.getByRole('button', { name: '账号菜单' }).click()
  await page.getByText('刷新权限', { exact: true }).click()
  await expect(page).toHaveURL(/\/403$/)
  await page.goto('/dashboard')
  await expect(page).toHaveURL(/\/403$/)
  await expect(page.getByRole('heading', { name: '暂无访问权限' })).toBeVisible()
  await expect(page.getByRole('link', { name: '首页', exact: true })).toHaveCount(0)
  await capture(page, info.outputPath('forbidden.png'))
  await page.goto('/me')
  await page.getByRole('button', { name: '修改密码', exact: true }).click()
  const change = page.getByRole('dialog')
  const newPassword = password + 'Updated!2'
  await change.getByLabel('原密码').fill(password)
  await change.getByLabel('新密码').fill(newPassword)
  await change.getByRole('button', { name: '保存', exact: true }).click()
  await expect(page.getByText('密码已修改，请使用新密码登录。')).toBeVisible()
  await browserLogin(page, user.username, newPassword, '/me')
  await browserLogout(page)

  await page.goto('/users?userId=' + user.id + '&tab=assignments')
  await browserLogin(page, 'root', rootPassword, `/users\\?userId=${user.id}&tab=assignments`)
  await page.getByRole('button', { name: '撤销指派' }).click()
  const links = await admin.get<AssignmentVO[]>(`/users/${user.id}/role-assignments?systemCode=rbac-center`)
  const revoked = response(page, `/api/v1/users/${user.id}/role-assignments/${links[0].id}`, 'DELETE')
  await page.getByRole('dialog').getByRole('button', { name: '确认' }).click()
  const revocation = await revoked
  expect(revocation.status()).toBe(200)
  expect((await revocation.json()).code).toBe('OK')
  await expect(page.getByText('当前系统暂无直接角色指派')).toBeVisible()
  expect((await admin.get<PermissionVO[]>(`/roles/${d.id}/permissions`)).map(item => item.id)).toContain(permission.id)

  const current = await admin.get<UserVO>(`/users/${user.id}`)
  await admin.post(`/users/${user.id}/role-assignments`, { roleId: a.id })
  await admin.put(`/users/${user.id}/department`, { departmentId: y.id, version: current.version })
  expect(await admin.get<AssignmentVO[]>(`/users/${user.id}/role-assignments?systemCode=rbac-center`)).toEqual([])
  const courseToken = await login(request, 'root', rootPassword, 'course-project-management')
  expect([401, 403]).toContain((await request.get(`${env.api}/api/v1/users`, { headers: { Authorization: `Bearer ${courseToken}` } })).status())
  const logs = await admin.get<ResultPage<AuditVO>>('/audit-logs?systemCode=rbac-center&actorId=1&page=1&pageSize=100')
  const revokeLog = logs.items.find(row => row.type === 'ROLE_UNASSIGNED' && [String(user.id), user.subject].includes(row.target))
  expect(revokeLog).toBeTruthy()
  await nav(page, '操作审计')
  await page.getByLabel('目标', { exact: true }).fill(revokeLog!.target)
  await page.getByRole('button', { name: '查询审计', exact: true }).click()
  const auditRow = page.getByRole('row').filter({ hasText: revokeLog!.target }).filter({ hasText: 'ROLE_UNASSIGNED' }).first()
  await auditRow.getByRole('button', { name: '详情' }).click()
  await expect(page.getByText('字段变更', { exact: true })).toBeVisible()
  await capture(page, info.outputPath('audit-detail.png'))
  await page.goto('/does-not-exist')
  await expect(page.getByRole('heading', { name: '页面未找到' })).toBeVisible()
})

const visualRoutes = [
  ['dashboard', '/dashboard'], ['me', '/me'], ['users', '/users'], ['departments', '/departments'],
  ['roles', '/roles'], ['hierarchy', '/hierarchy'], ['systems', '/systems'], ['permissions', '/permissions'],
  ['ui-resources', '/ui-resources'], ['api-operations', '/api-operations'], ['mappings', '/mappings'],
  ['authorization', '/authorization'], ['audit', '/audit'], ['403', '/403'], ['404', '/visual-missing-page'],
] as const

test('桌面逐路由截图、窄窗口与宽表验证', async ({ browser }, info) => {
  test.setTimeout(360000)
  const request = strictHttp(), rootPassword = process.env.E2E_BOOTSTRAP_PASSWORD!
  const admin = api(request, await login(request, 'root', rootPassword))
  const fixture = await seed(admin, 'visual' + Date.now())
  await admin.post(`/users/${fixture.user.id}/role-assignments`, { roleId: fixture.a.id })
  const versions = env.baseline ? [['before', env.baseline], ['after', env.web]] : [['after', env.web]]

  for (const viewport of [{ width: 1366, height: 768 }, { width: 1440, height: 900 }]) {
    for (const [version, baseURL] of versions) {
      const context = await browser.newContext({ baseURL, viewport, timezoneId: 'Asia/Shanghai' })
      try {
        const page = await context.newPage()
        const output = (name: string) => info.outputPath(`${version}-${viewport.width}-${name}.png`)
        await page.goto('/login')
        await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
        await capture(page, output('login'))
        await page.getByLabel('账号', { exact: true }).fill('fictional_missing_account')
        await page.getByLabel('密码', { exact: true }).fill('Fictional!Invalid12')
        const invalid = response(page, '/auth/login', 'POST')
        await page.getByRole('button', { name: '登录', exact: true }).click()
        expect((await invalid).status()).toBe(401)
        await expect(page.getByRole('alert')).toBeVisible()
        await capture(page, output('login-failed'), true)
        await browserLogin(page, 'root', rootPassword, version === 'before' ? '/me' : '/dashboard')
        for (const [name, path] of visualRoutes) {
          // 原版的 Vite 代理也匹配了这两个页面路径。使用原版侧栏进入来保留可比的页面截图。
          if (version === 'before' && (name === 'api-operations' || name === 'authorization')) {
            await nav(page, name === 'api-operations' ? 'API 操作' : '鉴权解释')
          } else await page.goto(path)
          await expect(page.locator('main :is(h1, h2)').first()).toBeVisible()
          if (name === 'dashboard') await expect(page.locator('main .counts strong').first()).not.toHaveText('0')
          if (name === 'users') await expect(page.getByText(fixture.user.name, { exact: true })).toBeVisible()
          if (name === 'hierarchy') {
            if (version === 'after') await expect(page.locator('.dag-node').first()).toBeVisible()
            else await expect(page.getByRole('img', { name: /角色继承/ }).locator('rect').first()).toBeVisible()
          }
          await capture(page, output(name))
        }
        if (version === 'after') {
          await page.goto('/users')
          await expect(page.getByRole('heading', { name: '用户管理', exact: true })).toBeVisible()
          await page.keyboard.press('Tab')
          await expect(page.getByRole('link', { name: '跳到主要内容' })).toBeFocused()
          await page.goto(`/users?userId=${fixture.user.id}&tab=permissions`)
          await expect(page.getByText(fixture.permission.code, { exact: true })).toBeVisible()
          await capture(page, output('user-detail'))
          await page.goto('/users')
          await page.getByLabel('姓名 / 账号').fill('fictional_no_match')
          await page.getByRole('button', { name: '检索', exact: true }).click()
          await expect(page.getByText('未找到匹配的用户', { exact: true })).toBeVisible()
          await capture(page, output('empty'))
          await page.getByRole('button', { name: '收起侧栏' }).click()
          await page.locator('body').evaluate(body => { body.style.zoom = '125%' })
          await expect(page.getByLabel('姓名 / 账号')).toBeVisible()
          await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
          await page.locator('body').evaluate(body => { body.style.zoom = '' })
          await browserLogout(page)
        } else {
          await page.goto('/users')
          await page.getByRole('row').filter({ hasText: fixture.user.username }).getByRole('button', { name: '角色 / 有效权限' }).click()
          await expect(page.getByText(fixture.permission.code, { exact: true }).first()).toBeVisible()
          await page.getByText(fixture.permission.code, { exact: true }).first().scrollIntoViewIfNeeded()
          await capture(page, output('user-detail'))
          await page.getByRole('button', { name: '退出登录' }).click()
          await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
        }
        await browserLogin(page, fixture.user.username, fixture.password, version === 'before' ? '/me' : '/dashboard')
        if (version === 'before') await page.goto('/dashboard')
        await expect(page.locator('main .counts strong').first()).not.toHaveText('0')
        await capture(page, output('restricted-dashboard'), version === 'before')
      } finally { await context.close() }
    }
  }
  const wide = await browser.newContext({ baseURL: env.web, viewport: { width: 1920, height: 1080 }, timezoneId: 'Asia/Shanghai' })
  try {
    const page = await wide.newPage()
    await page.goto('/login')
    await browserLogin(page, 'root', rootPassword, '/dashboard')
    await nav(page, 'API 操作')
    await expect(page.getByRole('row').nth(1)).toBeVisible()
    await capture(page, info.outputPath('after-1920-api-operations.png'))
    await nav(page, '角色继承')
    await expect(page.locator('.dag-node').first()).toBeVisible()
    if (fixture.longRole) {
      const name = fixture.longRole.name
      await pick(page, page, '定位角色', new RegExp(name))
      await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
      await page.getByRole('button', { name: '适配视图' }).click()
    }
    await capture(page, info.outputPath('after-1920-hierarchy.png'))
  } finally { await wide.close() }
})
