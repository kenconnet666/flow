# 连这个目录起的开发 MySQL 用的变量，在仓库根目录 source：
#   . infra/mysql/live-env.sh                              # 实库测试（java/flow-it），库 flow_it
#   FLOW_MYSQL_DATABASE=flow_demo . infra/mysql/live-env.sh   # 示例后端（.claude/run-example-backend.sh）
# 密码从同目录已忽略的 .env 读，不写进这里。
# 主机默认取 WSL 的地址（`hostname -I` 的第一项，WSL 重启后会变，所以每次现取）：WSL 的 localhost 转发只认 WSL 启动时
# 就在监听的端口，之后新发布的容器端口经 localhost 连不上。不在 Windows 上（没有 wsl.exe）时用 localhost；也可以设 FLOW_MYSQL_HOST。
_flow_host="${FLOW_MYSQL_HOST:-$(wsl.exe -d Debian hostname -I 2>/dev/null | cut -d' ' -f1 | tr -d '\r')}"
export FLOW_MYSQL_URL="jdbc:mysql://${_flow_host:-localhost}:${FLOW_MYSQL_PORT:-53316}/${FLOW_MYSQL_DATABASE:-flow_it}"
export FLOW_MYSQL_USER=flow
export FLOW_MYSQL_PASSWORD="$(grep '^FLOW_DB_PASSWORD=' infra/mysql/.env | cut -d= -f2-)"
unset _flow_host
