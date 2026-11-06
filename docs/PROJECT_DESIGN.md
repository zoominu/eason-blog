# Eason Blog V1.0

项目设计文档

面向 DeepSeek Harness / AI Coding Agent 的分阶段开发蓝图

版本：V1.0  |  文档日期：2026-11-03  |  状态：开发基线

# 文档使用说明

本文件是项目的统一需求与技术基线，建议放入项目仓库根目录，命名为 docs/PROJECT_DESIGN.md（也可保留 DOCX 供阅读）。AI 开发工具必须先读取本文件，再按阶段实施，不得一次性生成全部系统。

推荐工作方式：一个阶段一个任务；每次先分析、列计划，再改代码、运行测试、总结变更。

所有实现以本文件为准。若发现需求冲突或技术风险，先提出方案与影响，等待确认，不擅自改变核心架构。

每个阶段完成后，更新 README、开发日志、接口文档、数据库迁移和测试。

不要把密钥、数据库密码、JWT 密钥、真实管理员密码提交到 Git。

# 1. 项目概述

## 1.1 项目定位

Eason Blog 是一个单作者、前后端分离的个人技术博客系统。核心目标是沉淀 Java、Spring Boot、MySQL、Vue 等技术学习笔记与实践文章，同时提供现代化阅读体验、用户互动能力和可学习、可维护的后台管理系统。

## 1.2 项目目标

建立稳定的个人知识库：文章、分类、标签、专栏、搜索与 Markdown 阅读。

提供访客与注册用户互动：评论、留言、点赞、收藏、通知。

仅站点管理员（作者本人）发布和管理文章；普通注册用户不能投稿或发布文章。

采用清晰、模块化、可测试的代码结构，适合初学者理解和持续迭代。

可部署到 Linux VPS 或 NAS 环境，通过 Nginx 提供 HTTPS 与反向代理。

## 1.3 范围与非目标

V1.0 范围：个人博客、用户认证、互动、后台管理、资源管理、基础日志与部署。

暂不采用微服务、复杂工作流、付费订阅、多人作者投稿、实时聊天。

Redis、邮件验证、对象存储、全文搜索引擎均按后续阶段启用；MVP 可先使用 MySQL 查询与本地文件存储。

不照搬 Poetize 的代码或视觉，仅借鉴功能组织与信息架构。

# 2. 已确认产品决策

| 项目项 | 确定方案 |
| --- | --- |
| 作者模式 | 单作者；仅管理员本人发布文章 |
| 普通用户 | 注册后可评论、留言、点赞、收藏；不可投稿/发布 |
| 登录 | 邮箱 + 密码 |
| 文章编辑 | Markdown；原始 Markdown 为内容源 |
| 前台主题 | 自动跟随系统 + 手动切换 |
| 后台主题 | 浅色 + 深色双主题 |
| UI方向 | Apple 极简 + 现代科技感；留白、黑白灰、少量蓝/银色点缀 |
| 导航 | 顶部毛玻璃导航；适度动效 |
| 首页 | 博客内容优先，文章列表为主，可切换卡片模式 |
| 参考 | 借鉴 Poetize 的功能布局，不复制其实现 |
| 架构 | 前后端分离、模块化单体，不使用微服务 |

# 3. 技术栈与系统架构

## 3.1 技术栈

| 层 | 技术 | 说明 |
| --- | --- | --- |
| 前台 | Vue 3 + TypeScript + Vite + Pinia + Vue Router | 读者网站；组件化、响应式 |
| UI | Tailwind CSS + shadcn-vue + Lucide | 极简视觉；按需组件 |
| 后台 | Vue 3 + TypeScript + Element Plus | 独立管理端；浅/深主题 |
| Markdown | Vditor（编辑）+ Markdown 渲染器 + Shiki | 编辑、目录、高亮、复制；输出必须安全净化 |
| 后端 | Java 21 + Spring Boot 3.x + Maven | REST API；模块化单体 |
| 安全 | Spring Security + JWT + HttpOnly Cookie | 认证授权；密码 BCrypt |
| 持久层 | MyBatis-Plus | Mapper/分页/条件查询 |
| 数据库 | MySQL 8.x + Flyway | 版本化迁移 |
| 部署 | Nginx + Linux；Docker 可选 | HTTPS、静态资源、反向代理 |
| 缓存 | Redis（可选） | 限流、缓存、验证码等后续能力 |

