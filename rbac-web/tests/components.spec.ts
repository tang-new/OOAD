import { it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { vPerm } from '@/directives/perm'
import { useAuthStore } from '@/stores/auth'
import { meetsPolicy } from '@/utils/password'
import RecordForm from '@/components/RecordForm.vue'
import RoleDag from '@/components/RoleDag.vue'
import type { HierarchyVO } from '@/api/types'
it('权限指令禁用、恢复、隐藏，处理子按钮及非按钮宿主', async () => {
  setActivePinia(createPinia()); const auth = useAuthStore()
  const wrapper = mount({ template: '<div><button v-perm="\'edit\'">edit</button><div v-perm="\'edit\'"><button>nested</button></div><span v-perm="\'edit\'">text</span><button v-perm.hide="\'hide\'">hide</button><button v-perm="\'other\'" disabled>native</button></div>' }, { global: { directives: { perm: vPerm } } })
  expect((wrapper.find('button').element as HTMLButtonElement).disabled).toBe(true)
  auth.identity = { subject: 'x', name: '', no: '', roles: ['admin'], permissions: ['edit', 'hide'] }; await wrapper.vm.$nextTick()
  wrapper.vm.$forceUpdate(); await wrapper.vm.$nextTick()
  expect((wrapper.find('button').element as HTMLButtonElement).disabled).toBe(false)
  expect(wrapper.findAll('button')[2].attributes('style')).not.toContain('display: none')
  // 初始已有权限时不覆盖原生 disabled。
  const native = mount({ template: '<button disabled v-perm="\'edit\'">native</button>' }, { global: { directives: { perm: vPerm } } }); expect((native.element as HTMLButtonElement).disabled).toBe(true)
})
it.each([null, undefined, '', 'Ab1!', 'abcdefgh', 'ABCDEFGH', 'Abcdefgh', 'Abc12345', 'Abcd123!'])('密码强度边界 %s', password => { expect(meetsPolicy(password)).toBe(password === 'Abcd123!') })
it('表单只发送字段白名单，number / checkbox 保持类型，密码不留存', async () => {
  const submit = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(RecordForm, { props: { title: '测试', fields: [{ key: 'name', label: '名称' }, { key: 'parentId', label: '上级', type: 'number' }, { key: 'enabled', label: '开启', type: 'checkbox' }, { key: 'password', label: '口令', type: 'password' }], initial: { name: 'x', enabled: false, secretNotAllowed: 'no' }, submit } })
  await wrapper.find('input[type=number]').setValue('12'); await wrapper.find('input[type=password]').setValue('fictional'); await wrapper.find('form').trigger('submit'); await vi.waitFor(() => expect(submit).toHaveBeenCalled())
  expect(submit).toHaveBeenCalledWith({ name: 'x', parentId: 12, enabled: false, password: 'fictional' })
})
it('DAG 多父边数真实呈现，非树复制', () => {
  const graph = { roles: [{ id: 1, name: 'A', status: 'ENABLED' }, { id: 2, name: 'B', status: 'DISABLED' }, { id: 3, name: 'C', status: 'ENABLED' }], edges: [{ seniorRoleId: 1, juniorRoleId: 3 }, { seniorRoleId: 2, juniorRoleId: 3 }] } as HierarchyVO
  const wrapper = mount(RoleDag, { props: { graph } }); expect(wrapper.findAll('rect')).toHaveLength(3); expect(wrapper.findAll('line')).toHaveLength(2)
})
