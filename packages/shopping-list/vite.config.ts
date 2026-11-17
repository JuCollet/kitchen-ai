/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { viteSingleFile } from 'vite-plugin-singlefile'

// An MCP App is served as one HTML resource: the host renders it in a sandboxed iframe that may
// not load anything else, so scripts and styles are inlined into dist/shopping-list.html. The
// Maven build copies that file onto the API classpath (apps/api/pom.xml).
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    rollupOptions: { input: 'shopping-list.html' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
})
