<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { NButton } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { homePath, managementRetryPath } from '@/router/routes'
const props = defineProps<{ status: 403 | 404 | 'refresh-failed' }>()
const auth = useAuthStore()
const route = useRoute()
const messages = {
  403: { title: '暂无访问权限', detail: '当前账号无法打开此页面。页面可能已停用，或你的访问权限已变更。' },
  404: { title: '页面未找到', detail: '此地址对应的页面可能已移动。你可以从侧栏继续操作。' },
  'refresh-failed': { title: '权限刷新失败', detail: '暂时无法确认当前权限，请重试。' },
}
const retry = computed(() => props.status === 'refresh-failed' ? managementRetryPath(route.query.redirect) : undefined)
const destination = computed(() => retry.value ?? (props.status === 'refresh-failed' ? '/me' : homePath(auth.navigation)))
</script>
<template>
  <section class="status-page">
    <span v-if="status !== 'refresh-failed'" class="status-number">{{ status }}</span>
    <h1>{{ messages[status].title }}</h1>
    <p>{{ messages[status].detail }}</p>
    <RouterLink :to="destination" :replace="!!retry" custom v-slot="{ href, navigate }"><NButton tag="a" :href="href" type="primary" @click="navigate">{{ retry ? '重试' : destination === '/me' ? '前往本人资料' : '返回工作台' }}</NButton></RouterLink>
  </section>
</template>
