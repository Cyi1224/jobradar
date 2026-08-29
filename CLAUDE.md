# JobRadar (jobradar.xin) — 项目全览

> 大学生校招投递管理平台 | Spring Boot 3.3.4 + Vanilla JS SPA + MySQL 8 + Docker

---

## 1. 项目概览

- **线上地址**: https://jobradar.xin
- **GitHub**: https://github.com/Cyi1224/jobradar
- **服务器**: 123.207.41.105 (ubuntu)
- **容器**: MySQL 8 + Spring Boot 后端 + Nginx 前端，3 个 Docker 容器
- **本地 Maven**: `F:\BaiduNetdiskDownload\maven-mvnd-1.0.6-windows-amd64\maven-mvnd-1.0.6-windows-amd64\bin\mvnd.exe` (已加入用户 PATH，命令是 `mvnd`)

---

## 2. 目录结构

```
jobradar_frontend/
├── docker-compose.yml          # 3 容器编排
├── deploy.sh                   # 一键部署: git push → SSH pull → docker compose up
├── nginx.conf                  # 生产 Nginx (80→443, /api/→backend:8080, SPA fallback)
├── server.js                   # 本地开发服务器 (8123端口, 静态文件 + DeepSeek代理 + 后端代理)
├── .env                        # 本地环境变量 (gitignored)
│
├── backend/                    # Spring Boot 后端
│   ├── Dockerfile              # 多阶段: Maven构建 → JRE运行
│   ├── pom.xml                 # Java 17, Spring Boot 3.3.4
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml         # dev: H2, seed-demo=true, sync关闭
│       │   ├── application-prod.yml    # prod: MySQL, seed-demo=false, sync开启
│       │   └── jobs.json              # ~14k 种子数据
│       └── java/com/jobradar/
│           ├── JobradarApplication.java  # @SpringBootApplication + @EnableScheduling + @EnableAsync
│           ├── config/       # CorsConfig, DataSeeder, RestTemplateConfig, OfferbiuProperties
│           ├── common/       # GlobalExceptionHandler, ResourceNotFoundException
│           ├── security/     # JwtUtil, JwtAuthFilter, AdminApiKeyFilter, RateLimitFilter, VisitLoggingFilter, UserContext
│           ├── entity/       # User, Job, Application, StatusLog, Profile, Resume, ResumeDoc, Recommendation, VisitLog
│           ├── repository/   # 8个 JpaRepository
│           ├── service/      # JobService, ApplicationService, MembershipService, AnalyticsService, OfferbiuSyncService, ...
│           ├── controller/   # Auth, Job, Application, Profile, Resume, ResumeDoc, Recommendation, Review, Membership, Analytics
│           └── dto/          # AuthReq/Resp, JobSyncReq, JobPageDTO, ApplicationDTO, ... + offerbiu/
│
├── jobradar/                  # 前端 (Vanilla JS SPA)
│   ├── index.html             # SPA 外壳
│   ├── style.css              # 所有样式
│   ├── admin.html             # 管理后台页面
│   └── js/
│       ├── config.js          # USE_MOCK, API_BASE, LLM配置, 百度统计
│       ├── main.js            # 应用入口 (路由初始化, 登录弹窗, 导航守卫)
│       ├── core/              # auth, router, bus, format, toast, membership
│       ├── data/              # store(facade), http, mock, catalog, llm, profile, resume, resumedoc, review, meta, jobs.seed
│       └── views/             # dashboard, jobdb, applications, addjob, aimatch, review, resume, resumeeditor, autofill, profile, pricing
│
└── scripts/
    └── init-db.sql            # CREATE DATABASE jobradar
```

---

## 3. 数据库表 (MySQL/H2，Hibernate ddl-auto:update 自动管理)

| 表名 | 实体 | 主要字段 |
|------|------|---------|
| `app_user` | User | id, username(account), display_name, password_hash(BCrypt), created_at, member_until |
| `job` | Job | id, co, co_type, industry, recruit_type, target, city, positions(2000), updated_at, deadline, apply_url(2000), announce_url(2000), note(500) |
| `application` | Application | id, user_id, co, pos, job_type, city, deadline, status, note |
| `status_log` | StatusLog | id, application_id, s(状态), time, note |
| `profile` | Profile | id, user_id(unique), name, phone, email, gender, school, education, major, grad_year, gpa, intent_city, intent_job, plan |
| `resume` | Resume | id, user_id, name, updated_at, size, color, active |
| `resume_tag` | (集合) | resume_id, tags |
| `resume_doc` | ResumeDoc | id, user_id(unique), content(LONGTEXT JSON), updated_at |
| `recommendation` | Recommendation | id, co, pos, city, target, recruit_type, deadline, match_score |
| `reco_tag` | (集合) | recommendation_id, tags |
| `visit_log` | VisitLog | id, path, method, ip, user_agent, referer, user_id, username, page_name, visit_type, created_at |

