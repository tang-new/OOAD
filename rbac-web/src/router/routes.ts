import type { UiResourceVO } from '@/api/types'

export const groups = ['组织与用户', '角色与授权', '系统与资源', '审计与排查'] as const
export const pages = [
  { path: '/dashboard', title: '首页', icon: 'home', view: () => import('@/views/DashboardView.vue') },
  { path: '/users', title: '用户管理', group: groups[0], icon: 'users', view: () => import('@/views/UserWorkspace.vue') },
  { path: '/departments', title: '部门准入', group: groups[0], icon: 'tree', view: () => import('@/views/DepartmentsView.vue') },
  { path: '/roles', title: '角色管理', group: groups[1], icon: 'key', view: () => import('@/views/RoleWorkspace.vue') },
  { path: '/hierarchy', title: '角色继承', group: groups[1], icon: 'graph', view: () => import('@/views/HierarchyView.vue') },
  { path: '/systems', title: '系统登记', group: groups[2], icon: 'systems', view: () => import('@/views/SystemsView.vue') },
  { path: '/permissions', title: '权限目录', group: groups[2], icon: 'shield', view: () => import('@/views/PermissionsView.vue') },
  { path: '/ui-resources', title: '菜单与按钮', group: groups[2], icon: 'menu', view: () => import('@/views/UiResourcesView.vue') },
  { path: '/api-operations', title: 'API 操作', group: groups[2], icon: 'code', view: () => import('@/views/ApiOperationsView.vue') },
  { path: '/mappings', title: 'UI ↔ API 映射', group: groups[2], icon: 'link', view: () => import('@/views/MappingsView.vue') },
  { path: '/authorization', title: '鉴权解释', group: groups[3], icon: 'search', view: () => import('@/views/AuthorizationView.vue') },
  { path: '/audit', title: '操作审计', group: groups[3], icon: 'audit', view: () => import('@/views/AuditView.vue') },
]
export const pageByPath = new Map(pages.map(page => [page.path, page]))

export function flattenMenus(nodes: UiResourceVO[]): UiResourceVO[] {
  return nodes.flatMap(node => [node, ...flattenMenus(node.children ?? [])])
}

export function enabledResources(nodes: UiResourceVO[]): UiResourceVO[] {
  return nodes.flatMap(node => node.status === 'ENABLED' ? [node, ...enabledResources(node.children ?? [])] : [])
}

export function visibleMenus(nodes: UiResourceVO[], has: (code: string) => boolean) {
  const visible = new Map<string, UiResourceVO>()
  for (const node of enabledResources(nodes)) {
    if (node.type === 'MENU' && node.route && pageByPath.has(node.route) && has(node.permissionCode) && !visible.has(node.route)) {
      visible.set(node.route, node)
    }
  }
  return [...visible.values()]
}

export function homePath(nodes: UiResourceVO[], has: (code: string) => boolean) {
  return visibleMenus(nodes, has).some(menu => menu.route === '/dashboard') ? '/dashboard' : '/me'
}
