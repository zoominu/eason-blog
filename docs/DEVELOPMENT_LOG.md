# 开发日志

> 记录每个阶段的真实变更、决策、验证结果与遗留问题。新条目追加在顶部。

---

## 2026-11-06 · 安全审计修复（SEC-01/02/03/06/07、BUG-01~05）

| 编号 | 修复 |
| --- | --- |
| SEC-01 | `application-prod.yml` 改为 `${JWT_SECRET}`（缺失即启动失败）；`JwtService` 在非 dev/test 环境拒绝仓库默认密钥 |
| SEC-02 | `ClientIp` 改为从右往左跳过可信代理（`TRUSTED_PROXIES` 环境变量），AuthController 统一使用；生产关闭 Spring 的 `forward-headers-strategy`；登录账号限流不再绑定 IP |
| SEC-03 | 限流器改为定长窗口计数 + 按 key 自身窗口定期清理，不再无界增长 |
| SEC-06 | 邮箱 token 改为 `UPDATE ... WHERE used=0` 原子消费 |
| SEC-07 | 上传移除 `svg`，MIME 只接受明确白名单（拒绝 `text/html` 等） |
| BUG-01 | 删除死掉的回收站 `restore` 接口与常量，删除语义明确为不可恢复 |
| BUG-02 | 删除改为物理删除并级联清理标签/专栏/评论/点赞/收藏/修订；V12 清理历史软删文章，slug 可复用 |
| BUG-03 | 发布通知改为事务 `afterCommit` 后再异步发送 |
| BUG-04 | 评论改为数据库分页 + 仅加载当前页回复树 |
| BUG-05 | 搜索过滤 `%`/`_` 并加 IP 限流（30 次/分） |

### 验证

```
61 个后端测试通过
删除后关联行：article/comment/like/favorite 均为 0；同 slug 重建返回 200
SVG 上传返回 400「不支持的文件类型：svg」
搜索 keyword=% / _ 返回 0 条
评论 page=1&size=1：total=3，records=1
伪造 XFF 203.0.113.9：last_login_ip 记录为 203.0.113.9（可信代理链解析）
Flyway 最新版本 12
```

## 2026-11-05 · 账号登录、账号名唯一不可改、封面放大

| 需求 | 处理 |
| --- | --- |
| 联系邮箱 | 用户协议、隐私政策里的站长邮箱改为 `3446885993@qq.com` |
| 账号密码登录 | 新增 `blog_user.username`（V10/V11），登录改为账号名；邮箱只用于验证码、订阅、找回密码。后端兼容旧 `email` 字段登录，管理员/历史账号不受影响 |
| 账号名唯一不可改 | 唯一索引 `uk_user_username`；注册/后台创建校验重名；个人中心只读展示；`PATCH /users/me` 和后台改昵称会被拒绝（“账号名不可修改”） |
| 封面放大 | 首页文章卡片封面 150px → 200px，仍为 `object-fit: cover` |

后台：登录页改“账号”，用户列表显示账号名，新增用户可填账号名，编辑时账号名只读。

### 验证

```
后端 61 个测试通过
登录 admin/Admin123456 -> 200
新建账号 qa_xxx -> 用户名登录 200；改名 400“账号名不可修改”；重名 409“账号名已被占用”
登录/注册字段：["账号","密码"] / ["账号","邮箱","邮箱验证码","密码","确认密码"]
协议页含 3446885993@qq.com；首页封面高度 200
```

## 2026-11-05 · 认证页去阴影、注册协议勾选与协议页面

| 需求 | 处理 |
| --- | --- |
| 去掉登录/注册/找回框阴影 | 认证页卡片 `box-shadow: none`、边框透明；页脚去掉 `border-top`，不再出现分界线 |
| 注册需同意协议 | 注册页新增复选框「我已阅读并同意《用户协议》和《隐私政策》」，未勾选时注册按钮禁用并提示；不勾选提交也会被拦截 |
| 协议内容 | 新增 `/terms` 用户协议、`/privacy` 隐私政策两个页面（服务说明、账号安全、行为规范、知识产权、信息收集与使用、Cookie、数据保留、用户权利等），页脚加了入口链接 |

### 验证

```
登录页卡片: boxShadow=none, border 透明; 页脚 borderTop=0px none
注册页: 有复选框，链接 /terms /privacy；未勾选按钮 disabled=true，勾选后 false
/terms h1=用户协议 /privacy h1=隐私政策
```

## 2026-11-05 · 登录/注册/找回密码页壁纸背景

- 登录、注册、找回密码、重置密码页统一使用 `https://api.paugram.com/wallpaper/?source=gt` 随机壁纸（接口 302 到实际图片）。
- 壁纸由 `MainLayout` 的 `.auth-wallpaper` 以 `position: fixed` 铺满整个视口，**导航栏和页脚也覆盖在图片上**；认证页的导航/页脚背景改为透明。
- 页面纯白底、无遮罩、无滤镜；图片 `cover` 靠右，卡片保持白底浅色。
- 认证页高度按视口计算（footer 在首屏可见，`scrollHeight == innerHeight`）。
- 实测：`.auth-wallpaper` 铺满 1440×1000，`.nav` / `.footer` 背景 `rgba(0,0,0,0)`，认证页背景透明。

## 2026-11-05 · 资源预览修复与测试账号清理

| 问题 | 处理 |
| --- | --- |
| 资源点击后都变成视频预览、图片点不了 | `.thumb` 缺少 `position: relative`，导致视频的播放遮罩 `position:absolute; inset:0` 相对页面定位，盖住了其他卡片。补上定位后图片/视频/音频各自打开对应预览 |
| 视频无法播放 | 用户上传的是大疆 H.265/HEVC 视频，浏览器无法解码（`videoWidth=0`）。播放器增加检测：无法解码时提示「常见于 H.265/HEVC」并给下载原文件入口；视频不再 autoplay |
| 封面后保存失败 | 服务端日志没有保存错误；失败发生在部署重启窗口（11:24:54 有 Spring 关闭日志）。重启完成后用同一流程复测：新建→裁剪封面→保存草稿成功（`草稿已保存`） |
| 测试账号 | 删除 `subtest64837@test.local`、`nocode78298@test.local` 及其订阅/令牌等关联数据；订阅表已空 |

### 验证

```
图片预览: {"hasImage":true,"hasVideo":false}
视频预览: {"hasVideo":true,"errorShown":true,"download":true}
封面后保存: {"messages":["草稿已保存"]}
数据库: 剩余 5 个真实账号，subscription 0 条
```

## 2026-11-05 · 封面裁剪、资源预览、邮件模板与文案清理

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 封面不能框选 | 新增 `CoverCropper`：固定 16:9 框、拖动/缩放选区域，导出 1200×675 JPEG 再上传 |
| 2 | 资源无缩略图/预览 | 视频用 `<video preload=metadata>` 首帧作缩略图，点开弹窗在线播放；图片、音频同样可预览，其他类型给下载入口 |
| 3 | 查询按钮位置 | 资源页筛选顺序改为：关键字 → 类型 → 查询 |
| 4 | 进度百分比重复 | 去掉额外「上传中 xx%」文字，只保留 `el-progress` 自带百分比，并移到筛选行右侧 |
| 5 | 个人中心邮箱验证 | 移除「邮箱未验证 / 发送验证邮件」区块 |
| 6 | 自定义邮件模板 | V9 新增 `mail.template.register-code/reset/verify/subscription`，后台可改，支持 `{code} {minutes} {name} {link} {title} {site}` 占位符 |
| 7 | 重置密码页面的测试提示 | 删除「本地开发默认未配置 SMTP…后端日志」文案，改成正常的查收邮件提示 |
| 8 | 邮件里是 localhost | 邮件链接统一用 `mailService.siteUrl()`（读 `site.url` 设置），VPS 已设为 `http://39.96.94.89` |