---

## 4. API 端点

### 公开 (无需认证)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 {account, displayName, password} |
| POST | `/api/auth/login` | 登录 → {token, account, displayName} |
| GET | `/api/jobs` | 校招岗位分页搜索 (免费用户限5页) |
| GET | `/api/jobs/stats` | 统计卡 + 筛选下拉选项 |
| POST | `/api/jobs/sync` | 外部同步脚本热推送 (需 X-Sync-Token) |

### JWT 认证 (/api/** 自动携带)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/api/applications` | 我的投递列表 / 新建 |
| GET/PATCH/DELETE | `/api/applications/{id}` | 查看 / 更新状态 / 删除 |
| GET | `/api/applications/stats` | 各状态计数 |
| GET/PUT | `/api/profile` | 查看 / 更新个人资料 |
| GET | `/api/resumes` | 简历列表 |
| GET/PUT | `/api/resume-doc` | 简历编辑器文档 |
| GET | `/api/recommendations` | AI 推荐岗位 |
| GET | `/api/review/summary` | 投递复盘分析 |
| GET/POST | `/api/membership` | 会员状态 / 订阅 |

### Admin (X-Admin-Key 认证)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/analytics/summary` | 汇总卡片 |
| GET | `/api/admin/analytics/daily` | 每日访问注册趋势 |
| GET | `/api/admin/analytics/pages` | 页面热度排行 |
| GET | `/api/admin/analytics/recent` | 最近访问记录 |
| GET | `/api/admin/analytics/users` | 注册用户列表 |
| POST | `/api/admin/analytics/ping` | 前端页面浏览上报 |
| POST | `/api/admin/analytics/sync-offerbiu` | 手动触发 offerbiu 同步 |

---

## 5. 安全架构 (无 Spring Security，纯 Filter 链)

| 顺序 | Filter | 功能 |
|------|--------|------|
| 0 | AdminApiKeyFilter | `/api/admin/**` 需 X-Admin-Key |
| 1 | RateLimitFilter | IP限流: auth 20/15min, subscribe 10/15min |
| 2 | VisitLoggingFilter | 记录所有 API 访问到 visit_log |
| - | JwtAuthFilter | JWT Bearer Token 验证 (跳过 /api/auth/, /api/jobs, /api/admin/) |

**JWT**: HS256, jjwt 库, TTL 7天, claims: subject=userId, account, displayName
**UserContext**: ThreadLocal<Long> 存储当前请求用户ID

---

## 6. offerbiu 校招数据每日同步

### 同步机制
- **触发**: 每天凌晨 6:00 (cron: `0 0 6 * * ?`) + 应用启动 30 秒后首次同步
- **数据源**: https://offerbiu.com/api/recruitment/postings
- **策略**: 每个招聘季只拉取最新 3 页 (max-pages=3, page-size=100)，共最多 600 条
- **去重**: 业务主键 = co + positions + recruitType + city + deadline
- **updatedAt**: 使用 offerbiu 的 sourceUpdatedAt (信息来源发布时间)
- **并发控制**: AtomicBoolean 防止重复执行
- **手动触发**: `POST /api/admin/analytics/sync-offerbiu` + X-Admin-Key

### 数据映射 (offerbiu → JobRadar)
| offerbiu | JobRadar Job | 转换 |
|----------|-------------|------|
| companyName | co | 直接 |
| companyNature | coType | 直接 |
| industry | industry | 直接 |
| recruitType | recruitType | 直接 |
| targetYears[] | target | "2027届" 格式 |
| locations[] | city | 逗号拼接 |
| positionsText | positions | 截断 2000 字符 |
| sourceUpdatedAt | updatedAt | 取日期部分 YYYY-MM-DD |
| deadlineAt/deadlineText | deadline | 优先 deadlineAt |
| applyUrl | applyUrl | 直接 |
| announcementUrl | announceUrl | 直接 |
| examPolicy + noteText | note | 拼接，截断 500 字符 |

### 配置 (application-prod.yml)
```yaml
offerbiu:
  base-url: https://offerbiu.com
  sync:
    enabled: true
    cron: "0 0 6 * * ?"
    max-pages: 3
    page-size: 100
    page-delay-ms: 200
```

---

## 6.5 offerqingbaoju 校招数据每日同步（第二数据源）

> 只同步源站**最新一次更新**的批次，透传源站更新时间，与 offerbiu 一起经业务主键去重入库。