## 3.2 架构图（逻辑）

Browser

```text
 ├─ blog-frontend (Vue 3) ── public pages / reader interactions
 └─ blog-admin (Vue 3 + Element Plus) ── admin console
```

│ HTTPS / JSON REST (/api/v1)

▼

```text
       Nginx
```

│ reverse proxy

▼

```text
 blog-server (Spring Boot modular monolith)
```

├─ auth / user / article / category / tag / column

├─ comment / guestbook / favorite / like / notification

├─ resource / system / log / dashboard

└─ common / security / config

│

```text
          ├── MySQL 8 (business data + Flyway)
          ├── Redis (optional)
          └── File storage (local directory / future object storage)
```

## 3.3 域名与部署建议

方案 A（推荐易维护）：www.eas0n.top 前台、admin.eas0n.top 后台、api.eas0n.top API。

方案 B（简化 Cookie/跨域）：同域部署 /、/admin/、/api/。

生产环境强制 HTTPS；数据库与 Redis 不直接暴露公网；上传目录不可执行脚本。

具体域名、端口、路径、存储目录通过环境变量或部署配置管理，不硬编码进源码。

# 4. 角色、权限与业务规则

## 4.1 角色

SUPER_ADMIN：作者本人，管理所有内容、用户、系统设置、资源与日志。

USER：普通注册用户，可管理个人资料、密码、收藏、评论、留言；不能创建、编辑、发布文章。

GUEST：未登录访客，可阅读公开内容、搜索；互动权限按下表执行。

## 4.2 权限矩阵

| 操作 | 游客 | 普通用户 | 管理员 |
| --- | --- | --- | --- |
| 阅读公开文章 | 允许 | 允许 | 允许 |
| 评论/回复 | 不允许 | 允许 | 允许 |
| 点赞/收藏 | 不允许 | 允许 | 允许 |
| 留言 | 可配置（建议需登录） | 允许 | 管理 |
| 发布/编辑/删除文章 | 不允许 | 不允许 | 允许 |
| 管理分类/标签/专栏 | 不允许 | 不允许 | 允许 |
| 管理用户/系统/日志 | 不允许 | 不允许 | 允许 |

权限检查必须在后端执行；前端隐藏按钮不构成安全措施。

# 5. 页面与路由设计

## 5.1 前台页面

| 路由 | 页面职责 |
| --- | --- |
| 首页 / | Hero（博客内容优先）、最新/推荐文章、分类、专栏、近期更新、简短作者介绍、项目入口、页脚；文章列表/卡片切换 |
| 博客 /blog | 文章列表、分页、分类/标签筛选、排序、关键词 |
| 文章 /article/:slug | Markdown 正文、TOC、阅读进度、代码高亮/复制、图片放大、阅读时间、上一篇/下一篇、相关文章、点赞/收藏/评论 |
| 分类 /categories、/category/:slug | 分类目录、分类文章列表 |
| 标签 /tags、/tag/:slug | 标签目录、标签文章列表 |
| 专栏 /columns、/column/:slug | 专栏列表、专栏详情与文章 |
| 搜索 /search | 关键词搜索、结果分页 |
| 留言板 /guestbook | 留言、回复、审核后的内容 |
| 关于 /about | 作者、技术栈、联系方式/社交链接 |
| 项目 /projects（可选） | 个人项目展示 |
| 登录/注册 /login、/register | 邮箱密码认证 |
| 用户中心 /user | 资料、安全、收藏、通知 |
| 收藏 /user/favorites | 收藏文章列表 |
| 404 | 不存在路由/资源的友好提示 |

