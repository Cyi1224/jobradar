# JobRadar 上线部署指南

整套架构：**静态前端（nginx）** + **Spring Boot 后端** + **MySQL 8（持久化）**。
校招信息库的 1.4w 条数据由后端启动时灌入 MySQL，前端通过 REST API 读取。

---

## 一、改前端一行开关（让前端走真实后端）

编辑 `jobradar/js/config.js`：

```js
export const CONFIG = {
  USE_MOCK: false,                          // 上线：false（本地体验 mock 时保持 true）
  API_BASE: 'http://localhost:8080/api',    // 换成你的后端地址，如 https://api.你的域名/api
  ...
};
```

> 浏览器是直接请求后端的，所以 `API_BASE` 要填**浏览器能访问到的**后端地址（不是容器内网名）。
> 跨域已在后端 `CorsConfig` 放行。

---

## 二、方式 A：Docker 一键起（推荐，无需本机装 Maven/MySQL）

项目根目录执行：

```bash
# 可选：自定义数据库密码/库名
export DB_PASSWORD=改成强密码
export DB_NAME=jobradar

docker compose up --build
```

- MySQL → `localhost:3306`（数据持久化在 `mysql-data` 卷，重启不丢）
- 后端 → `http://localhost:8080`（首次启动灌 1.4w 条校招数据，约几十秒，仅一次）
- 前端 → `http://localhost:8123`

> 后端容器用 `SPRING_PROFILES_ACTIVE=prod` 连 MySQL，并且 `jobradar.seed-demo=false`
> （不灌「我的投递/简历」演示假数据，校招库照常灌入）。

---

## 三、方式 B：手动部署

### 1. 准备 MySQL
建库（或让程序自动建，URL 里已带 `createDatabaseIfNotExist=true`）：
```sql
CREATE DATABASE jobradar DEFAULT CHARACTER SET utf8mb4;
```

### 2. 打包并运行后端（需 JDK 17 + Maven）
```bash
cd backend
mvn clean package -DskipTests          # 产出 target/jobradar-backend-0.0.1-SNAPSHOT.jar

export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=localhost DB_PORT=3306 DB_NAME=jobradar
export DB_USER=root DB_PASSWORD=你的密码
java -jar target/*.jar
```

### 3. 部署前端
把 `jobradar/` 目录交给任意静态服务器（nginx / 对象存储 / CDN）。
nginx 示例：
```nginx
server {
  listen 80;
  root /var/www/jobradar;     # 指向 jobradar 目录
  location / { try_files $uri $uri/ /index.html; }
}
```

---

## 四、数据相关

- **校招数据更新**：重跑 `givemeoc_export_jobs_json.py` 生成新的 `backend/src/main/resources/jobs.json`，
  重新打包后端即可。注意：`DataSeeder` 只在 **job 表为空** 时灌入，所以更新数据要么清空 job 表，要么改成你自己的同步逻辑。
- **演示假数据**：`jobradar.seed-demo`（dev 默认 true，prod 为 false）。生产下「我的投递」从空开始，由用户自己「加入我的投递」写入。
- **dev 本地**：直接 `mvn spring-boot:run`（默认 H2 内存库，零配置），或继续用现有的 `server.js`（mock）。

---

## 五、上线前 checklist

- [ ] `config.js` 的 `USE_MOCK=false`、`API_BASE` 指向真实后端
- [ ] MySQL 密码改强、不要用默认 `jobradar123`
- [ ] 后端以 `prod` profile 运行（连 MySQL、不灌演示数据）
- [ ] 收紧跨域：设环境变量 `CORS_ORIGINS=https://你的前端域名`（prod profile 默认占位 `https://你的前端域名`，dev 放行全部）
- [ ] 反向代理 + HTTPS（建议前端、后端同域，用 nginx 把 `/api` 反代到后端，省掉跨域）

---

## 校招信息库 API（已分页 + 服务端筛选）

- `GET /api/jobs` —— 分页 + 筛选，返回 `{ content:[...], total, page, size, totalPages }`
  - 参数：`page`(0 起)、`size`(默认 30，上限 100)、`q`(关键词)、`recruitType`、`industry`、`city`、
    布尔开关 `apply`(可投递)/`urgent`(近 7 天截止)/`soe`(央国企)/`inst`(研究所)/`foreign`(外企)
  - 排序：按 `updatedAt` 倒序
- `GET /api/jobs/stats` —— 统计卡 + 下拉选项：`{ companies, total, open, today, todayDate, recruitTypes[], industries[] }`
- 前端 jobdb 已改为**按页拉取 + 下拉自动加载**；mock 模式在内存里同样分页，本地零后端也能跑。

## 安全 / 上线硬化（已落地）

参照「前端不可信」原则做了以下加固，**生产请务必跑 prod profile**：
- **会员开通防白嫖**：`jobradar.payment.demo-mode` —— dev=true（点击即开通便于联调），**prod=false**：
  前端直接调 `/api/membership/subscribe` 返回 **403**，会员只能由真实支付回调授予。**实测通过**。
- **限流**：`/api/auth/**` 15 分钟 20 次、`/api/membership/subscribe` 15 分钟 10 次，超出返回 **429**（防暴破/滥用，实测通过）。单实例内存版，多实例改 Redis。
- **错误不泄露**：500 只回 `服务器内部错误`，详情仅记服务端日志。
- **JWT 密钥**：prod 走环境变量 `JWT_SECRET`（≥32 字节强随机），已在 `application-prod.yml` 绑定。
- **CORS**：prod 用 `CORS_ORIGINS` 收窄到你的域名（非 `*`）。
- **密钥不入库**：新增根目录 `.gitignore`（忽略 `.env`/`target`/`node_modules`/`*.xlsx`/备份等）；源码中无任何硬编码密钥。

### 仍需你做（需账号/环境，无法在此完成）
- **真实支付**：Stripe **不支持中国大陆商户收款**，国内请接 **微信支付 / 支付宝**。安全流程不变：
  前端只传套餐名 → 服务端按 `MembershipService.daysOf` 定价下单 → 用户支付 → **网关回调验签成功后**调 `MembershipService.subscribe` 授予会员（绝不信任前端直接开通）。
- **HTTPS + 域名 + ICP 备案**；反向代理（nginx）把 `/api` 同域转发到后端。

## 已知边界 / 后续可做

- 校招数据现在是**全量含已截止**；要只上在招的，用 `givemeoc_招聘汇总.xlsx`（2155 条在招版）重导。
- 用户系统目前是自研 JWT（非 Firebase——国内更稳）；简历/投递/资料已按 `userId` 隔离。