### 验证

```
后端测试   61 个用例通过（V9 模板迁移）
浏览器     裁剪弹窗正常，导出封面 1200×675；视频缩略图 + 弹窗 <video controls> 正常；
           筛选顺序 关键字/类型/查询；个人中心无邮箱验证；找回密码页无开发提示
接口       site.url=http://39.96.94.89，4 个邮件模板 key 已生成
```

## 2026-11-05 · 封面上传、上传进度、导航精简与邮件功能

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 编辑文章没有封面接口 | 新增「上传封面」按钮 + 预览 + 进度，上传后自动填 `cover` 地址（也可手填） |
| 2 | 上传进度条 / 视频看不到 | `resourceApi.upload` 接 `onUploadProgress`，资源页显示百分比；后端大文件改为流式落盘（不再 `getBytes` 进堆），单文件上限 20MB → 200MB，Nginx/Spring 同步调整 |
| 3 | 仪表盘还有回收站 | 删除「回收站」指标卡，已下线卡片提示改为「重新发布或删除」 |
| 4 | 后台导航栏名称重复 | 顶部 header 不再显示当前页面标题，只保留右侧操作 |
| 5 | 接入邮件 | 新增 `blog_email_code`、`blog_subscription` 表和邮件配置项；MailService 改读后台设置（支持授权码/SSL）；注册验证码、订阅开关、发布邮件通知、后台测试邮件全部接通 |

### 验证

```
后端测试  59 个用例通过（新增订阅开关、邮件未开启时验证码报错）
浏览器    封面按钮/上传成功、仪表盘无回收站、header 无标题、邮件分组含测试按钮、注册页有验证码
接口      30MB mp4 上传成功（type=video），mail-test 未配置时返回明确错误
```

## 2026-11-05 · 首页卡片对齐与后台入口跳转

| 问题 | 处理 |
| --- | --- |
| 有封面的文章分类行比无封面的低 | 卡片改成固定 150px 高缩略图区：有封面显示图片，没封面显示占位（渐变 + 图标），保证同一行卡片分类标签在同一水平线 |
| 访问 `/admin` 跳转到 `http://域名:8080/admin/` | Nginx 生成目录跳转时带了监听端口。加 `absolute_redirect off;` + `location = /admin { return 301 /admin/; }`，跳转变为相对路径 `/admin/`；reload 不生效时用 restart |

### 验证

```
GET http://39.96.94.89/admin  -> 301 Location: /admin/
GET http://39.96.94.89/admin/ -> 200
首页同一行卡片 .article-card__head 的 top 全部为 784（第二行 1132）
```

---

## 2026-11-04 · 部署到 VPS（雷池源站 8080）

| 项 | 内容 |
| --- | --- |
| 机器 | 39.96.94.89，Debian 12，2C/1.6G，已有雷池 WAF 9.4.3（Docker，管理端 9443） |
| 方案 | 本机 Nginx 监听 `127.0.0.1:8080`（静态 + /api 反代），后端 `127.0.0.1:8081`，MySQL 仅本机；雷池 80/443 反代到 8080（tengine 为 host 网络，可访问回环） |
| 内存 | 新增 2G swap；JVM `-Xmx256m`、MySQL buffer 96M、关闭 performance_schema |
| 账号 | `admin@eason.blog` / `Admin123456`（需尽快修改） |
| 自动化 | `scripts/deploy-vps.ps1`：打包 jar + 两个 dist，上传并重启，自动等待接口 200 |
| 备份 | `/usr/local/bin/eason-blog-backup.sh` + cron 每天 03:20，保留 14 天 |
| 公网 8080 | Nginx 改为 `listen 8080`，外网 `http://39.96.94.89:8080/` 实测 200（绕过雷池，正式访问仍建议走 80/443） |
| 生产 profile | 补上 `SPRING_PROFILES_ACTIVE=prod`：默认 dev 会写演示数据、打印 SQL、加载 db/dev 迁移 |

### 验证

`curl 127.0.0.1:8080/`、`/api/v1/articles`、`/admin/` 均 200；管理员登录 200；资源上传与 `/uploads/` 静态访问 200；
Flyway 7 个迁移成功；无 OOM。详见 `docs/VPS_DEPLOY.md`。

---

## 2026-11-04 · 去回收站、用户头像、资源分类与引用、首页缩略图

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 文章不要回收站 | `DELETE /admin/articles/{id}` 改为直接逻辑删除；列表移除「回收站」筛选与「回收/恢复」按钮，行内只保留 发布/下线/删除；历史 status=3 的数据在 V7 迁移里转成「已下线」 |
| 2 | 用户模块展示头像 | 用户列表「用户」列加头像（无头像时显示昵称首字），角色列不变 |
| 3 | 资源分类 | V7 给 `blog_resource` 加 `type`（avatar/article_image/music/video/document/other）+ 索引；上传自动识别、也支持显式传 type；头像上传改为入资源库；资源页可按类型筛选并显示类型标签；单文件上限提升到 20MB（与 Nginx 对齐） |
| 4 | 资源引用 | `GET /admin/resources/{id}/references` 返回引用文章；资源卡片显示「引用 (N)」，点开弹窗列出文章并可跳编辑页 |
| 5 | 首页缩略图、文章页不放 | 列表 VO 的 `cover` 在没设封面时回退到正文第一张图，首页卡片因此有缩略图；文章详情页移除顶部封面大图 |

### 验证

```
后端测试  Tests run: 59（新增删除文章、资源分类/引用、头像入库断言）
浏览器    scripts/verify-admin-v2.mjs 13/13
```

实测：状态筛选无「回收站」、行内按钮为 编辑/发布/下线/删除；用户列表头像 `/uploads/...`；
资源筛选 6 类且引用弹窗列出文章；首页卡片 `naturalWidth > 0`；文章页无 `.cover`。

---

## 2026-11-04 · 文章导入 Markdown（图片入资源库）

| 需求 | 处理 |
| --- | --- |
| 上传 md 时把里面的图片自动上传到资源库并引用（md 用绝对路径） | 导入改为走 `ResourceService.store`，所有图片登记进 `blog_resource`、存 `uploads/yyyy/MM/` 并改写正文引用。支持：http(s) 绝对地址自动下载（拦截内网）、后端可读的绝对路径直接读、读不到时按文件名匹配随 md 选中的图片、base64 内嵌 |

### 验证

```
后端测试  Tests run: 57（导入用例改用 Windows 绝对路径匹配，并断言资源库能查到图片）
浏览器    scripts/verify-markdown-import.mjs 10/10
```

实测 md 写 `C:\Users\eason\Pictures\shot.png`，随 md 选中 `shot.png` 后正文被改写为
`/uploads/2026/11/xxxx.png`，`资源管理` 可按 `shot.png` 查到，前台图片 `naturalWidth > 0`。