## 5.2 后台页面

/admin/login：管理员登录。

/admin/dashboard：统计卡片、文章趋势/近期操作（初期可简化）。

/admin/articles：文章列表、筛选、草稿/发布/下线/回收；/admin/articles/create、/admin/articles/:id/edit。

/admin/categories、/admin/tags、/admin/columns：内容组织管理。

/admin/comments、/admin/guestbook：审核、回复、删除/屏蔽。

/admin/users、/admin/roles、/admin/permissions：用户与权限管理。

/admin/resources：上传、预览、引用检查、删除。

/admin/login-logs、/admin/operation-logs：安全与操作审计。

/admin/settings、/admin/profile：站点配置与管理员资料。

后台布局：左侧菜单、顶部导航、面包屑、可折叠菜单、内容区、主题切换。

# 6. 数据库设计

数据库：MySQL 8.x；默认 InnoDB、utf8mb4。统一 BIGINT 主键（建议应用生成或自增，项目内统一策略）；时间字段统一命名 create_time/update_time；业务表可使用 is_deleted 逻辑删除，日志表不做软删除。表名使用 blog_ 前缀，避免 user、column 等潜在保留字冲突。

## 6.1 表清单（建议完整版本）

| 表 | 用途 |
| --- | --- |
| blog_user | 用户账号、资料、状态、登录信息 |
| blog_role | 角色 |
| blog_permission | 权限点 |
| blog_user_role | 用户-角色关联（唯一 user_id+role_id） |
| blog_role_permission | 角色-权限关联（唯一 role_id+permission_id） |
| blog_article | 文章 Markdown、摘要、封面、分类、状态、SEO、统计 |
| blog_category | 分类树（parent_id） |
| blog_tag | 标签 |
| blog_article_tag | 文章-标签关联 |
| blog_column | 专栏 |
| blog_column_article | 专栏-文章关联及排序 |
| blog_comment | 文章评论与回复（parent_id） |
| blog_guestbook | 留言板及回复 |
| blog_favorite | 用户收藏（user_id+article_id 唯一） |
| blog_article_like | 文章点赞（user_id+article_id 唯一） |
| blog_notification | 用户通知 |
| blog_resource | 文件资源元数据；文件本体存文件系统/对象存储 |
| blog_system_config | 系统配置 |
| blog_announcement | 公告 |
| blog_friend_link | 友情链接 |
| blog_login_log | 登录日志 |
| blog_operation_log | 后台操作日志 |
| blog_article_revision | 文章修订历史 |
| blog_article_view_log（可选） | 阅读记录/访客去重；需考虑数据增长与保留周期 |

## 6.2 核心字段建议

### blog_user

id BIGINT PRIMARY KEY

uid VARCHAR(32) NOT NULL UNIQUE            -- 对外用户标识

email VARCHAR(128) NOT NULL UNIQUE          -- 登录账号

password VARCHAR(255) NOT NULL              -- BCrypt 哈希，不存明文

nickname VARCHAR(50) NOT NULL

avatar VARCHAR(500) NULL

bio VARCHAR(500) NULL

status TINYINT NOT NULL DEFAULT 1            -- 0 禁用，1 正常

email_verified TINYINT NOT NULL DEFAULT 0

last_login_time DATETIME NULL

last_login_ip VARCHAR(45) NULL

register_time DATETIME NOT NULL

create_time DATETIME NOT NULL

update_time DATETIME NOT NULL

is_deleted TINYINT NOT NULL DEFAULT 0

### blog_article

id BIGINT PRIMARY KEY

author_id BIGINT NOT NULL

category_id BIGINT NULL

title VARCHAR(200) NOT NULL

slug VARCHAR(200) NOT NULL UNIQUE

summary VARCHAR(500) NULL

cover VARCHAR(500) NULL

content LONGTEXT NOT NULL                  -- Markdown 原文

content_html LONGTEXT NULL                 -- 可选缓存，需安全净化

