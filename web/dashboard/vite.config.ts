import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dashboard runs on 3000 and proxies /api to the query-api on 8087.
// This way the UI code just calls fetch('/api/v1/...') — no environment
// variables, no CORS surprises in dev.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8087',
        changeOrigin: true,
      },
    },
  },
});
