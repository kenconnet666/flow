import { defineConfig } from 'vite';
import { vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

// 文档站：组件库按源码读（改了组件立刻热更新）；纯静态页面，不连后端
export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro()],
  resolve: { dedupe: ['vue', 'vue-router'] },
  server: { port: 5190, strictPort: true },
});
