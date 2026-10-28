#!/bin/sh
# 把示例后端的库 flow_demo 清空重建（只剩空库）：之后重启示例后端由 Flyway 建表，再 `pnpm -C typescript/apps/demo seed` 发布种子流程。
# 文档站截图前用它，截出来的是干净的数据。root 密码从本目录已忽略的 .env 读，不写在这里。
set -eu
cd "$(dirname "$0")"
. ./.env
wsl -d Debian --exec docker exec -e MYSQL_PWD="$FLOW_DB_PASSWORD" flow-mysql-mysql-1 \
  mysql -uroot -e 'DROP DATABASE IF EXISTS flow_demo; CREATE DATABASE flow_demo; GRANT ALL ON flow_demo.* TO flow;'
echo "flow_demo 已清空"
