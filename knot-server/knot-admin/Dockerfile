# syntax=docker/dockerfile:1
#
# knot-admin（管理端）镜像。
#
# ⚠ 构建上下文必须是 Maven reactor 根目录 knot-server（不是本文件所在目录）：
#     cd knot-server
#     docker build -f knot-admin/Dockerfile -t knot-admin:local .
#
# 之所以要用 reactor 根作为上下文：knot-admin 依赖 knot-service / knot-dal / knot-adapter /
# knot-common 等兄弟模块，只有从根构建才能用 -am 一次性带上依赖模块。
#
# ── 生产运行必读（与启动期守卫配套，缺一项即拒绝启动）─────────────────────
#   SPRING_PROFILES_ACTIVE=prod
#   KNOT_SQL_INIT_MODE=NEVER        # 生产禁止每次启动重跑 schema/data（由迁移脚本演进）
#   KNOT_JWT_SECRET=<强随机>         # 否则 BootstrapSecurityGuard 拒启
#   KNOT_CREDENTIAL_ENCRYPTION_KEY=<强随机>
#   KNOT_DB_USERNAME / KNOT_DB_PASSWORD / 数据源地址
#
# 存活/就绪探针：/actuator/health

# ── 构建阶段 ────────────────────────────────────────────────────────────────
# 说明：这里没有拆「先拷 pom 预热依赖」的缓存层。dependency:go-offline 对
# spring-boot-maven-plugin 的插件依赖解析不可靠（常见 Could not resolve plugin），
# 一旦失败会让镜像构建在预热阶段就挂掉。为保证可重复构建，选择一次拷贝 + 一次 package。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY knot-common/pom.xml knot-common/
COPY knot-plugin-api/pom.xml knot-plugin-api/
COPY knot-plugin/pom.xml knot-plugin/
COPY knot-adapter/pom.xml knot-adapter/
COPY knot-dal/pom.xml knot-dal/
COPY knot-service/pom.xml knot-service/
COPY knot-admin/pom.xml knot-admin/
COPY pom.xml ./

COPY knot-common/src knot-common/src
COPY knot-plugin-api/src knot-plugin-api/src
COPY knot-plugin/src knot-plugin/src
COPY knot-adapter/src knot-adapter/src
COPY knot-dal/src knot-dal/src
COPY knot-service/src knot-service/src
COPY knot-admin/src knot-admin/src

# 单测由 CI 单独跑（那里有 MySQL service）；镜像构建只做打包，保持构建快且可复现。
# -am 连带构建依赖模块；-DskipTests 只跳过执行，测试代码仍会编译（保证编译期门禁生效）。
RUN mvn -B -ntp -pl knot-admin -am package -DskipTests

# 从构建产物里取出可执行 fat jar（spring-boot-maven-plugin repackage 后的同名 jar）
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# 非 root 运行：/app 归属 knot，便于写本地日志与 Kafka 死信目录
RUN groupadd -r knot && useradd -r -g knot -d /app knot \
    && mkdir -p /app/logs && chown -R knot:knot /app

COPY --from=build --chown=knot:knot /build/knot-admin/target/knot-admin-*.jar /app/app.jar
# spring-boot repackage 会留下 .jar.original，通配只命中可执行 jar（已去重）

USER knot
EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