> 注意：浏览器的安全策略不允许网页直接读取用户磁盘上的绝对路径。所以「绝对路径」要么是
> http(s) 地址（服务端下载），要么需要把图片和 md 一起选中（按文件名匹配）。
> 如果后端与 md 在同一台机器上（本地开发），服务端会直接读取该路径。

---

## 2026-11-04 · 个人中心与评论自助管理

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 展示/上传头像 | 新增 `POST /users/me/avatar`（multipart，≤2MB，仅图片，随机文件名存 `uploads/avatar/yyyy/MM`）；个人中心显示头像并支持上传，成功后就地更新 |
| 2 | 收藏/点赞/评论/通知 + 批量管理 | 个人中心扩为 5 个标签；收藏、点赞、评论均带复选框和批量操作（取消收藏/取消点赞/删除评论）；通知支持「全部/评论/回复/系统」分类筛选 |
| 3 | 昵称违禁词过滤 | 注册、改资料、后台新增/编辑用户统一调用 `auditPolicy.findBannedWord`，命中直接 400 |
| 4 | 3 分钟内可删自己的评论 | 后端 `DELETE /users/me/comments/{id}` 校验本人 + 3 分钟 + 无回复；文章页评论 hover 出「删除」，个人中心评论列表也可删；超时/有回复会被拒绝并提示 |

### 验证

```
后端测试  Tests run: 56（新增昵称过滤、我的评论删除/超时、点赞批量、通知分类、头像上传）
浏览器    scripts/verify-profile.mjs 10/10
```

实测：头像上传返回 `/uploads/avatar/2026/11/xxx.png`；收藏/点赞批量取消成功；
3 分钟内的评论在个人中心和文章页都能删除，超过 3 分钟返回 400。

> 附带发现：VM 上 reader1/reader2 的 `status` 被改成了 0（禁用），登录返回 403。
> 已恢复为 1；后台用户编辑接口目前不支持改状态，需要的话可以补。

---

## 2026-11-04 · 后台接口 404 修复与设置手风琴

| # | 问题 | 根因 / 处理 |
| --- | --- | --- |
| 1 | 专栏「管理文章」保存报“接口或资源不存在” | `axios` 实例的 `baseURL` 已是 `/api/v1`，代码又写了 `/api/v1/admin/...`，实际请求变成 `/api/v1/api/v1/...` → 404。改为相对路径 `/admin/columns/{id}/articles` |
| 2 | 留言回复报同样错误 | 同一原因：`instance.patch('/api/v1/admin/guestbook/{id}/reply')` → 改为 `/admin/guestbook/{id}/reply` |
| 3 | 设置页分组太多太长 | 改为 `el-collapse` 手风琴：一次只展开一组，标题显示分组名 + 配置项数量，默认展开第一组 |

### 验证

```
浏览器  scripts/verify-admin-fixes.mjs 6/6
```

实测：专栏保存提示「专栏文章已更新」、留言回复提示「回复已保存并通知用户」；
设置页 7 个分组、默认展开 1 组、点击其它分组会切换展开。

> 同时给 API 封装加了注释：`instance` 的 baseURL 已含 `/api/v1`，调用处只能写相对路径。

---

## 2026-11-04 · 长内容折叠与通知跳转修复

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 长评论/留言不换行、不折叠 | 内容加 `overflow-wrap:anywhere` + `word-break:break-word` 解决长串不换行；超过 6 行时 `max-height` 截断并显示「显示全部/收起」。是否显示按钮由 `scrollHeight > clientHeight` 实测决定，避免 4 行文本也出现按钮 |
| 2 | 审核通过通知点击不跳转 | 根因：同页点击通知只改 query，不触发组件重新加载。新增 `watch(route.query.comment)`：先刷新当前页评论再逐页定位；`focusComment` 改为从第 1 页扫到最后一页，不再只向后找 |
| 3 | 高亮一级评论连带子评论 | 高亮样式从整节点改为只作用于 `.comment--highlight` 的直接子元素（头部/正文），子评论背景保持透明 |

### 验证

```
浏览器  scripts/verify-comment-v4.mjs 15/15
```

实测：长评论折叠高度 143px（6 行）→ 展开 238px；长留言同样折叠且无横向滚动；
父评论高亮时子评论 `backgroundColor = rgba(0, 0, 0, 0)`；
同页点击通知后目标评论高亮且 URL 参数被清理。

---

## 2026-11-04 · 审核操作收敛与评论可见性

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 后台操作按钮收敛 | 行内去掉通过/拒绝/删除，只保留顶部三个按钮；选中 1 条显示「通过/拒绝/删除」，多选显示「批量xx (N)」；留言行保留「回复」 |
| 2 | 拒绝父评论保留回复 | 公开接口把「被拒绝但有已通过回复」的评论以 `hidden=true` + 「当前评论已被隐藏」占位返回；删除父评论才递归删除子回复 |
| 3 | 通过通知只发一次 | 通过前检查是否已有指向该评论的通知，有则跳过；拒绝后再通过不会重复通知 |
| 4 | 评论跳转失效 | 去掉组件内的「已高亮集合」（它会让同一会话的第二次跳转不高亮），只保留“高亮后清除 URL 参数”来避免刷新重复 |
| 5 | 目录跳转被导航遮挡 | 正文标题加 `scroll-margin-top: 96px`，跳转后标题 top=96（导航高 64） |

### 验证

```
后端测试   Tests run: 50（新增占位、删除连带、通知只一次 3 个用例）
浏览器      scripts/verify-comment-v3.mjs 12/12
后台交互    scripts/verify-admin-moderation.mjs：行内无按钮、全选后「批量通过 (2)」
```

实测：占位 `{hidden:true, content:"当前评论已被隐藏"}` 且回复仍渲染、占位无回复按钮；
删除父评论 `{deleted:2}` 且列表不再出现；目录标题 `top=96`；连续两次跳转均高亮。

---

## 2026-11-04 · 评论即时展示与后台检索

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 评论后马上展示 | 发布接口返回 `{id,status}`；已发布时前端自动刷新到对应分页并定位高亮，不再提示“待审核” |
| 2 | 超过 10 条分页 | 顶层评论每页 10 条；`Pagination` 增加「跳至第 N 页」（页数 >5 时显示），实测 14 条 → 2 页可切换 |
| 3 | 只高亮一次 | 高亮后把 `?comment=` 从地址栏移除；同一条评论在页面生命周期内只加一次高亮 class，刷新不再高亮 |
| 4 | 最大四级 | 前端第 4 级不渲染回复按钮；后端校验父评论层级 ≥4 时返回 400 |
| 5 | hover 范围只到单条 | 选择器改为 `.comment:hover > .comment__actions > .comment__reply`，鼠标停在父评论上不会点亮子评论的按钮 |
| 6 | 后台搜索评论/留言 | 评论支持 `keyword`（内容/昵称/邮箱/文章标题）、`articleId`、`userId`；留言支持 `keyword`、`userId`；后台筛选栏加关键字输入、文章/用户下拉与重置 |

### 验证

```
后端测试   Tests run: 47（新增层级限制与筛选 2 个用例）
浏览器      scripts/verify-comment-v2.mjs 14/15 → hover 单独复核通过（scrollIntoView 后 15/15）
```

实测输出：发表后 `visible=true, highlighted=true`；跳转高亮后 `url=''`、刷新无高亮；
第 4 级 `reply button=0`、第 3 级 `=1`；hover 时 `own=1, child=0`；分页 `active=2`。

