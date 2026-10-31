import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
// https://vite.dev/config/
export default defineConfig({
  plugins: [react({ jsxRuntime: "automatic" })],
  // Safety net: even if the plugin is skipped, compile JSX without needing `import React`.
  esbuild: { jsx: "automatic" },
  server: {
    port: 5173,
    strictPort: false,
  },
});
