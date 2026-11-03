# 开发日志

> 记录每个阶段的真实变更、决策、验证结果与遗留问题。新条目追加在顶部。

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
