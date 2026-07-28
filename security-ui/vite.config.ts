/// <reference types="vitest/config" />
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// The e2e harness runs this on 4200 (the JVM stack keeps 8080/8083); a human dev server too.
export default defineConfig({
  plugins: [react()],
  server: { port: 4200 },
  test: {
    // The unit suite exists for what e2e/ cannot hold still. This module is pure decision-making —
    // "what does this status mean to a person?" — and every one of its rules was previously an
    // unwritten `else` that told users their password was wrong when the database was down.
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['e2e/**', 'node_modules/**'],
  },
});
