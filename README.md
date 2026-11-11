# knot

AI 网关项目脚手架：
- 后端：`Spring Boot 3 + MyBatis + MySQL`
- 前端：`Vue3 + Vite + Element Plus`

## 目录结构

- `knot-server`：后端服务
- `knot-front`：前端页面

## 后端启动

1. 建库：**`knot`**（库形态以 `knot-server/knot-admin/src/main/resources/db/schema.sql` 为唯一标准）
2. 数据源在**各模块自己的** `src/main/resources/application-local.yml`（默认 `127.0.0.1:3306/knot`，
   账号口令可用 `KNOT_DB_USERNAME` / `KNOT_DB_PASSWORD` 覆盖）；`knot-server/` 根是 Maven 聚合 pom，
   **没有** `src/main/resources/application.yml`
3. 启动（仓库根执行；reactor 根是 `<packaging>pom</packaging>`，不能直接 `mvn spring-boot:run`）：

```bash
mvn -f knot-server/pom.xml -pl knot-admin   spring-boot:run -Dspring-boot.run.profiles=local
mvn -f knot-server/pom.xml -pl knot-gateway spring-boot:run -Dspring-boot.run.profiles=local
```

> ⚠ **profile 必须显式给**：两个模块的 `application.yml` 都不再内置 `spring.profiles.active`
> （2026-11-11 起），不带 profile 启动会因「找不到数据源 url」直接失败——这是刻意的：
> 内置 `local` 会让配错的部署静默以开发档启动（开发密钥 + fail-open 守卫），比启不来危险得多。
> 本地/测试给 `local`（或 `dev`/`test`）：启动期 `BootstrapSecurityGuard` 会在「使用开发默认密钥
> 且未激活非生产 profile」时拒绝启动；生产给 `prod` 并注入 `KNOT_JWT_SECRET` /
> `KNOT_CREDENTIAL_ENCRYPTION_KEY`（两个模块必须同值，凭证加解密同源）。

默认地址：管理端 `http://127.0.0.1:8080`，网关 `http://127.0.0.1:9090`

健康检查：**`POST /api/health`**（管理端，GET 会 405）；网关用 `/actuator/health`

## 前端启动

1. 安装依赖（在 `knot-front` 目录）：

```bash
npm install
```

2. 启动开发环境：

```bash
npm run dev
```

默认地址：`http://127.0.0.1:5173`，并已代理 `/api` 到后端 `8080` 端口。