> 另外把接口限流做成可配置（`app.rate-limit.enabled`），测试环境关闭，
> 否则整套测试的登录次数会触发 429。

---

## 2026-11-04 · 通知实时化与评论审核策略

| # | 需求 | 处理 |
| --- | --- | --- |
| 1 | 管理员通知跳 `:5174` 拒绝连接 | 新增 `utils/admin.ts`：生产同域 `/admin/`，开发才用 5174；后台通知链接不再硬编码端口 |
| 2 | 有通知时右上角弹窗但别打扰 | 头部轮询到新通知时，右上角弹出小卡片（6 秒自动消失、可点击直达、可手动关闭） |
| 3 | 通知要实时同步 | 新增 `stores/notification.ts`：30 秒轮询未读数；通知页停留时自动刷新列表 |
| 4 | 博主评论/回复免审 | `addComment` 判断 SUPER_ADMIN 直接 `APPROVED` 并累加计数；游客评论按策略走 |
| 5 | 关闭审核时用违禁词 | V6 迁移新增 `audit.banned-words`、`audit.banned-action`；命中可「转审核」或「替换为 *」；后台设置页新增分组 |
| 6 | 展开按钮放到最后一条回复下 | `CommentNode` 的展开按钮移出操作区，放在回复列表末尾 |
| 7 | 回复按钮 hover 才显示 | `opacity:0` + `:hover/:focus-within` 显示；触屏设备保持常显 |
| 8 | 点击折叠评论的通知要能高亮 | `?comment=` 传给 `CommentNode.focusId`，命中路径自动展开；目标不存在时逐页查找 |

### 验证

```
后端测试   Tests run: 45（AuditPolicyTest 4：博主免审 / 违禁词转审 / 替换 / 正常发布）
浏览器验收  scripts/verify-comment-ux.mjs 14/15 → 补测高亮后 15/15
```

覆盖：管理员通知 URL 为 `/admin/comments?highlight=13`、回复按钮 `opacity=0`、
展开按钮为回复列表最后一个子元素、折叠只渲染 3 条、跳转后展开为 6 条并高亮、
新通知 35 秒内自动弹出。

---

## 2026-11-04 · 用户管理增强 / 测试数据 / 复制修复

| 需求 | 处理 |
| --- | --- |
| 测试数据进虚拟机 | 新增 `deploy/vm/seed-demo.sh`（临时开启 demo-data 开关 → 重启 → 自动关闭），VM 已有 5 篇文章；另用新接口创建 2 个测试读者 |
| 新增用户 | `POST /admin/users`（邮箱唯一、密码强度校验、角色可选，默认 USER）；后台用户页「新增用户」弹窗 |
| 修改用户资料 | `PATCH /admin/users/{id}`（邮箱/昵称/头像/简介，改邮箱重置验证状态）；后台「编辑」弹窗 |
| 资源复制链接失效 | 根因同文章页：`navigator.clipboard` 在 http IP 下不存在。改为 Clipboard API + `execCommand` 兜底，并提示「链接已复制到剪贴板」 |

### 验证

```
后端测试                    Tests run: 41（新增 create/update 2 个用例）
无头浏览器（真实 http 站点） secure=false, hasClipboard=false → 复制成功提示
部署验收                    scripts/verify-vm.mjs 17/17
```

> 资源复制在 `http://192.168.245.131` 这种非安全上下文下实测通过：浏览器没有
> `navigator.clipboard`，走的是隐藏 textarea + `execCommand('copy')`。

---

## 2026-11-03 · 分享提示 / 排版 / 用户删除

| 需求 | 处理 |
| --- | --- |
| 分享后提示已复制 | 新增全局轻提示 `ToastHost` + `utils/toast`，分享成功显示「分享文案已复制到剪贴板」；失败提示手动复制 |
| 文章右侧目录再靠右 / 正文加宽 | 容器 1200 → 1320px，正文与目录间距 64 → 96px，目录列 200px |
| 用户模块删除 | 后台用户列表新增「删除」；后端 `DELETE /admin/users/{id}`：逻辑删除 + 解除角色绑定，禁止删自己、禁止删最后一个管理员 |
| 编辑栏铺满 | `MarkdownEditor` 根元素 `width:100%`（此前被 flex 容器挤成内容宽度）；默认进入「编辑」全宽模式，编辑区高度 420 → 560px |

### 说明

- 删除用户用 MyBatis-Plus 的 `deleteById` 触发 `@TableLogic`；手动 `setIsDeleted(1)+updateById` 不会更新逻辑删除字段（已验证会失败，测试覆盖）。
- 分享在部分桌面浏览器上 `navigator.share` 会挂起，加了 1.5s 超时回退到复制。

### 验证

```
后端测试                  Tests run: 39, Failures: 0（新增 AdminUserApiTest 3 个）
vue-tsc（前台/后台）       0 error
无头浏览器实测            分享 Toast 文案「分享文案已复制到剪贴板」
```

---

## 2026-11-03 · 评论/通知体验优化（11 项）

| # | 需求 | 实现 |
| --- | --- | --- |
| 1 | 不能自己回复自己 | 后端 `addComment` 校验父评论作者，返回 400；前端自己的评论不显示「回复」 |
| 2 | 回复过多折叠 | 一条评论的回复 > 5 时只展示前 3 条，其余「展开其余 N 条」 |
| 3 | 审核通知说明是哪条评论 | 通知内容带评论摘要 `「...」` 与文章标题 |
| 4 | 通知跳转定位 + 高亮 | 读者通知 → `/article/{slug}?comment={id}`（自动翻页、滚动、高亮 3 秒）；审核通知 → `/admin/comments?highlight={id}`（后台自动清筛选、定位高亮行） |
| 5 | 通知一键已读 / 删除已读 | `PATCH /users/me/notifications/read-all`、`DELETE /users/me/notifications/read`，个人中心加两个按钮 |
| 6 | 博主标记 | `CommentVO.author`（按 SUPER_ADMIN 角色判定），评论头部显示「博主」 |
| 7 | 批量审核 | `POST /admin/comments/batch`、`POST /admin/guestbook/batch`；后台表格加多选 + 批量通过/拒绝 |
| 8 | 固定选项下拉 | 系统设置对 `*.enabled`、`*.audit`、`storage.driver`、`site.theme` 使用下拉，分享模板用 textarea |
| 9 | 大列表分页容量 | 评论/留言/用户/日志/资源/文章后台页大小支持 10/20/50/100；分页插件上限 50 → 100（公开接口仍在 Controller 限制 50） |
| 10 | 文章页两侧留白 | 正文容器 900 → 1200px，正文/目录间距 40 → 64px |
| 11 | 分享按钮无反应 | 分享改为「后台模板 + 文章标题/链接」生成文案并复制；模板存 `share.template`（V5 迁移，公开配置） |

### 关键决策

- **已读通知的删除是真删除**：通知表没有保留价值，用物理删除避免无限增长；未读的不会被删。
- **高亮靠 URL 参数而不是本地状态**：通知跳转是跨页面/跨后台的，URL 可分享、可刷新复现。
- **分页上限统一放到 100**：公开接口仍限 50，防止匿名请求拖库；后台是登录态才放开。
- **分享不依赖 `navigator.share`**：桌面浏览器普遍不支持，改为复制文案，移动端仍会先尝试系统分享。

