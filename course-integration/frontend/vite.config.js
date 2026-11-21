import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), 'VITE_')
    const center = {
        target: env.VITE_RBAC_PROXY_TARGET || 'http://127.0.0.1:8083',
        changeOrigin: true,
    }

    return {
        base: '/course/',
        server: {
            proxy: {
                '^/api/v1(?:/|\\?|$)': { ...center, bypass: () => false },
                '/login': {
                    target: env.VITE_COURSE_PROXY_TARGET || 'http://127.0.0.1:8084',
                    changeOrigin: true,
                    configure: (proxy) => proxy.on('proxyReq', (request) => request.removeHeader('authorization')),
                },
                '/api': center,
                '/auth': center,
            },
        },
    }
})
