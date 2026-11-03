# 数据库设计（MySQL 8.x）

## 1. 总则

- 引擎 `InnoDB`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。
- 主键统一 `BIGINT AUTO_INCREMENT`；表名前缀 `blog_`，避开 `user`、`column` 等保留字。
- 时间字段统一 `create_time` / `update_time`，由应用层写入（不使用 `ON UPDATE CURRENT_TIMESTAMP`，便于审计与测试断言）。
- 业务表使用 `is_deleted` 逻辑删除；日志表不软删，按保留周期清理。
- 二进制文件不入库：`blog_resource` 只存元数据，文件落 `UPLOAD_DIR`。

## 2. 迁移

```
blog-server/src/main/resources/db/migration/
├── V1__init_schema.sql          # 建表
├── V2__seed_roles_permissions.sql # 角色/权限/分类/标签/配置等基础字典
└── db/dev/                       # 仅 dev profile 加载（演示数据）
```

规则：

1. 结构变更只允许新增 `V3__xxx.sql`、`V4__xxx.sql`；**已应用的迁移文件禁止修改**。
2. 每次变更同时更新本文档与 `docs/DEVELOPMENT_LOG.md`。
3. 生产 `spring.flyway.locations` 只含 `classpath:db/migration`，不会加载演示数据。
4. 管理员账号**不写入 SQL**，由 `AdminBootstrapRunner` 用环境变量创建。

本地执行：启动后端即自动迁移；测试库 `eason_blog_test` 每次测试由 Flyway 校验/重建。

## 3. 表清单

| 表 | 用途 | 关键约束/索引 |
| --- | --- | --- |
| `blog_user` | 账号、资料、状态、登录信息 | `uid`、`email` 唯一；`status`、`create_time` 索引 |
| `blog_role` | 角色（SUPER_ADMIN / USER） | `code` 唯一 |
| `blog_permission` | 权限点 | `code` 唯一 |
| `blog_user_role` | 用户-角色 | 唯一 `(user_id, role_id)`，双外键 |
| `blog_role_permission` | 角色-权限 | 唯一 `(role_id, permission_id)` |
| `blog_category` | 分类（parent_id 树） | `slug` 唯一；`(parent_id, sort)` |
| `blog_tag` | 标签 | `slug` 唯一 |
| `blog_column` | 专栏 | `slug` 唯一 |
| `blog_article` | 文章（Markdown 原文） | `slug` 唯一；`(status,publish_time)`、`(category_id,status)`、`(is_top,publish_time)`；`author_id` 外键 |
| `blog_article_tag` | 文章-标签 | 唯一 `(article_id, tag_id)` |
| `blog_column_article` | 专栏-文章 + 排序 | 唯一 `(column_id, article_id)`；`(column_id, sort)` |
| `blog_comment` | 评论/回复 | `(article_id,status,create_time)`、`parent_id` |
| `blog_guestbook` | 留言/回复 | `(status, create_time)` |
| `blog_favorite` | 收藏 | 唯一 `(user_id, article_id)` |
| `blog_article_like` | 点赞 | 唯一 `(user_id, article_id)` |
| `blog_notification` | 通知 | `(user_id,is_read,create_time)` |
| `blog_resource` | 资源元数据 | `stored_name` 唯一；`uploader_id` |
| `blog_system_config` | 站点配置 | `config_key` 唯一；`is_public` 控制是否对外 |
| `blog_announcement` | 公告 | — |
| `blog_friend_link` | 友链 | — |
| `blog_login_log` | 登录日志 | `create_time`、`user_id`、`login_status` |
| `blog_operation_log` | 操作日志 | `create_time`、`operator_id` |
| `blog_article_revision` | 文章修订历史 | `(article_id, create_time)` |

## 4. 核心字段

### blog_user

```sql
id BIGINT PK
uid VARCHAR(32) NOT NULL UNIQUE
email VARCHAR(128) NOT NULL UNIQUE
password VARCHAR(255) NOT NULL      -- BCrypt
nickname VARCHAR(50) NOT NULL
avatar VARCHAR(500) NULL
bio VARCHAR(500) NULL
status TINYINT DEFAULT 1            -- 0 禁用 1 正常
email_verified TINYINT DEFAULT 0
last_login_time DATETIME NULL
last_login_ip VARCHAR(45) NULL
register_time / create_time / update_time DATETIME NOT NULL
is_deleted TINYINT DEFAULT 0
```

### blog_article

```sql
id BIGINT PK
author_id BIGINT NOT NULL           -- FK -> blog_user.id
category_id BIGINT NULL
title VARCHAR(200) NOT NULL
slug VARCHAR(200) NOT NULL UNIQUE
summary VARCHAR(500) NULL
cover VARCHAR(500) NULL
content LONGTEXT NOT NULL           -- Markdown 原文（唯一内容源）
content_html LONGTEXT NULL          -- 可选缓存，使用前必须净化
content_format VARCHAR(20) DEFAULT 'markdown'
status TINYINT DEFAULT 0            -- 0 草稿 1 已发布 2 下线 3 回收
visibility TINYINT DEFAULT 0        -- 0 公开 1 私密
is_top / is_recommend TINYINT DEFAULT 0
view_count / like_count / comment_count BIGINT DEFAULT 0
seo_title VARCHAR(200) NULL
seo_description VARCHAR(500) NULL
publish_time DATETIME NULL
create_time / update_time DATETIME NOT NULL
is_deleted TINYINT DEFAULT 0
```

## 5. 一致性约定

- 浏览量用 `UPDATE ... SET view_count = GREATEST(view_count + 1, 0)` 原子自增，不做「读-改-写」。
- 点赞/收藏/文章标签/用户角色均有联合唯一键，重复写入由数据库兜底。
- 删除文章采用软删除；关联表（标签、专栏）的清理策略在 P2 实现时补充，避免悬挂引用。
- 不在 MySQL 中存图片/附件二进制。

## 6. 初始化数据

| 数据 | 来源 |
| --- | --- |
| 角色、权限、角色权限 | `V2__seed_roles_permissions.sql` |
| 分类、标签、专栏、站点配置、公告、友链 | `V2__seed_roles_permissions.sql` |
| 演示文章 | `DemoDataSeeder`（`app.demo-data.enabled=true` 且已存在管理员） |
| 管理员账号 | 环境变量 `BLOG_ADMIN_EMAIL` / `BLOG_ADMIN_PASSWORD` |

## 7. 备份与恢复

```bash
# 备份（含结构+数据）
mysqldump -u blog -p --single-transaction --routines --triggers eason_blog > eason_blog_$(date +%F).sql

# 恢复
mysql -u blog -p eason_blog < eason_blog_2026-11-03.sql
```

建议：每日全量 + binlog 增量；备份文件加密后异地保存；每季度做一次恢复演练。
