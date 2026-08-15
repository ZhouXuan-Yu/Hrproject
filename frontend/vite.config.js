import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue(), react()],
  server: {
    host: '127.0.0.1',
    port: 7100,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/confirm': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
});
