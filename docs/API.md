# API 说明（V1.0 / 已实现部分）

统一前缀：`/api/v1`　编码：UTF-8　时间：ISO-8601（`2026-11-03T10:00:00`，时区 Asia/Shanghai）
交互式文档：后端启动后访问 `/swagger-ui.html`（OpenAPI JSON：`/v3/api-docs`）。

---

## 1. 统一响应

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2026-11-03T10:00:00+08:00"
}
```

- `code` 与 HTTP 状态码一致，避免「200 包裹错误」。
- 错误示例：`401 未登录或登录已过期`、`403 没有权限执行该操作`、`404 资源不存在`、`422 参数校验失败`。
- 分页 `data`：

```json
{ "records": [], "total": 0, "current": 1, "size": 10, "pages": 0 }
```

分页参数：`page` 从 1 开始，`size` 默认 10、**最大 50**（超出返回 422）。

---

## 2. 认证

登录成功后服务端通过 `Set-Cookie` 下发：

```
blog_token=<JWT>; Path=/; HttpOnly; SameSite=Lax; Max-Age=7200
```

- 前端不读取、不存储 token；浏览器自动携带。
- 也支持 `Authorization: Bearer <token>`（便于脚本/移动端调试）。
- 生产环境 `COOKIE_SECURE=true`，仅 HTTPS 传输。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 注册（邮箱 + 密码，需含字母与数字，≥8 位） | 公开（可关闭） |
| POST | `/auth/login` | 登录，返回用户信息并写 Cookie | 公开 |
| POST | `/auth/logout` | 清除 Cookie | 登录 |
| GET | `/auth/me` | 当前用户 | 登录 |
| PATCH | `/users/me` | 修改昵称/头像/简介 | 登录 |
| PATCH | `/users/me/password` | 修改密码（校验旧密码，成功后旧 token 全部失效） | 登录 |
| POST | `/auth/forgot-password` | 发送重置邮件；邮箱不存在也返回 200，防枚举 | 公开（限流） |
| POST | `/auth/reset-password` | `{token,newPassword}`；token 单次有效，30 分钟过期 | 公开 |
| POST | `/auth/verify-email` | `{token}`；消费邮箱验证 token | 公开 |
| POST | `/auth/resend-verification` | 重新发送验证邮件 | 登录 |

注册请求：

```json
{ "email": "reader@example.com", "password": "Passw0rd123", "nickname": "读者" }
```

登录失败统一返回 `401 邮箱或密码错误`（不区分账号不存在与密码错误，防枚举）。

用户对象（`UserVO`）：

```json
{
  "id": 2, "uid": "u9f3c...", "email": "reader@example.com",
  "nickname": "读者", "avatar": null, "bio": null,
  "roles": ["USER"], "registerTime": "2026-11-03T10:00:00"
}
```

---

## 3. 公开内容

### 3.1 文章

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/articles` | 分页；参数 `page`、`size`、`keyword`、`category`(slug)、`sort`(latest/popular) |
| GET | `/articles/latest?limit=5` | 最新文章（1–20） |
| GET | `/articles/recommended?limit=5` | 推荐文章 |
| GET | `/articles/{slug}` | 详情（Markdown 原文）；每次访问浏览量 +1 |

规则：只返回 `status=1`（已发布）且 `visibility=0`（公开）的文章；草稿/下线/私密一律 404。

列表项：

```json
{
  "id": 3, "title": "MySQL 索引失效的六个常见场景",
  "slug": "mysql-index-not-used-cases",
  "summary": "隐式类型转换…", "cover": null,
  "categoryId": 3, "categoryName": "MySQL", "tags": [],
  "top": false, "recommend": true,
  "viewCount": 12, "likeCount": 0, "commentCount": 0,
  "readingMinutes": 2, "publishTime": "2026-10-31T10:00:00"
}
```

详情额外包含：`content`（Markdown）、`updateTime`、`seoTitle`、`seoDescription`。
`content_html` 目前不落库，渲染与净化在前端完成（DOMPurify）。

### 3.2 分类 / 标签 / 专栏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/categories` | 分类列表（含 articleCount） |
| GET | `/categories/{slug}/articles` | 分类下文章分页 |
| GET | `/tags` | 标签列表 |
| GET | `/tags/{slug}/articles` | 标签下文章分页 |
| GET | `/columns` | 专栏列表 |
| GET | `/columns/{slug}` | 专栏详情 |

