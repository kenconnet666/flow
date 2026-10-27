import { defineConfig } from 'vite';
import { browserCompile, vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

// 示例前端：组件库、flow-ui、模型包都按源码读；/flow 转给示例后端（端口 8080：Spring Boot 的 examples/spring-boot）。
// 表单只有源码时在浏览器里编译，所以加上 browserCompile()
export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro(), browserCompile()],
  resolve: { dedupe: ['vue', 'vue-router', '@internationalized/date', 'decimal.js', 'zod'] },
  server: {
    port: 5180,
    strictPort: true,
    proxy: { '/flow': 'http://localhost:8080' },
  },
});
