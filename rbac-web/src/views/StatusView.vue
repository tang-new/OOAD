<script setup lang="ts">
import { computed } from 'vue'
import { NButton } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { homePath } from '@/router/routes'
const props = defineProps<{ status: 403 | 404 }>()
const auth = useAuthStore()
const destination = computed(() => homePath(auth.navigation))
</script>
<template>
  <section class="status-page">
    <span class="status-number">{{ status }}</span>
    <h1>{{ status === 403 ? '暂无访问权限' : '页面未找到' }}</h1>
    <p>{{ status === 403 ? '当前账号无法打开此页面。页面可能已停用，或你的访问权限已变更。' : '此地址对应的页面可能已移动。你可以从侧栏继续操作。' }}</p>
    <RouterLink :to="destination" custom v-slot="{ href, navigate }"><NButton tag="a" :href="href" type="primary" @click="navigate">{{ destination === '/me' ? '前往本人资料' : '返回工作台' }}</NButton></RouterLink>
  </section>
</template>
