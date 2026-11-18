/// <reference types="vitest/config" />
import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import type { ClientRequest, IncomingMessage } from 'node:http'
import { defineConfig } from 'vite'

type ProxyRequestListener = (proxyRequest: ClientRequest, request: IncomingMessage) => void

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Development only: lets a temporary Cloudflare tunnel (https://….trycloudflare.com) reach the
    // dev server, to test Kitchen with a real AI client.
    allowedHosts: ['.trycloudflare.com'],
    // Same-origin in development, like in production (single domain, routed by path):
    // no CORS, the session cookie just works.
    // The API sees the browser's URL through X-Forwarded-* headers, so Spring builds its URLs
    // (OAuth issuer, Google redirect URI, passkey origin) on http://localhost:5173, or on the
    // tunnel's https URL, like behind Traefik in production. Set explicitly rather than with
    // `xfwd`, which adds the dev server's own port and protocol.
    proxy: Object.fromEntries(
      [
        '/api',
        '/actuator/health',
        '/oauth2',
        '/login',
        '/logout',
        '/webauthn',
        '/.well-known',
        '/mcp',
        '/exports',
      ].map((path) => [
        path,
        {
          target: 'http://localhost:8080',
          configure: (proxy: {
            on: (event: 'proxyReq', listener: ProxyRequestListener) => void
          }) => {
            proxy.on('proxyReq', (proxyRequest, request) => {
              const forwardedProto = request.headers['x-forwarded-proto']
              const proto = (Array.isArray(forwardedProto) ? forwardedProto[0] : forwardedProto)
                ?.split(',')[0]
                ?.trim()
              proxyRequest.setHeader('X-Forwarded-Host', request.headers.host ?? 'localhost:5173')
              proxyRequest.setHeader('X-Forwarded-Proto', proto || 'http')
              proxyRequest.removeHeader('X-Forwarded-Port')
            })
          },
        },
      ]),
    ),
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
