#!/bin/sh
# 在开发机上组装示例站点的部署目录：deploy/demo/out/flow-demo-site（另打一个 .tar.gz 方便上传）。
# 服务器上不再编译（2G 内存放不下 Maven 与前端构建）：后端 jar、站点静态文件、种子脚本都在这里构建好，
# 上传后在那个目录里 `docker compose up -d --build` 只是把它们装进几个小镜像。
#
#   sh deploy/demo/build.sh
#
# 要求：JDK 17+；typescript/ 下 pnpm install 过；组件库 vue-tsx-pro 在本仓库的旁边并构建过编译器（同本地开发）。
set -eu
cd "$(dirname "$0")/../.."

# 预览工具起的进程里可能没有 JAVA_HOME：用 IDEA 装在 ~/.jdks 下的最新一个（同 .claude/run-example-backend.sh）
if [ ! -x "${JAVA_HOME:-/none}/bin/java" ] && [ ! -x "${JAVA_HOME:-/none}/bin/java.exe" ]; then
  JAVA_HOME="$(ls -d "$HOME"/.jdks/*/ 2>/dev/null | sort | tail -1)"
  export JAVA_HOME="${JAVA_HOME%/}"
fi

out=deploy/demo/out/flow-demo-site
rm -rf deploy/demo/out
mkdir -p "$out"
cp -r deploy/demo/site/. "$out/"

echo "== 后端"
./mvnw -B -q -DskipTests package -pl examples/spring-boot -am
cp examples/spring-boot/target/flow-example-spring-boot-*.jar "$out/backend/app.jar"

echo "== 站点"
pnpm -C typescript/apps/demo build
cp -r typescript/apps/demo/dist "$out/web/html"

echo "== 种子脚本"
# 打成一个 ESM 文件：依赖里有 CommonJS 的（@babel/parser），给它一个 require
(cd typescript/apps/demo && node_modules/.bin/esbuild seed/seed.ts --bundle --platform=node --format=esm --target=node24 \
  --banner:js="import { createRequire } from 'node:module'; const require = createRequire(import.meta.url);" \
  --outfile="../../../$out/seeder/seed.mjs" --log-level=warning)
mkdir -p "$out/seeder/files"
cp spec/scenarios/forms/purchase-form.json spec/scenarios/processes/purchase.json "$out/seeder/files/"
cp typescript/apps/demo/seed/leave-form.tsx typescript/apps/demo/seed/leave.json typescript/apps/demo/seed/ext-demo.json \
  typescript/apps/demo/seed/erp-push.json typescript/apps/demo/seed/tutorials.json "$out/seeder/files/"

tar -czf deploy/demo/out/flow-demo-site.tar.gz -C deploy/demo/out flow-demo-site
echo "== 好了：$out（$(du -sh "$out" | cut -f1)），压缩包 deploy/demo/out/flow-demo-site.tar.gz"