content_format VARCHAR(20) NOT NULL DEFAULT 'markdown'

status TINYINT NOT NULL DEFAULT 0           -- 0 草稿 1 已发布 2 下线 3 回收

visibility TINYINT NOT NULL DEFAULT 0        -- 0 公开 1 私密；密码文章暂不纳入 MVP

is_top TINYINT NOT NULL DEFAULT 0

is_recommend TINYINT NOT NULL DEFAULT 0

view_count BIGINT NOT NULL DEFAULT 0

like_count BIGINT NOT NULL DEFAULT 0

comment_count BIGINT NOT NULL DEFAULT 0

seo_title VARCHAR(200) NULL

seo_description VARCHAR(500) NULL

publish_time DATETIME NULL

create_time DATETIME NOT NULL

update_time DATETIME NOT NULL

is_deleted TINYINT NOT NULL DEFAULT 0

## 6.3 约束与索引

blog_article.slug 唯一；索引 (status, publish_time)、(category_id, status)、(is_top, publish_time)。

blog_user.email、uid 唯一；按 status、create_time 建常用索引。

blog_article_tag、blog_user_role、blog_role_permission、blog_favorite、blog_article_like 建联合唯一键防止重复关系。

评论索引 article_id+status+create_time、parent_id；专栏关联 column_id+sort。

日志索引 create_time、operator_id/user_id、login_status；日志设置保留周期。

核心关系建议建立外键或在应用层严格保证一致性；若采用软删除，需明确关联清理策略。

不要将图片/附件二进制写入 MySQL；存文件系统，数据库只保存元数据和路径。

文章浏览量采用原子更新/定时聚合；不要每次浏览都无约束地新增永久日志。

## 6.4 初始化数据与管理员

Flyway 负责建表与基础字典。初始化角色、权限、默认分类可放迁移脚本；管理员账号必须通过一次性 bootstrap 命令/安全初始化流程创建，密码由环境变量或交互输入提供并进行 BCrypt 哈希，禁止将真实密码或固定哈希写入 SQL/Git。

建议迁移目录：src/main/resources/db/migration/V1__init_schema.sql、V2__seed_roles_permissions.sql；后续每次结构变化新建 V3、V4，不修改已应用的迁移文件。

# 7. REST API 设计

统一前缀：/api/v1；JSON；UTF-8；时间采用 ISO-8601；列表分页参数 page、size，默认 page=1、size=10，限制最大 size。

## 7.1 统一响应

{

"code": 200,

"message": "success",

"data": {},

"timestamp": "2026-11-03T10:00:00+08:00"

}

分页 data 示例：

{

"records": [],

"total": 0,

"current": 1,

"size": 10,

"pages": 0

}

错误响应使用稳定业务错误码；HTTP 状态码应正确表达 400/401/403/404/409/422/429/500。不要把堆栈、SQL、密钥或内部异常详情返回给客户端。

## 7.2 公开 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /articles | 文章分页；支持 keyword/category/tag/sort |
| GET | /articles/{slug} | 文章详情（仅公开已发布） |
| GET | /articles/latest | 最新文章 |
| GET | /articles/recommended | 推荐文章 |
| GET | /categories | 分类列表 |
| GET | /categories/{slug}/articles | 分类文章 |
| GET | /tags | 标签列表 |
| GET | /tags/{slug}/articles | 标签文章 |
| GET | /columns | 专栏列表 |
| GET | /columns/{slug} | 专栏详情 |
| GET | /search | 站内搜索 |
| GET | /site/config | 公开站点配置 |
| GET | /site/statistics | 站点公开统计 |
| GET | /guestbook | 已审核留言列表 |

