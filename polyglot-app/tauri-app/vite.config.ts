import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Remove crossorigin attributes from HTML tags (breaks file:// protocol in Tauri)
function removeCrossorigin() {
  return {
    name: 'remove-crossorigin',
    enforce: 'post',
    transformIndexHtml(html) {
      return html
        .replace(/ crossorigin/g, '')
    }
  }
}

export default defineConfig({
  plugins: [react(), removeCrossorigin()],
  base: './',
  clearScreen: false,
  server: {
    port: 5173,
    strictPort: true,
    host: true,
  },
  build: {
    target: 'es2020',
    minify: 'esbuild',
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
          parsers: ['papaparse'],
        },
      },
    },
    chunkSizeWarningLimit: 500,
  },
  optimizeDeps: {
    include: ['react', 'react-dom', 'react-router-dom', 'recharts', 'papaparse'],
  },
})
