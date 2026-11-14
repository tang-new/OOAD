import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import UserWorkspace from '@/views/UserWorkspace.vue'
import { useAuthStore } from '@/stores/auth'
import { get } from '@/api'
import type { DepartmentVO } from '@/api/types'
vi.mock('@/api', () => ({ get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn(), del: vi.fn() }))
it('准入选项尚未返回时禁止打开快照表单，返回后保留部门真实选项与稳定可访问名称', async () => {
  setActivePinia(createPinia()); const auth = useAuthStore()
  auth.identity = { subject: 'fictional', name: '', no: '', roles: ['admin'], permissions: ['rbac:ui:createUser'] }
  let resolveDepartments!: (value: DepartmentVO[]) => void
  const pending = new Promise<DepartmentVO[]>(resolve => { resolveDepartments = resolve })
  vi.mocked(get).mockImplementation(async url => url === '/departments/tree' ? pending : { page: 1, pageSize: 20, total: 0, items: [] })
  const wrapper = mount(UserWorkspace); await flushPromises()
  const create = wrapper.findAll('button').find(b => b.text() === '创建用户')!
  expect((create.element as HTMLButtonElement).disabled).toBe(true)
  resolveDepartments([{ id: 77, name: '虚构部门', code: 'TEST', parentId: null, children: [], path: '/77/', status: 'ENABLED' }]); await flushPromises()
  expect((create.element as HTMLButtonElement).disabled).toBe(false)
  await create.trigger('click')
  expect(wrapper.find('select[aria-label="所属部门"] option[value="77"]').text()).toBe('虚构部门')
  wrapper.unmount()
})