## 7.3 认证与用户 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /auth/register | 注册（按配置启用） |
| POST | /auth/login | 邮箱密码登录；设置安全 Cookie |
| POST | /auth/logout | 注销/撤销 refresh token（如启用） |
| GET | /auth/me | 当前用户 |
| PATCH | /users/me | 更新资料 |
| PATCH | /users/me/password | 修改密码 |
| GET | /users/me/favorites | 我的收藏 |
| GET | /users/me/notifications | 通知列表 |
| POST | /articles/{id}/favorite | 收藏 |
| DELETE | /articles/{id}/favorite | 取消收藏 |
| POST | /articles/{id}/like | 点赞（幂等） |
| POST | /articles/{id}/comments | 发表评论 |
| POST | /comments/{id}/reply | 回复评论 |
| POST | /guestbook | 提交留言 |

## 7.4 管理端 API（统一 /admin 前缀）

GET /admin/dashboard：仪表盘统计。

GET/POST /admin/articles；GET/PATCH/DELETE /admin/articles/{id}；POST /admin/articles/{id}/publish、/offline。

GET/POST/PATCH/DELETE /admin/categories、/admin/tags、/admin/columns。

GET/PATCH/DELETE /admin/comments；POST /admin/comments/{id}/approve、/reject。

GET/PATCH/DELETE /admin/guestbook；POST /admin/guestbook/{id}/approve、/reject。

GET/PATCH /admin/users；POST /admin/users/{id}/disable、/enable。

GET/POST/PATCH/DELETE /admin/roles、/admin/permissions；关联角色权限的专用接口。

POST /admin/resources/upload；GET /admin/resources；DELETE /admin/resources/{id}（删除前检查引用）。

GET /admin/login-logs、GET /admin/operation-logs。

GET/PATCH /admin/settings；GET/PATCH /admin/profile。

## 7.5 API 约定

DTO 接收请求、VO 返回响应；Entity 不直接暴露给前端。

请求参数使用 Jakarta Validation；分页/排序字段白名单。

文章详情以 slug 为公开路由标识；后台更新使用 id。

评论/留言默认审核状态 pending；可在配置中开放自动审核。

点赞/收藏必须幂等；计数由服务层事务维护或异步汇总。

使用 OpenAPI/Swagger 生成接口文档；接口变更同步更新文档与前端 API。

# 8. 前端规范与页面交互

## 8.1 视觉规范

主风格：Apple 极简与科技感结合；大留白、清晰层级、黑白灰为主，少量浅蓝/银色点缀。

字体：系统优先（SF Pro/Helvetica Neue/Inter 等可用字体回退），中文使用系统中文字体。

导航：顶部毛玻璃，保证滚动时可读；背景模糊不可影响性能或可访问性。

动画：适度淡入、轻微位移、hover 透明度/缩放；尊重 prefers-reduced-motion。

主题：前台自动跟随系统并允许手动切换；后台浅/深色切换并持久化偏好。

响应式：Mobile-first；移动端菜单、文章目录折叠、代码块横向滚动。

## 8.2 前台目录建议

blog-frontend/

src/

api/                 # API 请求封装

assets/

components/           # 公共组件

layouts/              # MainLayout

router/

stores/               # Pinia

styles/

utils/

views/

Home/

Blog/

Article/

Categories/

Tags/

Columns/

Search/

Guestbook/

About/

User/

Auth/

types/

App.vue

main.ts

## 8.3 后台目录建议

blog-admin/

src/

api/

assets/

components/

layouts/              # AdminLayout

router/

stores/

styles/

utils/

views/

Login/

Dashboard/

Articles/

Categories/

Tags/

Columns/

Comments/

Guestbook/

Users/

Roles/

Permissions/

Resources/

Logs/

Settings/

types/

App.vue

main.ts

# 9. 后端代码结构与开发规范

blog-server/src/main/java/com/eason/blog/

BlogApplication.java

common/

response/             # ApiResponse、PageResult

exception/            # BusinessException、GlobalExceptionHandler

enums/

constants/

validation/

config/

security/

module/

auth/{controller,service,service/impl,dto,vo}

user/{controller,service,service/impl,mapper,entity,dto,vo,convert}

article/{controller,service,service/impl,mapper,entity,dto,vo,convert}

