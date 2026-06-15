import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { defineConfig } from 'vite'

const backendTarget = 'http://127.0.0.1:11451'

export default defineConfig({
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
})