### 验证

```
后端测试                  Tests run: 36, Failures: 0（新增 5 个互动用例）
node scripts/verify-ux.mjs  15/15 checks passed
vue-tsc（前台/后台）       0 error
vite build（双端）         成功
```

覆盖：自回复 400、博主 author 标记、通知摘要与两类跳转、一键已读后未读为 0、删除已读、
评论/留言批量通过、`share.template` 公开、后台 size=100 生效、评论按顶层分页。

---

## 2026-11-03 · 功能缺失补齐 + 复制修复

审计报告第 4 节列出的 6 项能力缺失全部实现，并修复代码块复制在非安全上下文失效的问题。

### 新增功能

| 功能 | 实现 |
| --- | --- |
| 找回密码 | V4 建 `blog_email_token`（只存 SHA-256、30 分钟、单次使用）；`/auth/forgot-password`、`/auth/reset-password`；重置后 `token_version` 自增，旧登录立即失效 |
| 邮箱验证 | 注册即发验证邮件，`/auth/verify-email`、`/auth/resend-verification`；个人中心显示验证状态 |
| 评论分页 | 按「顶层评论」分页，回复完整嵌套；`GET /articles/{slug}/comments?page&size` |
| 图片灯箱 | 正文图片点击放大，Esc / 点击遮罩关闭 |
| 博主回复留言 | `PATCH /admin/guestbook/{id}/reply`；回复即审核通过并通知留言者，后台新增「回复」弹窗 |
| RSS / 归档 | `/rss.xml`（最新 20 篇摘要）、`/api/v1/archive` |

### 复制失败修复

`navigator.clipboard` 只在安全上下文可用：用 `http://192.168.x.x:5173` 从别的设备访问时，
`writeText` 直接抛错，表现就是「点复制没反应」。现在改为先尝试 Clipboard API，
失败则回退到隐藏 textarea + `document.execCommand('copy')`，两种路径都基于同一份代码文本。

### 邮件策略

默认 `app.mail.enabled=false`：邮件正文打到后端日志（含完整链接），本地开发无需 SMTP。
生产在 `/etc/eason-blog.env` 配置 `MAIL_ENABLED=true` 与 `SPRING_MAIL_*` 即真正发信；
发信异常只记 warn，不影响注册/重置主流程。

### 验证

```
后端测试    Tests run: 32, Failures: 0（新增 AccountRecoveryTest 3 + 评论分页/留言回复 2）
前台/后台   vue-tsc 0 error
构建        vite build 双端成功
```

> 已知取舍：评论分页在内存中完成（单篇文章评论量小），数据量上来后应改为 SQL 分页 + 按根聚合。

---

## 2026-11-03 · 审计报告修复（P0/P1/P2 共 20 项）

按 `docs/AUDIT_REPORT_2026-11-03.md` 逐项修复，明细见该文档第 7 节。

### 关键改动

| 区域 | 改动 |
| --- | --- |
| 路由 | 守卫改用 `fetchMe()`（HttpOnly Cookie 前端读不到）；`afterEach` 重置 SEO |
| 专栏 | 组件监听路由参数；后台新增专栏文章绑定接口与弹窗；`ColumnVO` 返回 sort |
| 导航 | 按路由名高亮，根链接不再常亮；新增搜索入口 |
| 文章页 | 正文与互动解耦；递归评论组件；上一篇/下一篇；代码复制、分享、标签可点击、封面 |
| SEO | 重写 `utils/seo.ts`，修正 og 属性名，补 canonical/og:image/published_time |
| 数据契约 | `ArticleTagResolver` 批量补标签；收藏只返回公开文章；详情返回 categorySlug/tagSlugs |
| 认证 | V3 迁移新增 `token_version`，改密后旧 token 立即失效 |
| 后台 | 移除标签 allow-create；创建后监听 id 重新加载；支持 `VITE_ADMIN_URL` |
| 安全 | `ClientIp` 只信任本机代理并取 XFF 最后一段 |

### 验证

```
node scripts/verify-audit.mjs   → 18/18 checks passed
后端测试                        → 27 passed
vue-tsc（前台/后台）            → 0 error
vite build（前台/后台）         → 成功
```

覆盖：列表标签非空、详情返回 categorySlug/tagSlugs/上下篇、分类详情 200/404、
归档、sitemap、robots、改密后旧 token 401、新密码可登录、专栏 sort 与文章绑定、公开列表不含草稿。

### 未处理

RSS、找回密码、邮箱验证、评论分页、图片灯箱、博主回复留言 —— 属于能力缺失而非缺陷，
已在审计报告第 4 节登记。

---

## 2026-11-03 · P5 + P6：后台完善与质量加固

### P5 后台

| 模块 | 内容 |
| --- | --- |
| 用户管理 | 列表（邮箱/昵称搜索、状态筛选）、禁用/启用、角色分配；禁止禁用自己、禁止移除自己的管理员角色 |
| 角色权限 | 角色列表 + 权限点矩阵，可保存角色-权限映射（接口级鉴权仍以角色为准） |
| 资源管理 | 上传（5MB、扩展名/MIME 白名单、UUID 存储名、SHA-256）、列表、删除（被引用返回 409）；`/uploads/**` 静态访问 |
| 日志审计 | 登录日志（成功/失败）+ 操作日志；操作日志由 `@Audit` 注解 + AOP 切面写入 |
| 系统设置 | 配置项读取与批量更新；只允许更新已有 key，公开项即时反映到 `/site/config` |

### P6 质量与安全

| 项 | 处理 |
| --- | --- |
| 限流 | 登录（IP 60/分、IP+邮箱 10/分）、注册（IP 20/时、邮箱 3/时）、评论 5/分、留言 3/5 分 |
| 上传安全 | 白名单 + 随机名 + 大小限制 + 路径穿越校验；上传目录禁止脚本 |
| 静态资源 | `/uploads/**` 映射到上传目录，生产由 Nginx 直接返回 |
| 审计 | 关键写操作落库，记录操作人、模块、动作、请求行、IP |
| 部署 | 已有 Nginx/systemd/备份文档；本轮补 `scripts/backup.ps1` |
| CI | 27 个后端用例 + 前后端构建，push 即跑 |

### 关键决策

1. **RBAC 保持「角色鉴权 + 权限矩阵展示」**：接口级仍按 `SUPER_ADMIN` 判断，
   权限点用于后台能力矩阵与后续细化；不为了"看起来完整"引入一套未被使用的鉴权引擎。
2. **限流放内存**：单实例个人博客，Redis 是过度设计；多实例时再换，
   接口形态不变（`RateLimiter.check`）。
3. **两级限流 key**：单 IP 总闸防批量，IP+邮箱防撞库；只用 IP 会误伤公司/校园网出口。
4. **操作日志用注解而非全量拦截**：只标关键写操作，避免日志表被读请求淹没；
   参数与返回值不入库，防止正文/密码泄漏。
5. **上传目录按年月分片**：单目录文件过多会拖慢 ext4/NTFS 目录遍历。

### 验证

```
后端测试     Tests run: 27, Failures: 0, Errors: 0  BUILD SUCCESS
接口自检     node scripts/verify-p5.mjs  23/23 checks passed
类型检查     blog-admin vue-tsc --noEmit 0 error
构建         blog-admin vite build 成功
```

