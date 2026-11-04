import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const apiTarget = process.env.API_PROXY_TARGET || "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  define: {
    global: "window",
  },
  server: {
    port: 3000,
    proxy: {
      "/api": {
        target: apiTarget,
        changeOrigin: true,
      },
      "/ws": {
        target: apiTarget,
        ws: true,
        changeOrigin: true,
      },
    },
  },
});
