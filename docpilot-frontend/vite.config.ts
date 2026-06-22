import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { defineConfig, loadEnv } from 'vite'

const defaultBackendTarget = 'http://127.0.0.1:11451'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_DEV_PROXY_TARGET || defaultBackendTarget

  return {
    base: './',
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src')
      }
    },
    server: {
      proxy: {
        // Frontend calls /api/*; the backend controllers are still mounted at /*.
        '/api': {
          target: backendTarget,
          changeOrigin: true,
          rewrite: (requestPath) => requestPath.replace(/^\/api(?=\/|$)/, '')
        }
      }
    }
  }
})
