import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import cesium from 'vite-plugin-cesium';

export default defineConfig({
  plugins: [react(), cesium()],
  server: {
    // Calls to /api go to the Spring Boot backend while developing.
    proxy: { '/api': 'http://localhost:8080' },
  },
});
