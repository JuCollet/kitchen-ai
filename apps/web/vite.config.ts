/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Same-origin in development, like in production (single domain, routed by path):
    // no CORS, the session cookie just works.
    // The API sees the browser's host through X-Forwarded-* headers (xfwd), so Spring builds
    // its URLs (Google redirect URI, passkey origin) on http://localhost:5173, like behind
    // Traefik in production.
    proxy: Object.fromEntries(
      ['/api', '/actuator/health', '/oauth2', '/login', '/logout', '/webauthn'].map((path) => [
        path,
        { target: 'http://localhost:8080', xfwd: true },
      ]),
    ),
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
