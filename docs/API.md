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
| PATCH | `/users/me/password` | 修改密码（校验旧密码） | 登录 |

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
| GET | `/guestbook` | 已审核留言分页 |

`/site/config` 返回键值对：`site.title`、`site.subtitle`、`site.description`、`site.author`、`site.avatar`、`site.startDate`、`site.github`、`site.icp`。

---

## 4. 管理端

所有 `/api/v1/admin/**` 要求 `SUPER_ADMIN`，未登录 401、非管理员 403。

### 4.1 仪表盘

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/dashboard` | 统计卡片、最近更新、归档 |

```json
{
  "publishedArticles": 3, "draftArticles": 0, "offlineArticles": 0,
  "totalViews": 36, "users": 2, "categories": 5, "tags": 8,
  "recentArticles": [{ "id": 1, "title": "…", "status": 1, "updateTime": "…" }],
  "archive": [{ "month": "2026-11", "total": 2 }]
}
```

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
| 429 | 触发限流（P4 起启用） |
| 500 | 未预期异常（响应不含堆栈与 SQL） |

## 6. 约定

- DTO 接收请求、VO 返回响应，Entity 不直接暴露。
- 排序、分页字段走白名单，禁止把前端字符串拼进 SQL。
- 接口变更必须同步更新本文档与 `/v3/api-docs`。