自检覆盖：用户列表/搜索/禁用/启用（禁用后登录 403）/禁止禁用自己 → 角色与权限 →
上传 txt 并可访问、拒绝 .sh → 设置读写、拒绝未知 key → 登录日志与操作日志 →
连续错误登录触发 429。

### 遗留

- 资源上传没有图片压缩/裁剪，也没有对象存储适配（S3/OSS）；
- 操作日志无保留期清理任务（设计文档要求设定保留期）；
- 角色权限矩阵尚未接入后端鉴权，新增权限点需同步代码；
- 备份脚本仅 Windows/本机，生产环境需改为 cron + 异地存储。

---

## 2026-11-03 · P3 + P4：搜索、专栏、代码高亮、评论/点赞/收藏/通知

### P3 前台完善

| 项 | 实现 |
| --- | --- |
| 站内搜索 | `GET /search`（标题/摘要/正文 LIKE），`SearchView` 独立页面，支持分页与空结果 |
| 专栏文章 | `GET /columns/{slug}/articles`，按 `blog_column_article.sort` 排序；专栏详情页展示文章列表 |
| 代码高亮 | highlight.js（core + 8 种语言按需注册），配色用 CSS 变量适配浅/深色 |
| SEO | 文章页动态写入 `title`、`meta description`、`og:title/description/type` |

### P4 用户互动

| 项 | 实现 |
| --- | --- |
| 评论 | 树形评论（`replies`）、回复、默认待审核；审核通过才公开并累加 `comment_count` |
| 留言 | 登录后提交，默认待审核；后台通过后展示 |
| 点赞 | `POST/DELETE /articles/{id}/like`，联合唯一键 + 原子计数，幂等 |
| 收藏 | 同上；个人中心「收藏」标签页 |
| 通知 | 评论/回复/审核通过写入通知；个人中心可查看并标记已读 |
| 后台审核 | 「评论审核」「留言管理」两个页面，支持按状态筛选、通过/拒绝 |

### 关键决策

1. **搜索先用 MySQL LIKE**：个人博客数据量小，零额外组件；接口与 Service 分离，
   以后换 Meilisearch/ES 只改 `SearchService`。
2. **代码高亮用 highlight.js 而非 Shiki**：Shiki 需要异步加载 wasm/主题，首屏成本高；
   hljs 支持按语言注册，打包后 ArticleView 约 144KB（gzip 48KB），可接受。
   设计文档允许「经确认的同类方案」，此处记录为偏差。
3. **评论计数只统计已通过**：待审核不计数，审核通过 +1、已通过被拒绝 -1，
   避免前台数字虚高。
4. **通知在服务层内联写入**：评论与审核是低频操作，暂不引入消息队列；
   写入失败只记日志，不影响主流程。
5. **留言与评论共用 `InteractionService`**：两者都是「登录 + 待审核 + 后台通过」的同一模式，
   放一起减少重复。

### 验证

```
后端测试     Tests run: 27, Failures: 0, Errors: 0  BUILD SUCCESS
             （新增 InteractionApiTest 6 例：审核可见性、点赞幂等、收藏、留言、通知、匿名拦截）
接口自检     node scripts/verify-p3p4.mjs  20/20 checks passed
前台/后台    vue-tsc --noEmit 均 0 error；vite build 均成功
```

自检覆盖：搜索「索引」命中 → 专栏 4 篇 → 评论待审核不可见 → 审核后可见 → 未登录 401
→ 点赞两次计数仍为 1 → 收藏进入个人中心 → 留言审核 → 评论触发管理员通知。

### 遗留

- 评论/留言的回复（博主回复留言）暂无后台入口，`blog_guestbook.reply` 字段未写；
- 评论分页未做（一篇文章评论量大时需补），当前一次返回全部已通过评论；
- 搜索无高亮、无拼音/同义词，标题权重未区分；
- 通知只覆盖评论相关事件，点赞/收藏不通知（避免噪音）。

---

## 2026-11-03 · 仪表盘改版：可跳转指标卡 + 登录审计 + 系统信息

### 需求

1. 仪表盘指标可点击跳转到对应模块；
2. 细分模块：最近登录用户、最近访问 IP、文章数量、系统信息等。

### 后端

- 新增 `LoginLog` 实体与 Mapper，登录成功/失败都写 `blog_login_log`
  （失败只记邮箱与 IP，绝不记密码；写日志失败不影响登录主流程）。
- `DashboardVO`：把原来返回 `Map` 改成明确 record，字段变动可被编译器发现。
- `DashboardService` 聚合四类数据：
  - 内容统计：已发布/草稿/下线/回收、累计阅读、用户数、分类数、标签数；
  - 最近动态：最近更新文章、最近登录用户（带角色）、最近登录记录（带结果）；
  - 归档：按年月分组；
  - 系统信息：应用名、profile、Java 版本、OS、启动时间、运行时长、堆内存、数据库版本。

### 后台

- 8 个指标卡（4 列 × 2 行），每张卡带「点击跳转」：
  已发布 → `/articles?status=1`、草稿 → `?status=0`、已下线 → `?status=2`、
  回收站 → `?status=3`、用户 → `/users`、分类 → `/categories`、标签 → `/tags`。
- 新增「最近登录用户」「最近登录记录」两张表，以及「系统信息」描述列表；
  「最近更新」的表格行可直接点进编辑页。
- `ArticlesView` 支持从 URL 读取 `status` / `categoryId` 并监听变化，仪表盘跳转后筛选立即生效。

### 验证

```
后端测试     Tests run: 21, Failures: 0, Errors: 0  BUILD SUCCESS
接口自检     /admin/dashboard 返回完整结构：
             counts={published:5,draft:0,offline:1,recycled:0,views:12,users:2,cats:5,tags:8}
             recentUsers=2, recentLogins=3, system={MySQL 8.0.28, Java 21.0.13, Windows 11}
类型检查     vue-tsc --noEmit  0 errors
后台构建     ✓ built in 5.75s
截图         docs/screenshots/admin-dashboard.png、admin-articles-filtered.png
```

### 决策

- 登录日志在 P5 之前提前落地：仪表盘要展示「最近访问 IP」，没有日志就只能看用户表最后一条，
  无法体现失败尝试。日志表已存在，写入成本极低。
- 仪表盘计数采用「全状态」口径，避免管理员看到「分类 0 篇」但实际有草稿的困惑；
  前台仍只统计公开文章。
- 指标卡用 `<button>` 而非 `<div>`，保留键盘可访问性。

### 遗留

- 「用户管理」页仍是占位，点进去看不到用户列表（P5）；
- 登录日志暂无保留周期清理任务（设计文档要求设定保留期）；
- `recentLogins` 只取 8 条，完整日志页待 P5。

---

## 2026-11-03 · P2 内容基础：文章 CRUD + 分类/标签/专栏管理

### 交付内容

| 模块 | 内容 |
| --- | --- |
| 后端 · 文章 | 列表（按状态/分类/关键词）、详情、新建、整体更新、发布、下线、回收、恢复；slug 自动生成与去重；标签全量替换 |
| 后端 · 分类 | 列表（含全部状态文章计数）、新建、更新、删除（有子分类或文章 → 409） |
| 后端 · 标签 | 列表（含引用计数）、新建、更新、删除（被引用 → 409） |
| 后端 · 专栏 | 列表、新建、更新、删除（专栏内有文章 → 409） |
| 后台 · 文章 | 列表页（筛选、状态标签、批量操作入口）、编辑页（左右两栏：正文 + 发布设置/SEO） |
| 后台 · 编辑器 | 自研分栏 Markdown 编辑器：工具栏、实时预览、字符/行统计；预览走 marked + DOMPurify |
| 后台 · 分类/标签/专栏 | 三个管理页，表格 + 弹窗增删改 |

