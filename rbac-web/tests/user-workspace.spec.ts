import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import UserWorkspace from '@/views/UserWorkspace.vue'
import RoleWorkspace from '@/views/RoleWorkspace.vue'
import RecordForm from '@/components/RecordForm.vue'
import RowActions from '@/components/RowActions.vue'
import { useAuthStore } from '@/stores/auth'
import { get } from '@/api'
import type { DepartmentVO, EffectiveVO, RoleVO, UiResourceVO, UserVO } from '@/api/types'
vi.mock('@/api', () => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn(), del: vi.fn() }))

it('部门选项加载前禁止打开创建表单，返回后使用真实部门树', async () => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.identity = { subject: 'fictional', name: '', no: '', roles: ['admin'], permissions: ['rbac:ui:createUser'] }
  auth.menus = [{ id: 3, type: 'BUTTON', status: 'ENABLED', permissionCode: 'rbac:ui:createUser', children: [] }] as unknown as UiResourceVO[]
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/users', component: UserWorkspace }] })
  await router.push('/users')
  let resolveDepartments!: (value: DepartmentVO[]) => void
  const pending = new Promise<DepartmentVO[]>(resolve => { resolveDepartments = resolve })
  vi.mocked(get).mockImplementation(async url => url === '/departments/tree' ? pending : { page: 1, pageSize: 20, total: 0, items: [] })
  const wrapper = mount(UserWorkspace, { global: { plugins: [router] } })
  await flushPromises()
  const create = wrapper.findAll('button').find(button => button.text() === '创建用户')!
  expect((create.element as HTMLButtonElement).disabled).toBe(true)
  resolveDepartments([{ id: 77, name: '虚构部门', code: 'TEST', parentId: null, children: [], path: '/77/', status: 'ENABLED' }])
  await flushPromises()
  expect((create.element as HTMLButtonElement).disabled).toBe(false)
  await create.trigger('click')
  expect(wrapper.getComponent(RecordForm).props('fields').find(field => field.key === 'departmentId')?.tree).toEqual([{ key: 77, label: '虚构部门', children: [] }])
  wrapper.unmount()
})

const user: UserVO = {
  id: 7, subject: 'fictional', username: 'demo', name: '测试用户', no: null, phone: null,
  departmentId: 1, departmentName: '总部', status: 'ACTIVE', version: 1,
}
const role: RoleVO = {
  id: 7, systemCode: 'rbac-center', code: 'demo', name: '测试角色', description: null,
  status: 'ENABLED', builtin: false, identityRole: null, version: 1,
}

it.each([
  {
    title: '用户详情',
    component: UserWorkspace,
    path: '/users',
    url: '/users?userId=7&tab=permissions',
    profile: user,
    canEdit: 'updateUser',
    effectivePath: '/users/7/systems/rbac-center/permissions',
    rejectProfile: false,
  },
  {
    title: '角色详情',
    component: RoleWorkspace,
    path: '/roles',
    url: '/roles?roleId=7&tab=effective',
    profile: role,
    canEdit: 'updateRole',
    effectivePath: '/roles/7/effective-permissions',
    rejectProfile: true,
  },
])('$title刷新失败时隐藏原有权限，重试成功后展示新权限', async ({
  component, path, url, profile, canEdit, effectivePath, rejectProfile,
}) => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const editPermission = `rbac:ui:${canEdit}`
  auth.identity = { subject: 'fictional', name: '', no: '', roles: ['admin'], permissions: [editPermission] }
  auth.menus = [{ id: 3, type: 'BUTTON', status: 'ENABLED', permissionCode: editPermission, children: [] }] as unknown as UiResourceVO[]
  vi.spyOn(auth, 'refresh').mockResolvedValue(undefined)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component }] })
  await router.push(url)
  const profilePath = `${path}/7`
  let rejected = false
  let grant = '旧权限'
  vi.mocked(get).mockImplementation(async endpoint => {
    if (rejected && endpoint === (rejectProfile ? profilePath : effectivePath)) throw new Error('暂时无法读取权限资料')
    if (endpoint === profilePath) return profile
    if (endpoint === effectivePath) {
      return {
        directRoles: [], effectiveRoles: [], sources: [],
        permissions: [{ id: 1, systemCode: 'rbac-center', code: 'demo:grant', name: grant, kind: 'API', status: 'ENABLED' }],
      } satisfies EffectiveVO
    }
    if (endpoint === path) return { page: 1, pageSize: 20, total: 0, items: [] }
    return []
  })
  const wrapper = mount(component, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.text()).toContain('旧权限')

  rejected = true
  wrapper.getComponent(RowActions).vm.$emit('select', 'edit')
  await flushPromises()
  await wrapper.getComponent(RecordForm).props('submit')({ name: '修改后的资料' })
  await flushPromises()
  const alert = wrapper.get('[role="alert"]')
  expect(alert.text()).toContain('暂时无法读取权限资料')
  expect(wrapper.text()).not.toContain('旧权限')

  rejected = false
  grant = '当前权限'
  await alert.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('当前权限')
  expect(wrapper.text()).not.toContain('暂时无法读取权限资料')
  wrapper.unmount()
})
