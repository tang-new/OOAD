<script setup lang="ts">
import { computed } from 'vue'
import type { HierarchyVO } from '@/api/types'
const props = defineProps<{ graph: HierarchyVO }>()
// 每个角色只有一个节点；边单独渲染，因此多父与菱形不会变成重复的树节点。
const nodes = computed(() => props.graph.roles.map((role, index) => ({ ...role, x: 130 + (index % 3) * 270, y: 70 + Math.floor(index / 3) * 140 })))
function position(id: number) { return nodes.value.find(node => node.id === id) ?? { x: 0, y: 0 } }
</script>
<template><div class="dag-scroll"><svg role="img" aria-label="角色继承有向无环图，箭头由高级角色指向被继承角色" :viewBox="`0 0 850 ${Math.max(200, Math.ceil(nodes.length / 3) * 140)}`"><defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto"><path d="M 0 0 L 10 5 L 0 10 z" fill="#536e91" /></marker></defs><g v-for="edge in graph.edges" :key="`${edge.seniorRoleId}-${edge.juniorRoleId}`"><line :x1="position(edge.seniorRoleId).x" :y1="position(edge.seniorRoleId).y + 22" :x2="position(edge.juniorRoleId).x" :y2="position(edge.juniorRoleId).y - 25" stroke="#536e91" stroke-width="2" marker-end="url(#arrow)" /></g><g v-for="node in nodes" :key="node.id" :transform="`translate(${node.x},${node.y})`"><rect x="-110" y="-24" width="220" height="48" rx="8" :fill="node.status === 'ENABLED' ? '#e6f3ff' : '#eee'" stroke="#536e91" /><text text-anchor="middle" dy="5">{{ node.name }}</text><title>{{ node.code }} · {{ node.status }}</title></g></svg></div></template>
