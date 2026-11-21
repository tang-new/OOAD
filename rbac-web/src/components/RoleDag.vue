<script setup lang="ts">
import { computed, onScopeDispose, shallowRef, watch, ref } from 'vue'
import { BaseEdge, Handle, MarkerType, Position, VueFlow, useVueFlow, type Edge, type Node } from '@vue-flow/core'
import { NButton } from 'naive-ui'
import ELK from 'elkjs/lib/elk-api'
import type { ElkNode } from 'elkjs/lib/elk-api'
import elkWorkerUrl from 'elkjs/lib/elk-worker.min.js?url'
import '@vue-flow/core/dist/style.css'
import type { HierarchyVO, RoleVO } from '@/api/types'
import ReadState from './ReadState.vue'
import { edgeId } from '@/views/hierarchy'

const props = defineProps<{ graph: HierarchyVO; selected?: number | null; target?: number | null; activeEdges?: ReadonlySet<string>; filtered?: boolean }>()
const emit = defineEmits<{ select: [number] }>()
const width = 184, height = 56
const nodes = shallowRef<Node<RoleVO>[]>([]), edges = shallowRef<Edge<{ path: string; disabled: boolean }>[]>([])
const loading = ref(false), error = ref('')
const flow = useVueFlow()
const zoom = computed(() => Math.round(flow.viewport.value.zoom * 100))
let elk: InstanceType<typeof ELK> | undefined
let generation = 0
async function layout() {
  const revision = ++generation
  const { roles, edges: links } = props.graph
  if (!roles.length) {
    nodes.value = []; edges.value = []; loading.value = false; error.value = ''
    return
  }
  loading.value = true
  error.value = ''
  try {
    elk ??= new ELK({ workerFactory: () => new Worker(elkWorkerUrl) })
    const byId = new Map(roles.map(role => [String(role.id), role]))
    const linkById = new Map(links.filter(edge => byId.has(String(edge.seniorRoleId)) && byId.has(String(edge.juniorRoleId))).map(edge => [edgeId(edge.seniorRoleId, edge.juniorRoleId), edge]))
    const result = await elk.layout<ElkNode>({
      id: 'roles', layoutOptions: {
        'elk.algorithm': 'layered', 'elk.direction': 'RIGHT', 'elk.edgeRouting': 'ORTHOGONAL',
        'elk.spacing.nodeNode': '32', 'elk.layered.spacing.nodeNodeBetweenLayers': '76',
        'elk.layered.spacing.edgeNodeBetweenLayers': '18', 'elk.layered.spacing.edgeEdgeBetweenLayers': '12',
      },
      children: [...byId.keys()].map(id => ({ id, width, height, layoutOptions: { 'elk.portConstraints': 'FIXED_POS' }, ports: [
        { id: id + ':in', x: 0, y: height / 2, width: 0, height: 0, layoutOptions: { 'elk.port.side': 'WEST' } },
        { id: id + ':out', x: width, y: height / 2, width: 0, height: 0, layoutOptions: { 'elk.port.side': 'EAST' } },
      ] })),
      edges: [...linkById].map(([id, edge]) => ({ id, sources: [edge.seniorRoleId + ':out'], targets: [edge.juniorRoleId + ':in'] })),
    })
    if (revision !== generation) return
    nodes.value = (result.children ?? []).map(node => ({
      id: node.id, type: 'role', position: { x: node.x ?? 0, y: node.y ?? 0 },
      data: byId.get(node.id)!, style: { width: width + 'px', height: height + 'px' },
    }))
    edges.value = (result.edges ?? []).flatMap(edge => {
      const link = linkById.get(edge.id)!, section = edge.sections?.[0]
      if (!section) return []
      const source = String(link.seniorRoleId), target = String(link.juniorRoleId)
      const points = [section.startPoint, ...(section.bendPoints ?? []), section.endPoint]
      const path = points.map((point, index) => (index ? 'L' : 'M') + `${point.x},${point.y}`).join(' ')
      const disabled = byId.get(source)?.status !== 'ENABLED' || byId.get(target)?.status !== 'ENABLED'
      return [{ id: edge.id, type: 'hierarchy', source, target, data: { path, disabled }, selectable: false }]
    })
  } catch {
    if (revision === generation) {
      nodes.value = []; edges.value = []
      error.value = '关系图布局失败。你仍可使用下方关系列表。'
    }
  } finally { if (revision === generation) loading.value = false }
}
const markedEdges = computed(() => edges.value.map(edge => {
  const active = props.activeEdges?.has(edge.id)
  const color = active ? (edge.data?.disabled ? '#9c5a1c' : '#2157c6') : (props.filtered ? '#a9b5c6' : '#7f91ad')
  return {
    ...edge,
    markerEnd: { type: MarkerType.ArrowClosed, color, width: 15, height: 15 },
    style: { stroke: color, strokeWidth: active ? 3 : 1.7, strokeDasharray: edge.data?.disabled ? '5 4' : undefined },
    class: active ? 'path-active' : '',
  }
}))
const activeNodes = computed(() => new Set([...props.activeEdges ?? []].flatMap(edge => edge.split(':'))))
function fit() { void flow.fitView({ padding: .18, maxZoom: 1, duration: 180 }) }
function locate(id: number) {
  const node = nodes.value.find(item => item.id === String(id))
  if (node) void flow.setCenter(node.position.x + width / 2, node.position.y + height / 2, { duration: 180, zoom: Math.max(.8, flow.viewport.value.zoom) })
}
flow.onNodesInitialized(() => {
  if (props.selected) locate(props.selected)
  else fit()
})
watch(() => props.graph, layout, { immediate: true })
watch(() => props.activeEdges, active => {
  if (!props.target || !active?.size || loading.value) return
  const nodes = [...new Set([...active].flatMap(edge => edge.split(':')))]
  void flow.fitView({ nodes, padding: .24, maxZoom: 1, duration: 190 })
}, { flush: 'post' })
onScopeDispose(() => { generation++; elk?.terminateWorker() })
defineExpose({ locate })
</script>

