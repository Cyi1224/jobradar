# JobRadar 后端（Spring Boot）

职雷求职追踪平台的后端服务，提供投递管理、校招信息库、AI 推荐、投递复盘等 REST 接口。

## 技术栈
- Java 17 · Spring Boot 3.3
- Spring Web · Spring Data JPA
- H2 内存数据库（开发期零配置；切 MySQL 见 `application.yml` 注释）

## 运行

```bash
cd backend
mvn spring-boot:run
```

启动后：
- API 根地址：`http://localhost:8080/api`
- H2 控制台：`http://localhost:8080/h2-console`（JDBC URL: `jdbc:h2:mem:jobradar`，用户 `sa`，空密码）

启动时会自动灌入与前端 mock 一致的演示数据（8 条投递、14 条校招信息、6 条推荐）。

## 让前端连上后端
编辑 `jobradar/js/config.js`：
```js
export const CONFIG = {
  USE_MOCK: false,                       // 改为 false
  API_BASE: 'http://localhost:8080/api',
};
```
然后照常用静态服务器打开前端（如 `http://127.0.0.1:8123`）。CORS 已在 `CorsConfig` 放行。

## 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/applications` | 投递列表 |
| GET | `/api/applications/{id}` | 单条详情 |
| POST | `/api/applications` | 新增投递 `{co,pos,type,city,deadline,status,note}` |
| PATCH | `/api/applications/{id}/status` | 更新状态 `{status, fields}` |
| DELETE | `/api/applications/{id}` | 删除 |
| GET | `/api/applications/stats` | 各状态计数 |
| GET | `/api/jobs` | 校招信息库 |
| GET | `/api/recommendations[?refresh=1]` | AI 推荐岗位 |
| GET | `/api/review/summary` | 投递复盘分析 |
| GET | `/api/profile` | 用户资料（个人中心 / 自动填充）|
| GET | `/api/resumes` | 简历列表 |

## 测试 & Docker

```bash
mvn test                 # 运行 JUnit + MockMvc 接口测试
docker compose up --build   # 在项目根目录：一键起后端(8080)+前端(8123)
```

## 分层结构
```
controller/   接收请求、参数校验，不写业务
service/      业务逻辑（状态流转、时间线生成、复盘派生）
repository/   Spring Data JPA 接口
entity/       JPA 实体（Application 1—* StatusLog）
dto/          对外数据结构（record），与前端字段对齐
config/       CORS、启动数据灌入
common/       统一异常处理
```
