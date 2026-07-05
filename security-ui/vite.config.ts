import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The e2e harness runs this on 4200 (the JVM stack keeps 8080/8083); a human dev server too.
export default defineConfig({
  plugins: [react()],
  server: { port: 4200 },
});