<template>
  <div v-if="graph.roles.length" class="dag-region" role="region" aria-label="角色继承有向无环图">
    <ReadState v-if="loading || error" :loading="loading" :error="error" @retry="layout" />
    <div v-show="!loading && !error" class="dag-canvas">
      <VueFlow :nodes="nodes" :edges="markedEdges" :nodes-draggable="false" :nodes-connectable="false" :edges-focusable="false" :min-zoom="0.2" :max-zoom="2.2" :zoom-on-scroll="true" :pan-on-drag="true" :delete-key-code="null">
        <template #node-role="{ id, data }">
          <button class="dag-node" :class="{ 'is-selected': selected === Number(id), 'is-disabled': data.status === 'DISABLED', 'is-unrelated': filtered && selected !== Number(id) && !activeNodes.has(id) }" type="button" :aria-pressed="selected === Number(id)" :title="data.name + ' · ' + data.code" @click.stop="$emit('select', Number(id))">
            <Handle type="target" :position="Position.Left" :connectable="false" />
            <span class="dag-node-icon" aria-hidden="true">◇</span>
            <span><strong>{{ data.name }}</strong><small>{{ data.status === 'DISABLED' ? '已停用' : data.code }}</small></span>
            <Handle type="source" :position="Position.Right" :connectable="false" />
          </button>
        </template>
        <template #edge-hierarchy="{ id, data, markerEnd, style }"><BaseEdge :id="id" :path="data.path" :marker-end="markerEnd" :style="style" /></template>
      </VueFlow>
      <div class="view-controls" aria-label="画布缩放">
        <NButton size="small" aria-label="缩小" @click="flow.zoomOut()">−</NButton><span>{{ zoom }}%</span><NButton size="small" aria-label="放大" @click="flow.zoomIn()">+</NButton><NButton size="small" @click="fit">适配视图</NButton>
      </div>
    </div>
  </div>
  <ReadState v-else empty description="当前系统暂无角色" />
</template>
