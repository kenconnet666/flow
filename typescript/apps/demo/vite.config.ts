import { defineConfig } from 'vite';
import { browserCompile, vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

// 示例前端：组件库、flow-ui、模型包都按源码读；/flow 转给示例后端（端口 8080：Spring Boot 的 examples/spring-boot）。
// 表单只有源码时在浏览器里编译，所以加上 browserCompile()
export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro(), browserCompile()],
  resolve: { dedupe: ['vue', 'vue-router', '@internationalized/date', 'decimal.js', 'zod'] },
  // json-wire 是 link: 进来的（工程外），不预构建时它 import 的 decimal.js、@internationalized/date 按它自己的目录解析成另一份，
  // instanceof 对不上：一起预构建，与应用共用一份
  optimizeDeps: { include: ['@zerodep-dev/json-wire'] },
  server: {
    port: 5180,
    strictPort: true,
    proxy: { '/flow': 'http://localhost:8080', '/demo': 'http://localhost:8080' },
  },
});
