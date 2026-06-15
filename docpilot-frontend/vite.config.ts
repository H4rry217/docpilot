import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    proxy: {
      '/auth': 'http://127.0.0.1:11451',
      '/workspace': 'http://127.0.0.1:11451',
      '/document': 'http://127.0.0.1:11451',
      '/filesystem': 'http://127.0.0.1:11451',
      '/inline-completion': 'http://127.0.0.1:11451'
    }
  }
})