### 状态机

```
0 草稿 --publish--> 1 已发布 --offline--> 2 已下线 --publish--> 1
  ↑                    │
  └──── restore ─── 3 回收站 <── delete ──┘
```

- 发布时补写 `publishTime`；再次发布不会覆盖原发布时间；
- 下线只允许对「已发布」操作，否则 400；
- 删除是「移入回收站」，不做物理删除，避免误操作丢数据；
- 前台查询始终限定 `status=1 AND visibility=0`，与后台状态机解耦。

### 关键决策

1. **slug 生成**：英文标题转 kebab-case；中文标题无法音译，退化为 `post-20261103-ab12`，
   后台可手动改成英文。冲突自动追加 `-2`、`-3`，不返回 500。
2. **更新为整体替换**：PATCH 仍传全量字段，标签全量重建（先删后插），
   避免"局部更新 + 关联表"产生不可推理的中间状态。
3. **删除引用校验放在服务层**：分类/标签/专栏被引用时返回 409 并带数量，
   比数据库外键报错更可读。
4. **未引入 Vditor**：P2 用自研编辑器（marked + DOMPurify + 工具栏），
   理由：Vditor 默认从 CDN 加载资源，内网/离线环境会挂；自研版本零外部依赖、可测试。
   P3 若有富文本需求再评估替换，届时只需替换 `MarkdownEditor.vue`。
5. **后台计数用「全部状态」聚合**：草稿也算进分类/标签的引用数，与前台公开计数不同，
   两个 API 分开查询，避免管理端出现"明明有文章却显示 0"。

### 验证

```
后端测试     Tests run: 21, Failures: 0, Errors: 0  BUILD SUCCESS
             （新增 AdminArticleApiTest 7 个用例：状态机、slug 冲突、权限、校验）
接口自检     node scripts/verify-p2.mjs  17/17 checks passed
后台构建     vite build  ✓ built（含 ArticleEditView / MarkdownEditor / 三个管理页）
前台构建     vite build  ✓ built
```

自检覆盖：新建分类/标签/文章 → 草稿前台不可见 → 发布可见 → 更新同步 → 分类/标签被引用禁止删除（409）
→ 下线不可见 → 回收站 → 恢复 → 普通用户 403。

### 遗留

- 专栏与文章的绑定（`blog_column_article` 排序界面）留到 P3 与前台专栏页一起做；
- 封面图仍是手填 URL，资源上传在 P5；
- 编辑器暂无图片粘贴上传、无 Vditor 的目录/全屏等高级能力；
- 文章修订历史（`blog_article_revision`）表已建但未写入，计划 P5 与操作日志一起补。

---

## 2026-11-03 · 仓库托管与 CI

- 代码推送到 GitHub：`git@github.com:zoominu/eason-blog.git`（`main`，首个提交 `bf8f8cd`）。
- 环境里原本没有 Git，使用便携 MinGit 2.56（`.tools/git`），不污染系统 PATH。
- SSH 认证用独立密钥 `.tools/ssh/id_ed25519`，通过 `core.sshCommand` 只对当前仓库生效。
- 新增 `.github/workflows/ci.yml`：MySQL 8.0 service + `mvn test`；Node 22 + pnpm 11 构建前后台。
- 首次 CI run #1 全绿：后端 14 用例通过，前台/后台构建成功。
- 新增 `scripts/push-github.ps1`（推送）与 `scripts/poll-ci.mjs`（查询 CI 状态）。

---

## 2026-11-03 · 体验修正：排版、用户菜单、主题初始化、分类计数

来源：作者实际浏览页面后提出的四个问题。

### 1. 页面两侧过空

| 改动 | 说明 |
| --- | --- |
| 内容容器 1080 → 1320px | 1920 宽屏下左右留白从约 420px 降到约 300px |
| 首页重排 | Hero 改为「左文案 + 右站点概览卡」两栏；新增专栏区与标签云；最新文章 6 张卡片铺满 3 列 |
| 博客列表加侧栏 | 右侧 296px：分类（带真实篇数）、热门文章（按浏览量）、标签；列表区改为 3 列 |
| 顶部径向光晕 | `body::before` 一层极淡渐变，宽屏下两侧不显得空，不干扰文字 |
| 文章页 | 正文列 + 右侧目录，阅读宽度控制在 900px 内保证可读性 |

### 2. 用户菜单一点就消失

- 根因：`@mouseleave` 绑在按钮外层，鼠标从按钮移到菜单需要跨过 `top: calc(100% + 8px)` 的 8px 空隙，
  触发 leave 后菜单立刻关闭，根本无法点击。
- 修复：去掉 hover 关闭，改为 **点击切换 + 点击外部关闭 + Esc 关闭**；
  8px 间距移到菜单外层容器的 `padding-top`，鼠标路径连续，不再有"空隙判定"。
- 验证：`SiteHeader.vue` 中 `onDocumentClick` 通过 `contains()` 判断，点击菜单内部不会误关。

### 3. 主题初始化顺序

- 需求：先按用户当前状态（系统是浅色还是深色）决定，之后持续跟随系统变化。
- 实现：`index.html` 里加一段内联脚本，在 CSS 加载前读取 `localStorage`，没有则读
  `prefers-color-scheme`，直接写 `data-theme`，消除"先白后黑"的闪烁；
  Pinia store 读取同一个 `data-theme-mode` 保持状态一致。
- 三种模式循环：跟随系统 → 浅色 → 深色 → 跟随系统；手动选择后不再被系统变化覆盖。

### 4. 分类显示 0 篇（真 bug）

- 根因：`blog_category.article_count` / `blog_tag.article_count` / `blog_column.article_count`
  是冗余计数字段，但维护它们的写路径（文章增删改、发布下线）属于 P2，当前**从未被更新**，
  所以接口一直返回种子数据里的 0。
- 修复：改为**读时聚合**——`ArticleMapper` 新增三个 `GROUP BY` 查询，
  `CategoryService` / `TagService` / `ColumnService` 合并真实计数（`common/db/RowUtils` 处理列名大小写差异）。
- 附带：演示数据补到 5 篇（每个分类 1 篇），并建立 `blog_column_article` 关联 4 篇，专栏不再是 0。
- 前端：标签页与分类页过滤/排序 0 篇项，避免出现"点进去是空列表"的死链。

### 顺带修复

- 文章目录（TOC）一直不显示：`load()` 在 `loading` 仍为 true 时就 `buildToc()`，
  此时模板还停在骨架屏分支，`contentRef` 为空。改为先结束 loading 再 `nextTick`。
- 阅读时间统一显示 1 分钟：整数除法向下取整，改为按 400 字/分钟向上取整。
- Vite 开发服务器偶发崩溃：文件监听撞上编辑器原子写入产生的 `*.tmpdir`，
  在 `vite.config.ts` 增加 `server.watch.ignored`。

### 验证

```
后端测试     Tests run: 14, Failures: 0, Errors: 0  BUILD SUCCESS
接口自检     19/19 checks passed（categories 5×1 篇、column 4 篇、articles 5）
类型检查     vue-tsc --noEmit  0 errors
页面截图     docs/screenshots/{home,blog,article}.png（无头浏览器实拍）
```

