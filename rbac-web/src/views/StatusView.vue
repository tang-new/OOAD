<script setup lang="ts">defineProps<{ status: number }>()</script>
<template><h2>{{ status }} · {{ status === 403 ? '没有访问权限' : '页面不存在' }}</h2><p>{{ status === 403 ? '当前身份缺少该菜单或操作的权限，请联系管理员。后端仍会独立校验每次请求。' : '请检查地址或从导航进入。' }}</p><RouterLink to="/me">返回本人资料</RouterLink></template>
