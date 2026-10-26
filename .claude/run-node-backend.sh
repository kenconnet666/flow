#!/bin/sh
# 本机预览用：跑 Nitro 示例后端（typescript/apps/demo-server，端口 8080，同 Spring 示例的端口，两个只开一个）。
# 默认连 query 的开发 MySQL（WSL 的 Docker）里的 flow_demo 库：表已由 Spring 示例的 Flyway 建好，两个后端读写同一套表，
# 所以不再建表（FLOW_MIGRATE=false）；换个空库时设 FLOW_DB 与 FLOW_MIGRATE=true。密码从 query 仓库的 infra/mysql/.env 读。
cd "$(dirname "$0")/../typescript" || exit 1

FLOW_MYSQL_URL="jdbc:mysql://${FLOW_MYSQL_HOST:-172.17.206.87}:53306/${FLOW_DB:-flow_demo}"
FLOW_MYSQL_USER=query
FLOW_MYSQL_PASSWORD="$(grep '^QUERY_DB_PASSWORD=' ../../query/infra/mysql/.env | cut -d= -f2-)"
FLOW_MIGRATE="${FLOW_MIGRATE:-false}"
export FLOW_MYSQL_URL FLOW_MYSQL_USER FLOW_MYSQL_PASSWORD FLOW_MIGRATE

# 引擎各包按 dist 引用：先编译
npx tsc -b || exit 1
exec pnpm -C apps/demo-server dev
