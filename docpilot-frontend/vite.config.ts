import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/workspace': 'http://127.0.0.1:8080',
      '/document': 'http://127.0.0.1:8080'
    }
  }
})
