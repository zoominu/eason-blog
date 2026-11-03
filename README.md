# Eason Blog V1.0

[![CI](https://github.com/zoominu/eason-blog/actions/workflows/ci.yml/badge.svg)](https://github.com/zoominu/eason-blog/actions/workflows/ci.yml)

单作者、前后端分离的个人技术博客：前台阅读站 + 独立管理后台 + 模块化单体后端。

> 需求基线：`docs/PROJECT_DESIGN.md`（由设计文档 DOCX 转换并在仓库内维护）。
> 开发方式：严格按 P0–P6 分阶段交付，当前进度见 `docs/DEVELOPMENT_LOG.md`。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前台 | Vue 3 · TypeScript · Vite · Pinia · Vue Router · Tailwind CSS v4 |
| 后台 | Vue 3 · TypeScript · Vite · Element Plus（浅/深色） |
| 后端 | Java 21 · Spring Boot 3.3 · Spring Security + JWT · MyBatis-Plus · Flyway |
| 数据库 | MySQL 8.x（InnoDB / utf8mb4） |
| 部署 | Nginx + Linux；Docker 可选 |

## 目录

```
eason-blog/
├── blog-server/      # Spring Boot 后端（端口 8080）
├── blog-frontend/    # 读者前台（端口 5173）
├── blog-admin/       # 管理后台（端口 5174）
├── docs/             # 设计基线、API、数据库、部署、测试、开发日志
├── deploy/           # Nginx 与 Docker 示例
├── scripts/          # 本地开发脚本（Windows PowerShell）
└── .tools/           # 便携工具链（JDK/Maven/MySQL），已被 .gitignore 忽略
```

## 快速开始（Windows 本地）

环境里没有全局 JDK / Maven / Node / MySQL 也可以跑：项目自带 `.tools/` 便携工具链。

```powershell
# 0. 进入仓库
cd eason-blog

# 1. 启动本地 MySQL（首次会自动初始化数据目录）
powershell -File scripts/start-mysql.ps1

# 2. 创建数据库与账号（首次）
powershell -File scripts/init-db.ps1

# 3. 启动后端（自动执行 Flyway 迁移）
powershell -File scripts/start-backend.ps1

# 4. 另开两个窗口：前台 / 后台
powershell -File scripts/start-frontend.ps1
powershell -File scripts/start-admin.ps1
```

访问：

- 前台：http://localhost:5173
- 后台：http://localhost:5174
- 接口健康检查：http://127.0.0.1:8080/actuator/health
- OpenAPI 文档：http://127.0.0.1:8080/swagger-ui.html

首次启动需要管理员账号（只创建一次，之后从环境变量移除）：

```powershell
$env:BLOG_ADMIN_EMAIL="you@example.com"
$env:BLOG_ADMIN_PASSWORD="your-strong-password"
# 然后启动后端；成功后关闭窗口，别把密码写进脚本或仓库
```

## 在 GitHub 上运行（Codespaces，可生成公开链接）

> 先说清楚：**GitHub 不是应用托管平台**。Pages 只能放静态文件，跑不了 Spring Boot + MySQL；
> 想让"别人点开链接就能看"，最容易的方式是用 **Codespaces**（GitHub 提供的云端开发容器）。

1. 仓库页 → **Code** → **Codespaces** → **Create codespace on main**
2. 首次初始化约 3–5 分钟（装依赖 + 下载 Maven 依赖），完成后会自动拉起后端和两个前端
3. 打开 **PORTS** 面板 → 找到 `5173` → 右键 **Port Visibility** → **Public**
4. 复制形如 `https://xxx-5173.app.github.dev` 的地址发给别人即可访问
5. 后台同理：把 `5174` 也设为 Public，账号 `admin@eason.blog` / `Admin123456`

限制（务必了解）：

| 项 | 说明 |
| --- | --- |
| 生命周期 | 停止 / 回收 Codespace 后链接立即失效，不是长期网站 |
| 免费额度 | 个人账号 120 core-hours/月，2 核机器约等于 60 小时 |
| 休眠 | 闲置 30 分钟自动停止，下次打开需重新启动 |
| 用途 | 演示、评审、给别人看一眼；**不要当生产环境** |

要长期网址，请走 `docs/DEPLOYMENT.md` 的 VPS + Nginx 方案，或用 Render / Railway / Zeabur 这类支持 Java 的 PaaS。

## 从虚拟机 / 局域网访问

开发服务器监听 `0.0.0.0`，但有两件事必须注意：

1. **虚拟机里不能用 `127.0.0.1`**（那指向虚拟机自己），要用宿主机 IP；
2. Windows 防火墙默认拦截入站，需要一次性放行：

```powershell
# 管理员身份运行
powershell -ExecutionPolicy Bypass -File scripts\allow-firewall.ps1
```

宿主机 IP 按虚拟机的网络模式选择（在宿主机 `ipconfig` 里看）：

| 虚拟机网络模式 | 虚拟机里应访问 |
| --- | --- |
| NAT（默认） | `http://192.168.x.1:5173`（VMnet8 的地址） |
| 仅主机（Host-Only） | `http://192.168.x.1:5173`（VMnet1 的地址） |
| 桥接（Bridged） | `http://<宿主机 WLAN/以太网 IP>:5173` |

后台同理换成 `5174`。后端 `8080` 只暴露 `/api/v1/**` 与 `/actuator/health`，
直接在浏览器打开根路径会返回 401，属正常。

## 常用命令

```powershell
# 后端测试（使用 eason_blog_test 库）
powershell -File scripts/test-backend.ps1

# 前端类型检查 + 构建
powershell -File scripts/build-frontend.ps1
powershell -File scripts/build-admin.ps1
```

## 关键约定

- 接口统一前缀 `/api/v1`，响应体 `{code,message,data,timestamp}`；错误使用正确 HTTP 状态码。
- 鉴权：JWT 放在 HttpOnly Cookie（`blog_token`），前端不接触 token。
- 权限：`/api/v1/admin/**` 仅 `SUPER_ADMIN`；后端校验，前端隐藏按钮只是体验优化。
- 数据库只通过 Flyway 迁移；已应用的迁移文件不修改，新增 V3、V4…
- 密码 BCrypt；密钥、数据库密码、管理员密码一律走环境变量，不入库、不入 Git。

## 当前进度

- ✅ P0 工程骨架：三端可运行、统一响应/异常、Flyway 迁移、健康检查
- ✅ P1 认证：注册 / 登录 / 退出 / 当前用户、JWT Cookie、管理员 bootstrap、权限测试
- ✅ 阅读链路：公开文章/分类/标签/专栏/站点配置/留言只读接口 + 前台页面
- ✅ P2 内容基础：文章 CRUD、Markdown 编辑与实时预览、发布/下线/回收/恢复、分类/标签/专栏管理
- ✅ P3 前台完善：站内搜索、专栏文章列表、代码高亮（highlight.js）、动态 SEO / OpenGraph
- ✅ P4 用户互动：评论（树形 + 审核）、留言、点赞、收藏、通知、用户中心、后台审核
- ⏳ P5 后台完善（用户/角色/资源/日志/系统设置）
- ⏳ P6 质量与部署（性能、安全、备份恢复演练）

## 文档

- [项目设计基线](docs/PROJECT_DESIGN.md)
- [API 说明](docs/API.md)
- [数据库设计](docs/DATABASE.md)
- [部署说明](docs/DEPLOYMENT.md)
- [测试说明](docs/TESTING.md)
- [开发日志](docs/DEVELOPMENT_LOG.md)