### 3.3 站点与留言

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/site/config` | 公开配置（仅 `is_public=1`） |
| GET | `/site/statistics` | 文章数/阅读数/分类数/标签数/专栏数 |
| GET | `/guestbook` | 已审核留言分页（含博主 `reply`） |
| POST | `/guestbook` | 提交留言（登录，默认待审核） |
| GET | `/archive` | 按年月统计的公开文章数 |
| GET | `/rss.xml` | RSS 2.0，最新 20 篇（摘要，不含全文） |
| GET | `/sitemap.xml` | 文章/分类/标签 URL |
| GET | `/robots.txt` | 爬虫规则 + Sitemap 地址 |

### 3.4 搜索与专栏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/search?keyword=&page=&size=` | 站内搜索标题/摘要/正文，只搜已发布公开文章 |
| GET | `/columns/{slug}/articles` | 专栏内文章，按 `blog_column_article.sort` 排序 |

### 3.5 互动

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/articles/{slug}/comments?page=&size=` | 已审核评论；按顶层评论分页，`replies` 完整嵌套（最多四级） |
| POST | `/articles/{id}/comments` | 发表评论/回复（登录）；返回 `{id,status}`，`status=1` 表示已发布 |
| GET | `/articles/{id}/interaction` | 当前用户点赞/收藏状态 + 计数 |
| POST/DELETE | `/articles/{id}/like` | 点赞 / 取消（幂等） |
| POST/DELETE | `/articles/{id}/favorite` | 收藏 / 取消（幂等） |
| GET | `/users/me/favorites` | 我的收藏（分页） |
| GET | `/users/me/notifications` | 我的通知（`limit` 最大 50） |
| GET | `/users/me/notifications/unread-count` | 未读数 |
| PATCH | `/users/me/notifications/{id}/read` | 标记已读 |
| PATCH | `/users/me/notifications/read-all` | 一键已读，返回 `{updated}` |
| DELETE | `/users/me/notifications/read` | 删除已读通知，返回 `{deleted}` |

评论请求：

```json
{ "content": "写点什么", "parentId": null }
```

`parentId` 为空表示顶层评论；回复时传被回复评论的 id（不能回复自己的评论，返回 400）。
评论创建后 `status=0`（待审核），审核通过才出现在公开列表，并同步累加文章的 `comment_count`。

限制与规则：

- 评论**最多四级**，对第 4 级评论继续回复返回 400；
- 管理员（SUPER_ADMIN）评论/回复免审，直接 `status=1`；
- 审核关闭时按违禁词策略处理（见 4.7）；
- 父评论被拒绝但下面还有已通过回复时，父节点以 `hidden=true`、内容「当前评论已被隐藏」占位返回，
  回复照常展示；删除父评论会连同其下所有回复一起删除；
- 「评论已通过审核」通知只发第一次，之后拒绝再通过不重复通知；
- 通知链接为 `/article/{slug}?comment={id}`，同页点击（只改 query）也会重新拉取评论并定位高亮。

`/site/config` 返回键值对：`site.title`、`site.subtitle`、`site.description`、`site.author`、`site.avatar`、`site.startDate`、`site.github`、`site.icp`。

---

## 4. 管理端

所有 `/api/v1/admin/**` 要求 `SUPER_ADMIN`，未登录 401、非管理员 403。

### 4.1 仪表盘

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/dashboard` | 内容统计 + 最近文章/用户/登录 + 归档 + 系统信息 |

```json
{
  "publishedArticles": 5, "draftArticles": 0, "offlineArticles": 1, "recycledArticles": 0,
  "totalViews": 12, "users": 2, "categories": 5, "tags": 8,
  "recentArticles": [{ "id": 6, "title": "…", "status": 1, "updateTime": "…" }],
  "archive": [{ "month": "2026-11", "total": 2 }],
  "recentUsers": [{
    "id": 1, "nickname": "Eason", "email": "admin@eason.blog", "avatar": null,
    "lastLoginTime": "2026-11-03T17:13:00", "lastLoginIp": "127.0.0.1", "roles": ["SUPER_ADMIN"]
  }],
  "recentLogins": [{
    "id": 3, "email": "admin@eason.blog", "ip": "127.0.0.1",
    "loginStatus": 1, "message": "登录成功", "createTime": "2026-11-03T17:13:00"
  }],
  "system": {
    "appName": "blog-server", "profile": "dev", "javaVersion": "21.0.13",
    "osName": "Windows 11", "osArch": "amd64",
    "startedAt": "2026-11-03T17:06:34", "uptimeSeconds": 400,
    "heapUsedMb": 62, "heapMaxMb": 3894,
    "dbProduct": "MySQL", "dbVersion": "8.0.28", "serverTime": "2026-11-03T17:13:14"
  }
}
```

说明：

- `recentUsers` 取有登录记录的用户，按 `lastLoginTime` 倒序；
- `recentLogins` 来自 `blog_login_log`，**登录成功与失败都会记录**（失败仅记邮箱与 IP，不记录密码）；
- `system` 只暴露运行时概况，不含连接串、路径、密钥；
- 所有计数为「全状态」口径（草稿/下线/回收都算），与前台公开统计不同。

### 4.2 文章

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/articles` | 分页，参数 `page`、`size`、`keyword`、`status`、`categoryId` |
| GET | `/admin/articles/{id}` | 详情（含 Markdown 原文、标签 id） |
| POST | `/admin/articles` | 新建；`status=1` 表示创建即发布 |
| PATCH | `/admin/articles/{id}` | 整体更新（含标签全量替换） |
| POST | `/admin/articles/{id}/publish` | 发布（草稿/下线 → 已发布，补写 `publishTime`） |
| POST | `/admin/articles/{id}/offline` | 下线（仅已发布可调用，否则 400） |
| POST | `/admin/articles/{id}/restore` | 从回收站恢复为草稿 |
| DELETE | `/admin/articles/{id}` | 移入回收站（`status=3`，不物理删除） |

创建 / 更新请求：

```json
{
  "title": "标题",
  "slug": "可选，留空则按标题生成",
  "categoryId": 2,
  "summary": "摘要",
  "cover": "封面地址",
  "content": "# Markdown 正文",
  "visibility": 0,
  "isTop": 0,
  "isRecommend": 1,
  "seoTitle": "SEO 标题",
  "seoDescription": "SEO 描述",
  "tagIds": [1, 2],
  "status": 0
}
```

约定：

- `slug` 只允许小写字母、数字、短横线；冲突时自动追加 `-2`、`-3`；
- 中文标题无法音译，自动生成 `post-20261103-ab12`，可在后台手动改成英文；
- `author_id` 取自登录主体，不接受前端传入；
- 分类/标签不存在返回 400，不会依赖数据库外键报错。

### 4.3 分类 / 标签 / 专栏

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/categories` | 全部分类（含草稿文章计数） |
| POST | `/admin/categories` | 新建 |
| PATCH | `/admin/categories/{id}` | 更新 |
| DELETE | `/admin/categories/{id}` | 删除；有子分类或有文章时 409 |
| GET/POST/PATCH/DELETE | `/admin/tags` … | 同上；被文章引用时 409 |
| GET/POST/PATCH/DELETE | `/admin/columns` … | 同上；专栏内有文章时 409 |

> 评论、用户、角色、资源、日志、系统设置等接口在 P4/P5 阶段补齐。

### 4.4 用户与角色

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/users` | 分页；`keyword`（邮箱/昵称）、`status` |
| POST | `/admin/users` | 新建用户：`{email,password,nickname?,roleIds?}`，不传角色默认 `USER` |
| PATCH | `/admin/users/{id}` | 修改资料：`{email?,nickname?,avatar?,bio?}`；改邮箱会重置验证状态 |
| POST | `/admin/users/{id}/disable` | 禁用（不能禁用自己） |
| POST | `/admin/users/{id}/enable` | 启用 |
| PATCH | `/admin/users/{id}/roles` | `{ "roleIds": [1,2] }`；不能移除自己的管理员角色 |
| DELETE | `/admin/users/{id}` | 逻辑删除 + 解除角色；不能删自己、不能删最后一个管理员 |
| GET | `/admin/roles` | 角色 + 权限 id |
| GET | `/admin/permissions` | 权限点字典 |
| PATCH | `/admin/roles/{id}/permissions` | `{ "permissionIds": [...] }` |

> 禁用后该用户的 JWT 立即失效：每次请求都会按 userId 查库校验状态。

### 4.5 资源

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/admin/resources/upload` | `multipart/form-data`，字段名 `file`，≤5MB |
| GET | `/admin/resources` | 分页，`keyword` 按文件名 |
| DELETE | `/admin/resources/{id}` | 被文章正文引用时 409 |
| GET | `/uploads/{yyyy}/{MM}/{name}` | 静态访问（Nginx 生产同路径） |

限制：扩展名白名单 `png/jpg/jpeg/gif/webp/svg/pdf/txt/md/zip`，MIME 需为 `image/*`、`text/*` 或 `application/pdf`；
存储名随机（UUID），原始文件名只入库不落盘，防路径穿越。

### 4.6 日志

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/login-logs` | 登录日志，`status`（1 成功 / 0 失败）、`keyword` |
| GET | `/admin/operation-logs` | 操作日志，`module` 过滤 |

留言回复：`PATCH /admin/guestbook/{id}/reply`，body `{ "reply": "..." }`；
回复即视为审核通过，并给留言者发站内通知。

后台列表筛选参数：

| 接口 | 参数 |
| --- | --- |
| `GET /admin/comments` | `status`、`keyword`（内容/昵称/邮箱/文章标题）、`articleId`、`userId`、`page`、`size` |
| `GET /admin/guestbook` | `status`、`keyword`（内容/昵称/邮箱）、`userId`、`page`、`size` |

删除：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| DELETE | `/admin/comments/{id}` | 删除评论**及其下所有回复**，返回 `{deleted}` |
| POST | `/admin/comments/batch-delete` | `{ "ids": [...] }`，返回 `{deleted}` |
| DELETE | `/admin/guestbook/{id}` | 删除留言 |
| POST | `/admin/guestbook/batch-delete` | `{ "ids": [...] }`，返回 `{deleted}` |

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/me/favorites` | 我的收藏（分页） |
| POST | `/users/me/favorites/batch-remove` | `{articleIds:[...]}`，批量取消收藏 |
| GET | `/users/me/likes` | 我点赞的文章（分页） |
| POST | `/users/me/likes/batch-remove` | `{articleIds:[...]}`，批量取消点赞 |
| GET | `/users/me/comments` | 我的评论（含状态、文章标题、是否可删） |
| DELETE | `/users/me/comments/{id}` | 删除自己的评论：**3 分钟内**且没有回复 |
| POST | `/users/me/comments/batch-delete` | `{ids:[...]}`，跳过超时/有回复的，返回删除数 |
| POST | `/users/me/avatar` | multipart `file`，头像 ≤2MB，png/jpg/jpeg/gif/webp |
| GET | `/users/me/notifications?type=` | `COMMENT` / `REPLY` / `SYSTEM`，不传为全部 |

昵称规则：注册、修改资料、后台新增/编辑用户都会检查 `audit.banned-words`，
命中直接 400（`昵称包含违禁词：xxx`）。

评论删除规则：仅本人、创建 3 分钟内、没有回复；管理员删除不受此限制（会级联删除回复）。

批量审核：`POST /admin/comments/batch`、`POST /admin/guestbook/batch`，
body `{ "ids": [1,2,3], "approve": true }`，返回 `{ "updated": n }`。

后台列表（评论/留言/用户/日志/资源/文章）单页上限 100，公开接口仍为 50。

操作日志由 `@Audit(module, action)` 注解 + AOP 切面写入，覆盖文章增改/发布/下线/回收、
用户禁用启用、角色调整、系统设置、评论与留言审核、资源删除等关键写操作。

### 4.7 系统设置

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/settings` | 全部配置（含非公开） |
| PATCH | `/admin/settings` | `{ "site.title": "…" }`，只允许更新已存在的 key |

审核相关配置（后台「系统设置 → 审核与违禁词」）：

| key | 取值 | 说明 |
| --- | --- | --- |
| `comment.audit` / `guestbook.audit` | `true` / `false` | 是否全部先审后发；`false` 时只拦截违禁词 |
| `audit.banned-words` | 文本，每行或逗号分隔 | 命中后按下面配置处理 |
| `audit.banned-action` | `review` / `mask` | `review` 转待审核；`mask` 替换为等长 `*` 后发布 |

> 管理员（SUPER_ADMIN）发布的评论/回复始终免审，直接展示。

### 4.8 限流

| 接口 | 限制 |
| --- | --- |
| `POST /auth/login` | 单 IP 60 次/分；单 IP+邮箱 10 次/分 |
| `POST /auth/register` | 单 IP 20 次/时；单邮箱 3 次/时 |
| `POST /articles/{id}/comments` | 单用户 5 条/分 |
| `POST /guestbook` | 单用户 3 条/5 分 |

超限返回 `429`，内存计数（单实例）；多实例部署需换 Redis 计数器。

---

## 5. 错误码

| HTTP | 场景 |
| --- | --- |
| 400 | 请求体不可解析、缺少参数 |
| 401 | 未登录、token 过期、密码错误 |
| 403 | 已登录但权限不足 |
| 404 | 资源不存在（含未发布文章） |
| 409 | 邮箱已注册等状态冲突 |
| 422 | 参数校验失败（含 size 超限、排序白名单） |
| 429 | 触发限流（登录/注册/评论/留言） |
| 500 | 未预期异常（响应不含堆栈与 SQL） |

## 6. 约定

- DTO 接收请求、VO 返回响应，Entity 不直接暴露。
- 排序、分页字段走白名单，禁止把前端字符串拼进 SQL。
- 接口变更必须同步更新本文档与 `/v3/api-docs`。