### 同步机制
- **触发**: 每天 6:30 / 15:30 (cron: `0 30 6,15 * * ?`) + 应用启动 30 秒后首次同步 + 手动触发
- **数据源**: `GET https://offerqingbaoju.cn/api/simple/navigation/{id}/data`（公开接口，无需登录）
- **导航**: `navigation-ids` 配置（默认 61=27届秋招），站点已内置筛选
- **重要**: 源站第 2 页起需登录，靠大 `page-size: 5000` 一页匿名拉全当前全量（约 2k 条）；若数据超一页会在日志告警
- **只取最新批次**: 归一化源站「更新时间」后取最大日期，仅保留该批（避免误把斜杠格式旧日期当最新）
- **updatedAt**: 透传源站「更新时间」YYYY-MM-DD（非同步当天）
- **去重**: 复用 `JobService.insertNewJobs` 业务主键 `(co, positions, recruitType, city, deadline)`，与 offerbiu/种子数据共享
- **手动触发**: `POST /api/admin/analytics/sync-offerqingbaoju` + X-Admin-Key

### 数据映射 (offerqingbaoju → JobRadar)
| offerqingbaoju | JobRadar Job | 转换 |
|----------|-------------|------|
| 企业名称 | co | 直接 |
| 企业性质 | coType | 直接 |
| 行业 | industry | 直接 |
| 招聘批次 | recruitType | 直接（秋招/春招） |
| 毕业年份 | target | 逗号分割，4 位年份拼「届」→ "2027届" / "2026届,2027届" |
| 工作地点 | city | 直接 |
| 职位 | positions | 截断 2000 字符 |
| 更新时间 | updatedAt | 归一化 YYYY-MM-DD 透传 |
| 截止时间 | deadline | 直接（ISO 日期或「招满为止」） |
| 投递地址 | applyUrl | 直接（个别行空，如实透传） |
| 公告链接 | announceUrl | 直接 |
| 学历要求 | note | 截断 500 字符 |

### 配置 (application-prod.yml)
```yaml
offerqingbaoju:
  base-url: https://offerqingbaoju.cn
  sync:
    enabled: true
    cron: "0 30 6,15 * * ?"    # 与 offerbiu 错开半小时
    navigation-ids: 61
    page-size: 5000            # 一页匿名拉全（第 2 页起需登录）
    page-delay-ms: 300
```

---

## 7. 部署

### 服务器信息
- IP: 123.207.41.105
- 用户: ubuntu
- 项目目录: /home/ubuntu/jobradar
- MySQL root 密码: cy2426155413
- Docker Compose 管理三个容器: mysql / backend / frontend

### 部署流程 (deploy.sh)
```bash
git push origin main
ssh ubuntu@123.207.41.105 "cd /home/ubuntu/jobradar && sudo git pull && sudo docker compose up -d --build"
```

### 手动 SSH 部署
```bash
ssh ubuntu@123.207.41.105
cd /home/ubuntu/jobradar
sudo git pull
sudo docker compose up -d --build
```

### Docker 命令
```bash
# 查看后端日志
sudo docker logs jobradar-backend-1 --tail 50
# 查看 offerbiu 同步日志
sudo docker logs jobradar-backend-1 --tail 100 | grep offerbiu
# MySQL 查询
sudo docker exec jobradar-mysql-1 mysql -uroot -pcy2426155413 jobradar -e "SELECT ..."
# 重启单个容器
sudo docker compose restart backend
```

### 环境变量 (生产 .env)
```
DB_HOST=mysql, DB_PORT=3306, DB_NAME=jobradar, DB_USER=root, DB_PASSWORD=cy2426155413
JWT_SECRET=0227f11364d5d7e72c673b3db7a39e9cdb07e0a0f20654fd122b729e4f07ac87
CORS_ORIGINS=https://jobradar.xin
ADMIN_API_KEY=change-me-to-a-strong-admin-key
JOBRADAR_SYNC_TOKEN=changeme
```

---

## 8. 本地开发

### 启动方式
```bash
# 1. 启动后端 (端口 8080)
cd backend
mvnd spring-boot:run

# 2. 启动前端开发服务器 (端口 8123)
node server.js
# 或者直接 docker compose up
```

### 开发配置 (application.yml)
- H2 文件数据库: `./data/jobradar`
- H2 Console: http://localhost:8080/h2-console
- CORS: 全部放行
- Demo 数据: 开启 (demo用户: demo/demo123)
- offerbiu 同步: 默认关闭 (enabled: false)

### 前端 Mock 模式
- `jobradar/js/config.js` 中 `USE_MOCK: false` 改为 `true` 即可使用内存数据
- Mock 数据来自 `jobs.seed.js` (~14k 条)

---

## 9. 关键业务规则

1. **免费用户**: 校招信息库前 5 页可浏览；会员无限
2. **会员计划**: 月(15¥/30天)、季(40¥/90天)、半年(70¥/180天)、年(100¥/365天)
3. **生产环境**: 支付必须走真实回调 (demo-mode=false)，订阅接口返回 403
4. **数据隔离**: 所有用户数据操作通过 UserContext.get() 限定的 userId
5. **状态时间线**: 每次投递状态变更自动生成 StatusLog
6. **Job 去重**: 按 co+positions+recruitType+city+deadline 业务主键
7. **DataSeeder**: 启动时若 job 表为空自动灌入 jobs.json
