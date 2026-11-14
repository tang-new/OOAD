import type { UiResourceVO } from '@/api/types'
export const pages = [
  { path: '/dashboard', title: '首页', component: 'Dashboard', permission: 'rbac:ui:dashboard:view' },
  { path: '/users', title: '用户管理', component: 'Users', permission: 'rbac:ui:users:view' },
  { path: '/departments', title: '部门准入', component: 'Departments', permission: 'rbac:ui:departments:view' },
  { path: '/systems', title: '系统登记', component: 'Systems', permission: 'rbac:ui:systems:view' },
  { path: '/roles', title: '角色与继承', component: 'Roles', permission: 'rbac:ui:roles:view' },
  { path: '/permissions', title: '权限配置', component: 'Permissions', permission: 'rbac:ui:permissions:view' },
  { path: '/hierarchy', title: '角色继承 DAG', component: 'Hierarchy', permission: 'rbac:ui:hierarchy:view' },
  { path: '/ui-resources', title: '菜单按钮', component: 'UiResources', permission: 'rbac:ui:ui-resources:view' },
  { path: '/api-operations', title: 'API 操作', component: 'ApiOperations', permission: 'rbac:ui:api-operations:view' },
  { path: '/mappings', title: 'UI ↔ API 映射', component: 'Mappings', permission: 'rbac:ui:mappings:view' },
  { path: '/authorization', title: '鉴权解释', component: 'Authorization', permission: 'rbac:ui:authorization:view' },
  { path: '/audit', title: '操作审计', component: 'AuditLogs', permission: 'rbac:ui:audit:view' },
]
export function flattenMenus(nodes: UiResourceVO[]): UiResourceVO[] { return nodes.flatMap(node => [node, ...flattenMenus(node.children ?? [])]) }
export function visibleMenus(nodes: UiResourceVO[], has: (code: string) => boolean) { return flattenMenus(nodes).filter(node => node.type === 'MENU' && node.status === 'ENABLED' && node.route && has(node.permissionCode)) }
export function homePath(nodes: UiResourceVO[]) { return flattenMenus(nodes).find(node => node.type === 'MENU' && node.route)?.route ?? '/me' }
