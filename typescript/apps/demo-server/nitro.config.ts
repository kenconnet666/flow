import { defineNitroConfig } from 'nitropack/config';

// 示例后端：接口在 server/routes/flow，启动时建表、定时做作业在 server/plugins/flow.ts。Nuxt 应用里同样的文件放在 server/ 下
export default defineNitroConfig({
  srcDir: 'server',
  compatibilityDate: '2026-10-01',
  // 编号是 bigint（1001n 这样的字面量），esbuild 默认的 es2019 不认
  esbuild: { options: { target: 'es2022' } },
});
