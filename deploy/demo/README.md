# 示例站点的部署

把 `typescript/apps/demo`（文档与演示）连同示例后端（`examples/spring-boot`）、MySQL 部署到一台服务器上。

服务器内存小（2G），放不下 Maven 与前端构建，所以构建都在开发机上做，服务器上只是把构建好的文件装进几个小镜像。

```bash
sh deploy/demo/build.sh
```

它会组装出 `deploy/demo/out/flow-demo-site/` 与压缩包 `deploy/demo/out/flow-demo-site.tar.gz`。把这个目录上传到服务器，在里面执行 `docker compose up -d --build` 就部署好了。上传后的步骤、内存与日常维护见 [site/README.md](site/README.md)，它会随目录一起上传。

| 目录 | 内容 |
| --- | --- |
| `site/` | 上传到服务器的配置：`compose.yaml`，以及 backend、web、seeder 三个镜像的 Dockerfile、nginx 配置、定时重置脚本 |
| `build.sh` | 构建后端 jar、站点静态文件，把种子脚本打成一个文件，与 `site/` 一起组装进 `out/` |
| `out/` | 组装结果，不进仓库 |
