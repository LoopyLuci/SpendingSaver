import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { pwaPlugin } from './build/pwa-plugin'

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string }

export default defineConfig({
  // Relative base: the same build works at a domain root and in a sub-folder (GitHub Pages project sites).
  base: './',
  plugins: [react(), pwaPlugin(fileURLToPath(new URL('./build/sw.template.js', import.meta.url)))],
  define: { __APP_VERSION__: JSON.stringify(pkg.version) },
  build: { target: 'es2020', sourcemap: false, chunkSizeWarningLimit: 300 },
})
