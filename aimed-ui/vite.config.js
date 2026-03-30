import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBaseUrl = env.VITE_API_BASE_URL || 'http://localhost:8080'
  const devHost = env.VITE_DEV_HOST || '0.0.0.0'
  const devPort = Number(env.VITE_DEV_PORT || 5173)

  return {
    plugins: [
      vue()
    ],
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue'],
            elementPlus: ['element-plus', '@element-plus/icons-vue'],
            vendor: ['axios', 'uuid'],
          },
        },
      },
    },
    server: {
      host: devHost,
      port: devPort,
      strictPort: true,
      proxy: {
        '/api': {
          target: apiBaseUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''), // 去掉 /api 前缀
        },
      },
    },
    preview: {
      host: devHost,
      port: devPort,
      strictPort: true,
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  }
})
