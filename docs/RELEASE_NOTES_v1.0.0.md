# Eason Blog v1.0.0 发布说明

> 发布日期：2026-11-06
> 发布包：`eason-blog-v1.0.0-linux.tar.gz`（Linux 部署）/ `eason-blog-1.0.0-source.zip`（源码）

## 1. 功能范围

- **前台**：首页、文章列表/详情、分类、标签、专栏、归档、搜索、留言板、关于页；
  文章目录、代码高亮/复制、阅读进度、评论/回复、点赞、收藏、通知、邮件订阅。
- **后台**：仪表盘、文章（Markdown 编辑/预览、封面上传裁剪、草稿/发布/下线、标签/分类/SEO）、
  分类、标签、专栏、评论审核、留言管理、资源管理（图片/视频/音频预览）、用户/角色、日志、系统设置。
- **认证**：账号名 + 密码登录（邮箱只用于验证码/订阅/找回密码）、HttpOnly Cookie、JWT + token_version；
  注册需邮箱验证码（开启邮件后）、账号名唯一且不可修改、协议勾选。
- **邮件**：SMTP 配置、测试发信、注册验证码、重置密码、订阅通知；模板可在后台修改。
- **工程**：Flyway V1–V12、统一响应/异常、限流、审计日志、备份脚本、CI。

## 2. 本次发布包含的安全修复

- 生产强制 `JWT_SECRET`，非 dev/test 拒绝仓库默认密钥；
- `ClientIp` 只信可信代理链，登录账号限流不再绑定可伪造 IP；
- 内存限流器按窗口过期清理，修复无界增长；
- 邮箱 token 原子消费；
- 上传拒绝 SVG 与 `text/html`；
- 文章删除改为物理删除并级联清理，slug 可复用；
- 发布通知改到事务提交后发送；
- 评论数据库分页，搜索过滤 `%`/`_` 并限流。

## 3. 升级注意事项

1. `/etc/eason-blog.env` 必须有 `JWT_SECRET`（≥32 字节），否则 prod 启动失败；
2. 新增 `TRUSTED_PROXIES`，填 Nginx/雷池地址或网段；
3. **V12 迁移会物理清理历史 `is_deleted=1` 的文章**及其关联数据；
4. 删除文章现在是永久删除（`restore` 接口已移除），只想隐藏请用“下线”；
5. 上传不再接受 SVG；已有 SVG 资源仍可访问，建议手工清理；
6. 登录改为账号名：管理员默认 `admin`，普通用户为注册时填写的账号名。

## 4. 已知限制

- 搜索基于 MySQL `LIKE`，数据量大时建议换全文索引/搜索引擎；
- 限流是单实例内存实现，多实例部署需换 Redis；
- 浏览器对 H.265/HEVC 视频支持有限，后台会提示“下载原文件”；
- 邮件模板为纯文本，不含 HTML 邮件；
- 未实现回收站，删除不可恢复（依赖备份）。

## 5. 构建信息

```bash
# 后端
mvn -f blog-server/pom.xml clean package -DskipTests
# 前台
cd blog-frontend && pnpm install && pnpm build
# 后台
cd blog-admin && ADMIN_BASE=/admin/ pnpm install && pnpm build
```

产物校验：

```bash
sha256sum -c SHA256SUMS
```

部署步骤见 `docs/LINUX_DEPLOY.md`。