---

## 2026-11-03 · 首次交付：工程骨架 + 认证 + 阅读链路

### 范围

按 `docs/PROJECT_DESIGN.md` 执行 **P0（工程骨架）** 与 **P1（数据库与认证）**，并额外打通
**只读阅读链路**（文章/分类/标签/专栏/站点信息/留言列表 + 前台页面），使"项目 + 测试环境"可端到端演示。
未实现：文章 CRUD 与 Markdown 编辑器（P2）、评论/点赞/收藏写接口（P4）、后台管理界面（P5）。

### 交付物

| 模块 | 内容 |
| --- | --- |
| `blog-server` | Spring Boot 3.3.5 / Java 21；统一响应与异常；JWT Cookie 认证；MyBatis-Plus；Flyway（V1 建表 + V2 种子）；管理员 bootstrap；演示数据 Seeder；17 个 REST 接口 |
| `blog-frontend` | Vue 3 + TS + Vite + Pinia + Router + Tailwind v4；首页/博客/文章详情（Markdown + TOC + 阅读进度）/分类/标签/专栏/留言/关于/登录/注册/个人中心/404；浅深色主题（跟随系统） |
| `blog-admin` | Vue 3 + TS + Vite + Element Plus；登录、仪表盘（真实统计）、侧边菜单、浅深色切换；其余模块为明确标注的占位页 |
| 工程化 | 便携工具链 `.tools`（JDK 21.0.13 / Maven 3.9.16 / MySQL 8.0.28）、PowerShell 启动脚本、Nginx/Docker 部署示例、`.env.example`、`.gitignore` |
| 文档 | PROJECT_DESIGN（由 DOCX 转换）、API、DATABASE、DEPLOYMENT、TESTING、README |

### 验证结果（真实执行）

```
后端测试        mvn test                Tests run: 14, Failures: 0, Errors: 0  BUILD SUCCESS
接口验证        node verify-api.mjs     19/19 checks passed
前台类型检查     vue-tsc --noEmit        0 errors
前台构建        vite build              ✓ built，index-*.js 179.99 kB (gzip 68.50 kB)
后台构建        vite build              ✓ built，index-*.js 1,135.78 kB (gzip 376.45 kB)
前台代理        GET /api/v1/articles    total=3（经 Vite proxy）
后台代理        GET /api/v1/site/config 200（经 Vite proxy）
```

接口验证覆盖：健康检查、站点配置、文章列表/详情、分类、标签、统计、留言、匿名 401、
注册、登录、Cookie 会话、`/auth/me`、普通用户访问 admin 403、管理员 200、登出。

### 关键决策

1. **测试跑真实 MySQL 而非 H2**：`eason_blog_test` + 真实 Flyway 迁移，避免 SQL 方言差异掩盖问题；
   代价是测试依赖本地 MySQL 已启动（已由 `scripts/test-backend.ps1` 兜底）。
2. **演示数据不放进 Flyway**：迁移会在所有环境执行，演示文章一旦进入生产迁移历史就无法干净删除；
   改为 `DemoDataSeeder`（`app.demo-data.enabled=true` 且已存在管理员才执行，幂等）。
3. **管理员只由环境变量创建**：`AdminBootstrapRunner` 已存在即跳过，密码不入库、不入 Git。
4. **标签筛选用「先查文章 ID 再 IN」**：JOIN + 分页会让 `total` 失真，个人站点数据量下 IN 足够。
5. **分页 size 上限 50、排序字段白名单**：越界返回 422，防止前端传 `size=100000` 或注入排序片段。
6. **CSS 变量 + 少量 Tailwind 工具类**：Tailwind v4 与 `@tailwindcss/vite` 配合，设计令牌集中在 `styles/main.css`。
7. **shadcn-vue 延后到 P3**：P0 只需设计令牌与基础组件；引入 CLI 会带来交互式生成与版本耦合，
   在页面真正稳定后再接入，避免过早锁定组件结构。
8. **Markdown 用 marked + DOMPurify**：先净化再 `v-html`，外链统一 `rel="noopener"`；
   Shiki 高亮与 Vditor 编辑器属于 P2/P3，当前用基础代码块样式。
9. **pnpm 11 配置迁移**：`onlyBuiltDependencies`、`verifyDepsBeforeRun` 写入 `pnpm-workspace.yaml`；
   本机跨盘符无法硬链接，`nodeLinker: hoisted` 最稳。

### 与设计文档的偏差（需确认）

| 偏差 | 原因 | 影响 |
| --- | --- | --- |
| 专栏详情未展示文章列表 | 需要 `blog_column_article` 关联查询与排序，属 P2 | 前台专栏详情页显示"待接入"提示 |
| 评论/留言提交、点赞、收藏未实现 | 属 P4，需要登录态下的写接口与审核流 | 留言页只读；文章页暂无互动按钮 |
| 后台除仪表盘外为占位页 | 属 P2/P5 | 菜单与路由已就位，点击显示阶段说明 |
| 未引入 Vditor / Shiki | 属 P2/P3 | 文章渲染用 marked，无代码高亮 |
| Tailwind 使用 v4 | 文档未指定版本，v4 为当前主线 | 与 shadcn-vue 的兼容性在 P3 验证 |

### 问题与修复

| 问题 | 根因 | 修复 |
| --- | --- | --- |
| `PaginationInnerInterceptor` 找不到 | MyBatis-Plus 3.5.9 起 JSqlParser 插件拆包 | 显式引入 `mybatis-plus-jsqlparser` |
| `CorsConfigurationSource` 两个 Bean 冲突 | Spring MVC 自带 `mvcHandlerMappingIntrospector` 也实现了该接口 | 注入处加 `@Qualifier("corsConfigurationSource")` |
| 测试报 `MockMaker` 初始化失败 | Mockito inline mock maker 在受限环境无法挂载 agent | `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` 指定 `mock-maker-subclass` |
| 演示文章为 0 | `DemoDataSeeder` 的 `@Order(100)` 先于无 `@Order` 的管理员初始化执行 | `AdminBootstrapRunner` 加 `@Order(10)` |
| pnpm 安装后 `picomatch` 缺失 / esbuild 子进程 EPERM | 符号链接式 node_modules 在跨盘+沙箱下解析失败；esbuild 需 spawn | `nodeLinker: hoisted`；构建时按需放宽沙箱权限 |

### 已知限制

- Windows 沙箱内 Vite 构建/开发服务器需要 esbuild 子进程，必须在放宽权限的终端执行（`scripts/*.ps1` 在普通终端运行即可）。
- 首页/列表的骨架屏在数据加载极快时会闪一下，P3 再优化。
- 后台打包体积偏大（Element Plus 全量引入），P6 做按需引入与分包。
- 缺少文章封面图上传与资源管理，`cover` 字段暂为空。

### 下一步（P2 内容基础）

1. 文章 CRUD（DTO/VO 完整、slug 生成与冲突处理、草稿/发布/下线状态机）。
2. 分类/标签/专栏管理接口与后台页面；专栏文章排序。
3. Markdown 编辑与预览（Vditor），渲染侧引入 Shiki 代码高亮 + 目录。
4. 对应测试：权限矩阵、状态机、slug 冲突、XSS 净化用例。
