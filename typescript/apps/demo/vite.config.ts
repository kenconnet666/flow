import { defineConfig, searchForWorkspaceRoot } from 'vite';
import { browserCompile, vueTsxPro, workspaceSource } from '@zerodep-dev/vue-tsx-pro-tsx/vite';

// 示例站点（演示与文档）：组件库、flow-ui、模型包都按源码读；/flow、/demo 转给示例后端（端口 8080：Spring Boot 的 examples/spring-boot）。
// 表单只有源码时在浏览器里编译，所以加上 browserCompile()
export default defineConfig({
  plugins: [workspaceSource(), ...vueTsxPro(), browserCompile()],
  resolve: { dedupe: ['vue', 'vue-router', '@internationalized/date', 'decimal.js', 'zod'] },
  // json-wire 从 npm 来，预构建时把它 import 的 decimal.js、@internationalized/date 与应用并成一份，instanceof 才对得上
  optimizeDeps: { include: ['@zerodep-dev/json-wire'] },
  server: {
    port: 5180,
    strictPort: true,
    proxy: { '/flow': 'http://localhost:8080', '/demo': 'http://localhost:8080' },
    // 重置演示发布的采购申请在仓库根的 spec/scenarios（与引擎的规格用例同一份），在 typescript 工作区之外
    fs: { allow: [searchForWorkspaceRoot(process.cwd()), '../../../spec'] },
  },
});
