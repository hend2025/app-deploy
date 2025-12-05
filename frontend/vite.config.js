import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/deploy',
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: undefined
      }
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/deploy/appMgt': {
        target: 'http://127.0.0.1:7080',
        changeOrigin: true
      },
      '/deploy/verBuild': {
        target: 'http://127.0.0.1:7080',
        changeOrigin: true
      },
      '/deploy/logs': {
        target: 'http://127.0.0.1:7080',
        changeOrigin: true
      }
    }
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  }
})

