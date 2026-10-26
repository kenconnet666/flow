import { defineConfig } from 'vitest/config';
import { vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

// 组件库与模型包按源码读（@vue-tsx-pro/source 条件）；组件库在旁边的仓库里，Vue 只留一份
export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro()],
  resolve: { dedupe: ['vue', '@internationalized/date', 'decimal.js', 'zod'] },
  test: {
    name: 'flow-ui',
    environment: 'happy-dom',
  },
});
