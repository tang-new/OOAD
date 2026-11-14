<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserWorkspace from './UserWorkspace.vue'
import RoleWorkspace from './RoleWorkspace.vue'
import ResourceWorkspace from './ResourceWorkspace.vue'
import CatalogWorkspace from './CatalogWorkspace.vue'
const route = useRoute()
const view = computed(() => route.path === '/users' ? UserWorkspace : ['/roles', '/hierarchy'].includes(route.path) ? RoleWorkspace : ['/ui-resources', '/api-operations', '/mappings'].includes(route.path) ? ResourceWorkspace : CatalogWorkspace)
</script>
<template><h2>{{ $route.meta.title }}</h2><component :is="view" /></template>