category/ tag/ column/

comment/ guestbook/ resource/ log/ system/

utils/

blog-server/src/main/resources/

mapper/

db/migration/

application.yml

application-dev.yml

application-prod.yml

## 9.1 代码规则

遵循 Controller → Service → Mapper 分层；Controller 只做参数校验、调用服务、返回响应。

业务逻辑放 Service；复杂事务使用 @Transactional；Mapper 只负责数据访问。

Entity 对应数据库；DTO 对应输入；VO 对应输出；禁止直接返回 Entity。

统一异常处理；业务异常可预期、日志记录完整但不泄漏敏感信息。

命名清晰；方法职责单一；避免巨型类/重复逻辑；关键业务逻辑写中文或英文注释说明原因。

注释重点解释设计原因、边界条件和安全注意事项，不要给每行无意义重复注释。

前端 TypeScript 禁止随意 any；API 类型集中定义；统一请求封装、错误处理、加载/空/错误状态。

配置通过 application.yml + 环境变量覆盖；dev/prod 分离；敏感值不写入仓库。

数据库结构只通过 Flyway 迁移；禁止开发中直接依赖手工改库。

每个功能必须包含必要测试：Service 单元测试、Controller/API 集成测试、关键权限测试。

## 9.2 安全基线

密码使用 BCrypt（或 Argon2），绝不明文存储或日志记录。

JWT 使用 HttpOnly、Secure（生产）、SameSite Cookie；合理设置过期时间；若 Cookie 认证需正确处理 CSRF。

后端逐接口鉴权；管理员权限不能仅由前端路由控制。

Markdown HTML、评论、留言、头像、上传文件均需校验/净化；防 XSS、SQL 注入、路径穿越。

上传限制 MIME、扩展名、大小；随机存储名；禁止执行；下载响应设置安全头。

登录、注册、评论、留言、搜索和上传进行限流/频率限制；登录错误信息避免账号枚举。

敏感日志脱敏；IP 地址与行为日志设定访问控制和保留期限。

# 10. 开发阶段与验收标准

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| P0 工程骨架 | 创建三个项目；统一配置、代码规范、README；本地可启动 | 三个服务可运行；健康检查通过；无提交密钥 |
| P1 数据库与基础认证 | Flyway 核心表；管理员 bootstrap；注册/登录/退出/当前用户 | 迁移可重复部署；密码哈希；登录/权限测试通过 |
| P2 内容基础 | 分类、标签、文章 CRUD、Markdown 编辑/预览、发布/下线 | 管理员可完整管理文章；游客只读公开文章 |
| P3 前台阅读 | 首页、列表、详情、分类/标签/专栏、搜索、响应式主题 | 页面可用；分页/SEO/目录/代码高亮；无明显布局错误 |
| P4 用户互动 | 评论/回复、留言、点赞、收藏、用户中心、通知 | 权限、幂等、审核、边界测试通过 |
| P5 后台完善 | Dashboard、用户/角色、资源、日志、系统设置 | RBAC 后端生效；关键操作有审计日志 |
| P6 质量与部署 | 测试、性能/安全检查、Nginx、HTTPS、备份/恢复文档 | 生产构建成功；部署、回滚、备份恢复步骤验证 |

## 10.1 每阶段交付物

阶段计划与任务拆分；

可运行代码及必要测试；

数据库迁移（如涉及）；

API 文档/前端页面说明（如涉及）；

README 更新、变更清单、运行命令；

未完成项、风险、下一阶段建议。

# 11. 仓库与文档结构

eason-blog/

docs/

PROJECT_DESIGN.md

API.md

DATABASE.md

DEVELOPMENT_LOG.md

DEPLOYMENT.md

```text
  blog-frontend/
  blog-admin/
  blog-server/
```

deploy/

nginx/

docker/              # 可选

scripts/

README.md

.gitignore

.env.example

建议将本 DOCX 转成或同步维护为 docs/PROJECT_DESIGN.md，便于 Harness/代码代理直接读取。

