import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import './styles.css'
import { createAppRouter } from './router'
import { setAuthHooks } from './api/http'
import { useAuthStore } from './stores/auth'
import { vPerm } from './directives/perm'
const app = createApp(App)
app.use(createPinia())
const router = createAppRouter()
const auth = useAuthStore()
setAuthHooks({
  getToken: () => auth.token,
  onUnauthenticated: () => {
    auth.clear()
    const route = router.currentRoute.value
    if (route.path !== '/login') void router.replace({ path: '/login', query: { redirect: route.fullPath } })
  },
  onForbidden: () => {
    auth.loadedAt = 0
    if (auth.token) void auth.refresh().catch(() => undefined)
  },
})
app.use(router)
app.directive('perm', vPerm)
app.mount('#app')
