/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://localhost:7080' } },
  worker: { format: 'es' },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
    // Các test dựng component Ant Design (Drawer/Select/Form) trong jsdom rất nặng: một thao tác
    // userEvent có thể mất >1s, nên mặc định 5s của Vitest không đủ (trên vite5/vitest2 vài test đã mất
    // 3,4–3,9s, sát ngưỡng; sau khi lên vite8/vitest5 chúng vượt 5s, nên nâng timeout lên 30s).
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
})
