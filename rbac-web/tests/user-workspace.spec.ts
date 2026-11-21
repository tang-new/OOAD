import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import UserWorkspace from '@/views/UserWorkspace.vue'
import RecordForm from '@/components/RecordForm.vue'
import { useAuthStore } from '@/stores/auth'
import { get } from '@/api'
import type { DepartmentVO, UiResourceVO } from '@/api/types'
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
