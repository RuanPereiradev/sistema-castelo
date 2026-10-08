import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The backend runs from IntelliJ on 8080, not in a container, so the dev server
 * proxies `/api` there. Same-origin in dev means no CORS configuration in the
 * Spring side and no absolute URL baked into the bundle.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // The menu the waiter orders from is the public one, outside `/api`.
      '/public': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
