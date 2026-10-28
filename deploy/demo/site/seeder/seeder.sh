#!/bin/sh
# 演示数据：启动时库里还没有流程就发布"教程做完"那一档（第一次部署），之后按 FLOW_DEMO_RESET_CRON（UTC）每天重置一次。
# 重置的输出写到容器的标准输出，docker compose logs seeder 能看到。
set -eu

node /seed/seed.mjs tutorials --if-empty

echo "${FLOW_DEMO_RESET_CRON} FLOW_BACKEND=${FLOW_BACKEND} FLOW_SEED_DIR=${FLOW_SEED_DIR} $(command -v node) /seed/seed.mjs tutorials >> /proc/1/fd/1 2>&1" | crontab -
echo "定时重置：${FLOW_DEMO_RESET_CRON}（UTC）"
exec crond -f -l 8
