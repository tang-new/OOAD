<script setup lang="ts">
import { NConfigProvider, NMessageProvider, dateZhCN, zhCN, type GlobalThemeOverrides } from 'naive-ui'
const theme: GlobalThemeOverrides = {
  common: {
    primaryColor: '#2157c6',
    primaryColorHover: '#194bb1',
    primaryColorPressed: '#133d92',
    primaryColorSuppl: '#2157c6',
    textColorBase: '#202c40',
    borderColor: '#dce1e8',
    borderRadius: '7px',
    fontFamily: '"Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC", sans-serif',
  },
  DataTable: { thColor: '#f4f6f9', tdColorHover: '#f7f9fd', borderColor: '#e9edf2' },
  Card: { borderColor: '#e2e7ee', paddingMedium: '22px' },
}
</script>

<template>
  <NConfigProvider :theme-overrides="theme" :locale="zhCN" :date-locale="dateZhCN">
    <NMessageProvider><RouterView /></NMessageProvider>
  </NConfigProvider>
</template>
