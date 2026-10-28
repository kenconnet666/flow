# 示例后端：app.jar 是 build.sh 在开发机上构建好的（examples/spring-boot），这里只装进 JRE 镜像。
# 数据库密码由 compose 的 secrets 服务生成在共享卷里，启动时读进环境变量，不进镜像。
# 内存按 448M 的上限压：堆最多 55%，串行 GC，线程栈 512K，只做 C1 编译（省掉 C2 的编译内存），元空间与代码缓存封顶。
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "export FLOW_MYSQL_PASSWORD=\"$(cat /run/flow-secrets/db-password)\" && exec java -jar /app/app.jar"]
