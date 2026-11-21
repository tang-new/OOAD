import type { HierarchyVO } from '@/api/types'

export const edgeId = (senior: number, junior: number) => `${senior}:${junior}`
export const PATH_LIMIT = 50

export function inheritancePaths(graph: HierarchyVO, start: number, end: number) {
  const roles = new Map(graph.roles.map(role => [role.id, role]))
  const outgoing = new Map<number, number[]>(), incoming = new Map<number, number[]>()
  for (const edge of graph.edges) {
    const a = edge.seniorRoleId, b = edge.juniorRoleId
    if (!roles.has(a) || !roles.has(b)) continue
    outgoing.set(a, [...(outgoing.get(a) ?? []), b])
    incoming.set(b, [...(incoming.get(b) ?? []), a])
  }
  function reachable(from: number, neighbors: Map<number, number[]>) {
    const visited = new Set<number>(), pending = [from]
    while (pending.length) {
      const next = pending.pop()!
      if (visited.has(next)) continue
      visited.add(next)
      pending.push(...(neighbors.get(next) ?? []))
    }
    return visited
  }
  const forward = reachable(start, outgoing), backward = reachable(end, incoming)
  const everyEdge = new Set(graph.edges.filter(edge => forward.has(edge.seniorRoleId) && backward.has(edge.juniorRoleId)).map(edge => edgeId(edge.seniorRoleId, edge.juniorRoleId)))
  const paths: number[][] = []
  // 避免为有大量路径的图生成指数数量的数组。全部路径的边仍由两次可达性遍历完整高亮。
  if (start !== end && backward.has(start)) {
    const path = [start], active = new Set(path)
    const stack = [(outgoing.get(start) ?? []).values()]
    while (stack.length && paths.length <= PATH_LIMIT) {
      const next = stack.at(-1)!.next()
      if (next.done) {
        stack.pop()
        active.delete(path.pop()!)
        continue
      }
      const node = next.value
      if (!backward.has(node) || active.has(node)) continue
      if (node === end) paths.push([...path, node])
      else {
        path.push(node)
        active.add(node)
        stack.push((outgoing.get(node) ?? []).values())
      }
    }
  }
  return {
    everyEdge, truncated: paths.length > PATH_LIMIT,
    paths: paths.slice(0, PATH_LIMIT).map(nodes => ({
      nodes,
      effective: nodes.every(id => roles.get(id)?.status === 'ENABLED'),
      edges: new Set(nodes.slice(1).map((id, index) => edgeId(nodes[index], id))),
    })),
  }
}