# 12. 完整 AI 项目提示词（DeepSeek Harness）

以下提示词可直接粘贴给 DeepSeek Harness。首次运行建议要求它只执行“项目初始化与计划”，不要立即生成所有模块。

你是 Eason Blog V1.0 项目的资深全栈架构师、Java 后端工程师、Vue 前端工程师、测试工程师与代码导师。请严格遵守仓库根目录 docs/PROJECT_DESIGN.md（或我提供的《Eason Blog V1.0 项目设计文档》），将它视为项目唯一需求基线。

【项目目标】

开发一个单作者个人技术博客。作者本人是唯一文章发布者；普通注册用户只能评论、留言、点赞、收藏、管理个人资料，不能投稿或发布文章。系统采用前后端分离、模块化单体架构，适合初学者学习和长期维护。

【技术栈】

- blog-frontend：Vue 3、TypeScript、Vite、Pinia、Vue Router、Tailwind CSS、shadcn-vue、Lucide。

- blog-admin：Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus，支持浅色/深色主题。

- blog-server：Java 21、Spring Boot 3.x、Maven、Spring Security、MyBatis-Plus、Springdoc OpenAPI。

- MySQL 8.x、Flyway；Redis 为可选项；Nginx 部署；Docker 非强制。

- Markdown 编辑器 Vditor 或经确认的同类方案；代码高亮 Shiki；Markdown 输出必须进行安全净化。

【产品与 UI】

前台 Apple 极简 + 科技感，充分留白，黑白灰主色，少量浅蓝/银色点缀，顶部毛玻璃导航，适度动效，响应式布局。前台主题自动跟随系统并允许手动切换。首页内容优先展示文章。后台采用清晰的管理布局，浅/深主题切换。参考 Poetize 的功能组织，不复制代码或设计。

【必须遵守的工程规则】

1. 先阅读并分析设计文档、现有目录、README、配置和已有代码；不要覆盖或删除用户已有工作。

2. 严格分阶段开发。一次只实施我明确指定的阶段/任务；不得一次性生成整个项目。

3. 每个任务先输出：目标、现状分析、实施计划、涉及文件、风险/依赖、验收标准。得到我确认后再修改（若我明确要求“直接执行”，可直接实施该任务）。

4. 代码要可运行、可编译、可测试；禁止只生成伪代码、空方法、TODO 充当完成实现。

5. 采用 Controller → Service → Mapper 分层；Entity/DTO/VO 分离；统一响应、异常、校验、日志与配置。

6. 关键代码必须添加高质量中文注释，解释设计动机、关键流程、安全边界和容易误解的部分；避免逐行废话注释。

7. 数据库只通过 Flyway 迁移；迁移文件一经应用不得修改，新增版本迁移。

8. 不得将真实密码、Token、数据库凭据、JWT 密钥、个人隐私写入源码、日志或 Git；提供 .env.example，不提交真实 .env。

9. 权限必须在后端验证。普通 USER 不得通过直接调用 API 创建、修改、发布或删除文章。

10. Markdown、评论、留言、文件上传必须防 XSS、路径穿越、恶意文件和超大文件；登录/评论/留言等接口需要合理限流。

11. API 使用 /api/v1；DTO 接收请求、VO 返回响应；分页、排序、错误码遵循设计文档；接口同步维护 OpenAPI 文档。

12. 前端 API 请求统一封装；状态管理使用 Pinia；处理 loading、empty、error 状态；不要在页面组件中散落重复请求逻辑。

13. 新增功能必须有适当测试，至少覆盖核心业务、权限、异常和边界情况；不能声称测试通过而未实际运行。

14. 不随意增加技术栈、依赖或需求。遇到文档未定义事项，先列出选项与影响，优先选择简单、可维护、适合个人项目的方案。

15. 任何删除数据、重置数据库、覆盖文件、修改已有迁移、执行不可逆操作之前，必须明确说明并取得确认。

