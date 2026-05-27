import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://127.0.0.1:11451',
      '/workspace': 'http://127.0.0.1:11451',
      '/document': 'http://127.0.0.1:11451'
    }
  }
})
