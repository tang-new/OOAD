import { effectScope } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { errorText, sameIds, useRead, useWorkspace } from '@/views/workspace'

beforeEach(() => setActivePinia(createPinia()))

it('替换读取范围后丢弃旧响应，并保留独立的读取错误', async () => {
  const scope = effectScope()
  const state = scope.run(() => useRead(() => ''))!
  let complete!: (value: string) => void
  const first = state.read(() => new Promise(resolve => { complete = resolve }))
  state.reset()
  await state.read(async () => '新系统')
  complete('旧系统')
  await first
  expect(state.data.value).toBe('新系统')
  expect(await state.read(async () => { throw new Error('目录不可用') })).toBe(false)
  expect(state.error.value).toBe('目录不可用')
  expect(state.data.value).toBe('新系统')
  scope.stop()
})

it('写入确认后刷新失败只重试读取，不会再次提交写入', async () => {
  const scope = effectScope()
  const auth = useAuthStore()
  const refresh = vi.spyOn(auth, 'refresh').mockRejectedValueOnce(new Error('离线')).mockResolvedValue(undefined)
  const reload = vi.fn().mockResolvedValue(true)
  const editor = scope.run(() => useWorkspace(reload))!
  const write = vi.fn().mockResolvedValue(undefined)
  await editor.mutate(write)
  await vi.waitFor(() => expect(editor.stale.value).toBe(true))
  expect(editor.message.value).toContain('已保存')
  await editor.refresh()
  expect(editor.stale.value).toBe(false)
  expect(write).toHaveBeenCalledTimes(1)
  expect(reload).toHaveBeenCalledTimes(2)
  expect(refresh).toHaveBeenCalledTimes(2)
  expect(refresh).toHaveBeenNthCalledWith(1, true)
  expect(refresh).toHaveBeenNthCalledWith(2, false)
  scope.stop()
})

it('全量替换确认按 ID 集合对比，循环错误保留服务端业务码', () => {
  expect(sameIds([1, 2], [2, 1])).toBe(true)
  expect(sameIds([1, 2], [1, 3])).toBe(false)
  expect(sameIds([1], [1, 3])).toBe(false)
  expect(errorText(new ApiError(409, 'ROLE_CYCLE', '操作冲突'))).toContain('形成循环')
})
