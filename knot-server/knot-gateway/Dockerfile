# syntax=docker/dockerfile:1
#
# knot-gateway（网关运行时）镜像。
#
# ⚠ 构建上下文必须是 Maven reactor 根目录 knot-server（不是本文件所在目录）：
#     cd knot-server
#     docker build -f knot-gateway/Dockerfile -t knot-gateway:local .
#
# 网关依赖 knot-service / knot-adapter / knot-plugin-api / knot-common，必须从 reactor 根
# 用 -am 连带构建；knot-plugin 仅 runtime 依赖（插件 bean 进 IOC），同样需要一并带上。
#
# ── 生产运行必读 ────────────────────────────────────────────────────────────
#   KNOT_JWT_SECRET=<强随机>                # 否则 BootstrapSecurityGuard 拒启
#   KNOT_CREDENTIAL_ENCRYPTION_KEY=<强随机>  # 必须与 knot-admin 完全一致（凭证加解密同源）
#   KNOT_DB_USERNAME / KNOT_DB_PASSWORD / 数据源地址
#   KNOT_TRAFFIC_STORE / KNOT_LOAD_STORE     # 多节点部署必须切 redis（单节点才用 caffeine）
#   KNOT_REDIS_HOST / KNOT_REDIS_PORT / KNOT_REDIS_PASSWORD
#   KNOT_GATEWAY_ADMIN_TOKEN                 # 多节点部署摘除管理 API 必须配（否则仅 loopback 可访问）
#
# 存活/就绪探针：/actuator/health
# 指标抓取：/actuator/prometheus（建议仅内网可达）

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY knot-common/pom.xml knot-common/
COPY knot-plugin-api/pom.xml knot-plugin-api/
COPY knot-plugin/pom.xml knot-plugin/
COPY knot-adapter/pom.xml knot-adapter/
COPY knot-dal/pom.xml knot-dal/
COPY knot-service/pom.xml knot-service/
COPY knot-gateway/pom.xml knot-gateway/
COPY pom.xml ./

COPY knot-common/src knot-common/src
COPY knot-plugin-api/src knot-plugin-api/src
COPY knot-plugin/src knot-plugin/src
COPY knot-adapter/src knot-adapter/src
COPY knot-dal/src knot-dal/src
COPY knot-service/src knot-service/src
COPY knot-gateway/src knot-gateway/src

# 同 admin：测试由 CI 跑，镜像只打包；-DskipTests 仍会编译测试代码，保留编译期门禁。
RUN mvn -B -ntp -pl knot-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN groupadd -r knot && useradd -r -g knot -d /app knot \
    && mkdir -p /app/logs && chown -R knot:knot /app

COPY --from=build --chown=knot:knot /build/knot-gateway/target/knot-gateway-*.jar /app/app.jar

USER knot
EXPOSE 9090

# 网关是长连接 + 流式转发，容器内存上限要留足给堆外（Netty/HC5 直接缓冲与 SSE 缓冲）
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
