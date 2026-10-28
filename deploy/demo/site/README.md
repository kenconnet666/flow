# 流程引擎示例站点：部署

这个目录就是部署所需的全部：后端 jar、站点静态文件、种子脚本都已构建好，服务器上不再编译。

## 第一次部署

服务器需要 Docker（带 compose 插件）。把整个目录上传到服务器，例如 `/root/flow-demo-site`，然后：

```bash
cd /root/flow-demo-site
docker compose up -d --build
```

一两分钟后打开 https://america.zerodep.dpdns.org 。第一次启动会：

- 随机生成数据库密码，存在 Docker 卷里，不用写任何密码；
- 建表，并发布演示数据（"教程做完"那一档：六个教程的流程、几单在办、一条异常）。

证书默认从 `/root/cert/america.zerodep.dpdns.org/` 读 `fullchain.pem` 与 `privkey.pem`。域名、证书目录、每天重置的时间可以在本目录建一个 `.env` 改：

```bash
FLOW_DEMO_DOMAIN=america.zerodep.dpdns.org
FLOW_DEMO_CERT_DIR=/root/cert/america.zerodep.dpdns.org
FLOW_DEMO_RESET_CRON=0 20 * * *
```

`FLOW_DEMO_RESET_CRON` 是 UTC 时间的 cron，默认每天 20:00 UTC，即北京时间凌晨 4 点，重置成"教程做完"那一档。

## 内存

按 2G 内存的服务器压过，各服务上限合计约 950M：

| 服务 | 上限 | 说明 |
| --- | --- | --- |
| mysql | 384M | 缓冲池 64M，关了 performance_schema |
| backend | 448M | 堆最多约 250M，串行 GC，只做 C1 编译 |
| web | 48M | nginx |
| seeder | 96M | 发布演示数据、定时重置 |

## 日常

```bash
docker compose ps                 # 看状态（mysql、backend 要是 healthy）
docker compose logs -f backend    # 后端日志
docker compose logs seeder        # 演示数据的发布与定时重置
docker compose restart web        # 换了证书后重新加载
```

- **更新版本**：在开发机重新组装，上传覆盖本目录，再执行 `docker compose up -d --build`。数据库保留；演示数据由页面上的"重置演示"或每天的定时重置刷新。
- **全部推倒重来**：`docker compose down -v` 会连数据库和生成的密码一起删掉，下次 `up` 从空库开始。
