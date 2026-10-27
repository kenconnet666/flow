#!/bin/sh
# 本机预览用：跑 Spring Boot 示例后端（端口 8080），连本仓库 infra/mysql 起的开发 MySQL（WSL 的 Docker）里的 flow_demo 库。
# 连接变量由 infra/mysql/live-env.sh 给（密码从那里已忽略的 .env 读，不写进这里）。
cd "$(dirname "$0")/.." || exit 1

# 预览工具起的进程里可能没有 JAVA_HOME：用 IDEA 装在 ~/.jdks 下的最新一个
if [ ! -x "${JAVA_HOME:-/none}/bin/java" ] && [ ! -x "${JAVA_HOME:-/none}/bin/java.exe" ]; then
  JAVA_HOME="$(ls -d "$HOME"/.jdks/*/ 2>/dev/null | sort | tail -1)"
  export JAVA_HOME="${JAVA_HOME%/}"
fi

# 这台机器上 JDK 的 NIO 管道（Selector 要用）在临时目录里建 Unix 域套接字时，bind 成功、connect 却报 Invalid argument，
# 而 JDK 只在 bind 失败时才退回 TCP 回环：把套接字目录指到一个不存在的目录，让 bind 失败、走 TCP（只影响本机预览）
NO_AF_UNIX="$(cygpath -m "$HOME")/flow-no-af-unix"

FLOW_MYSQL_DATABASE=flow_demo . infra/mysql/live-env.sh

# 编译示例连同引擎各模块，取出类路径，直接用 java 跑（spring-boot:run 另起一个 JVM，停掉预览时它还留着占着端口）
./mvnw -q -B install -DskipTests -pl java/flow-model,java/flow-core,java/flow-spring -am || exit 1
./mvnw -q -B compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -pl examples/spring-boot || exit 1
CLASSPATH="examples/spring-boot/target/classes;$(cat examples/spring-boot/target/classpath.txt)"
exec "$JAVA_HOME/bin/java" "-Djdk.net.unixdomain.tmpdir=$NO_AF_UNIX" -cp "$CLASSPATH" org.dpdns.zerodep.flow.example.ExampleApplication
