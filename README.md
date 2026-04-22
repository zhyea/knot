# knot

AI 网关项目脚手架：
- 后端：`Spring Boot 3 + MyBatis + MySQL`
- 前端：`Vue3 + Vite + Element Plus`

## 目录结构

- `knot-server`：后端服务
- `knot-front`：前端页面

## 后端启动

1. 创建数据库：`knot_ai_gateway`
2. 按需修改 `knot-server/src/main/resources/application.yml` 中数据库连接
3. 启动命令（在 `knot-server` 目录）：

```bash
mvn spring-boot:run
```

默认地址：`http://127.0.0.1:8080`  
健康检查：`GET /api/health`

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