16. 每次任务完成后，输出：修改文件列表、关键实现说明、执行过的命令、测试结果（真实结果）、如何启动/验证、遗留问题、下一步建议，并更新 docs/DEVELOPMENT_LOG.md。

【协作方式】

- 我是编程初学者。解释要循序渐进、用中文，说明“为什么这样做”，但不要用大段理论代替实际代码。

- 遇到报错先定位原因，再提出最小修复；不要盲目重装、删除配置或绕过安全机制。

- 如果运行环境不具备执行某项测试的条件，明确标注“未执行”及原因。

- 保持命名、目录、依赖、API 和数据库设计一致；提交前检查编译、Lint、测试、敏感信息和差异。

- 任务边界不清时先问我一个关键问题，不要自行扩张需求。

【本轮任务】

请先只做“项目初始化与开发计划”：

A. 检查当前仓库及开发环境，不修改/删除已有文件；

B. 根据设计文档列出完整项目目录规划；

C. 拆解 P0-P6 阶段及每阶段可执行任务、依赖、验收标准；

D. 提出需要我确认的少量技术决策（仅限文档尚未明确的事项）；

E. 给出下一步最小可执行任务。

本轮不要生成全部业务代码，不要建业务数据库，不要自动安装大量依赖，不要执行不可逆操作。

## 12.1 单任务提示词模板

请依据 docs/PROJECT_DESIGN.md 执行【阶段/任务编号：____】。

先分析仓库现状并输出实施计划、文件清单、风险和验收标准。

若任务要求直接执行，请在确认范围后修改代码。

完成后必须运行适当的构建/测试，并汇报真实结果：

- 修改文件：

- 关键设计：

- 命令与结果：

- 验收项：

- 未完成/风险：

- 本地验证方式：

- 下一任务建议：

不要顺带实现未授权的功能。

# 13. Definition of Done（完成定义）

需求范围与验收标准已明确，代码实现完整，不以 TODO/空方法冒充完成。

项目可编译/构建；新增测试已运行且结果真实记录。

权限、输入校验、异常处理与安全边界符合要求。

数据库变更有 Flyway 迁移；配置有示例；无敏感信息。

API/README/开发日志同步更新；新功能有运行和验证说明。

无明显重复代码、无无用依赖、无未经确认的范围扩张。

# 14. 关键待确认项（开发前逐项确认）

注册是否开放：建议开放邮箱+密码注册；邮箱验证先作为可选能力。

留言是否允许游客：建议 V1 默认登录后留言，减少垃圾内容；若开放游客需验证码/限流/审核。

评论与留言审核策略：建议默认待审核，管理员审核后展示。

资源存储：V1 使用服务器本地目录，后续可迁移对象存储。

文章阅读量：V1 使用轻量去重策略，不先建设复杂行为分析。

是否启用 Redis：建议 P0-P4 不强依赖，等限流/缓存需求明确后接入。

项目是否 Docker 化：建议先提供原生部署说明，Docker Compose 作为可选部署方式。

# 附录 A：AI 开发时的首条消息建议

请先完整阅读 docs/PROJECT_DESIGN.md，并检查当前仓库。

严格遵守文档中的“完整 AI 项目提示词”。

本次只执行 P0 的第一步：环境与仓库检查、目录规划、阶段任务拆解。

不要创建业务代码或数据库迁移；先给我计划和验收清单。

# 附录 B：参考资料与官方文档入口

Vue 3：https://vuejs.org/

Vite：https://vite.dev/

Pinia：https://pinia.vuejs.org/

Spring Boot：https://spring.io/projects/spring-boot

Spring Security：https://spring.io/projects/spring-security

MyBatis-Plus：https://baomidou.com/

Flyway：https://flywaydb.org/

MySQL：https://dev.mysql.com/doc/

OpenAPI / Springdoc：https://springdoc.org/

Tailwind CSS：https://tailwindcss.com/

Element Plus：https://element-plus.org/

shadcn-vue：https://www.shadcn-vue.com/
