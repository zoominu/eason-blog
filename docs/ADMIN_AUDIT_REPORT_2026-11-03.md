# Eason Blog 后台管理审计报告

| 项 | 内容 |
| --- | --- |
| 报告日期 | 2026-11-03 |
| 审计对象 | 管理后台 `http://192.168.245.1:5174/`（本机同时验证 `127.0.0.1:5174`） |
| 代码版本 | `dce9d1c`（前台审计修复已包含在 `1916b25`） |
| 登录账号 | `admin@eason.blog`（密码按提供值验证，本文不记录密码） |
| 审计范围 | `blog-admin` 全部页面/路由/API 封装；与后台直接相关的 `blog-server` 管理接口、状态机、上传、权限、日志 |
| 审计方式 | 真实登录 + 管理接口实测 + 源码走查 + 类型检查 + 现有后台截图核对 |
| 总体结论 | 后台可登录、主要页面可用；但 **专栏文章绑定保存直接 404**，另有文章状态机、生产部署、分类排序、资源上传等问题需要处理 |

---

## 1. 执行摘要

### 1.1 问题分布

| 级别 | 数量 | 说明 |
| --- | --- | --- |
| P0 阻断 | 1 | 专栏「管理文章」保存请求 URL 拼接错误，功能完全不可用 |
| P1 严重 | 7 | 文章状态下架/越级发布、生产 `/admin/` 路由、分类排序、开发环境资源、会话权限、专栏选择器、ZIP 上传 |
| P2 一般 | 6 | 编辑器宽度、仪表盘信息、前台入口/favicon/主题、登录重复提交、测试数据、权限矩阵与审计 |
| 功能缺失 | 12 类 | 评论/留言回复、批量审核、文章预览/修订、用户管理等 |

### 1.2 最需要先处理的五件事

1. **修 `columnApi.setArticles` 的 URL**：现在实际请求 `/api/v1/api/v1/admin/...`，保存专栏文章必然 404。
2. **修文章编辑页的状态语义**：已发布文章点「保存草稿」会在无确认的情况下直接下架；回收站文章可以被「保存并发布」直接发布。
3. **修生产 `/admin/` 部署**：Vue Router 的 history base 仍是 `/`，按仓库默认 Nginx 部署后后台会跳到 `/dashboard` 并离开 `/admin/`；`build-admin.ps1` 也没有传 `--base=/admin/`。
4. **修分类编辑排序丢失**：`CategoryVO` 不返回 `sort`，编辑弹窗把 `sort` 固定写 0，保存一次分类顺序就被重置。
5. **修开发环境 `/uploads` 代理**：5174 下资源缩略图和“复制链接”都指向未代理的 `/uploads`，实际拿到的是 `index.html`。

### 1.3 已确认正常

- `5174` 页面可访问；通过 `192.168.245.1:5174/api/v1/auth/login` 登录返回 200，Cookie 正常下发。
- `dashboard`、`articles`、`users`、`roles`、`permissions`、`resources`、`login-logs`、`operation-logs`、`settings`、`columns`、`comments`、`guestbook` 接口均返回 200。
- 文章 CRUD、发布/下线/回收/恢复按钮、评论/留言审核、用户禁用/启用、角色分配、日志筛选、设置保存等主要链路在接口层可用。
- `vue-tsc --noEmit`（blog-admin）退出码 0，无类型错误。
- 后台登录页、仪表盘、文章列表、文章编辑页有可用截图，整体布局与 Element Plus 组件使用正常。

---

## 2. 审计方法与限制

### 2.1 已执行

- 使用提供的管理员账号真实登录，抓取 Cookie 后逐个请求后台接口。
- 读取 `blog-admin/src` 下全部页面、组件、路由、Store、API 封装与样式。
- 读取 `blog-server` 中 Admin 系列 Controller/Service、文章状态机、上传、权限、日志实现。
- 对关键疑点做真实请求验证：专栏保存 URL、ZIP 上传、`/uploads` 静态资源、分类返回字段、仪表盘字段。
- 执行 `blog-admin` 的 `vue-tsc --noEmit`。
- 核对 `docs/screenshots/admin-dashboard.png`、`admin-articles.png`、`admin-editor.png`。

### 2.2 未执行 / 限制

- 当前 Windows 沙箱无法启动无头 Edge（命名管道被拒绝），因此没有做完整点击流回归；UI 层结论以源码 + 现有截图为准。
- 没有修改业务数据。为验证 ZIP 上传，曾上传一个临时 `.md` 文件并立即删除；ZIP 请求被 400 拒绝，未落库。
- 没有做压力测试、完整安全渗透；没有逐个验证所有后台表单的浏览器交互。
- 截图拍摄于修复提交之前，布局类结论仅作为辅助证据。

---

## 3. 问题详情

### P0-1 专栏「管理文章」保存必然 404

| 项 | 内容 |
| --- | --- |
| 级别 | P0 阻断 |
| 位置 | `blog-admin/src/api/index.ts:74-77`；`blog-admin/src/api/http.ts:4` |
| 置信度 | 高（静态 + 真实请求双重确认） |

**现象**

在「专栏 → 文章」里选择文章、点保存，前端提示 404 / 「接口或资源不存在」，专栏文章关系不会更新。该功能是最近新增的，目前完全不可用。

**根因**

Axios 实例已经配置了 `baseURL: '/api/v1'`：

```ts
const instance = axios.create({ baseURL: '/api/v1', withCredentials: true })
```

但保存专栏文章时又传了完整路径：

```ts
setArticles: (id, articleIds) =>
  instance.put(`/api/v1/admin/columns/${id}/articles`, { articleIds })
```

Axios 对以 `/` 开头但不是完整 URL 的地址仍会拼接 `baseURL`，实际请求变成：

```text
/api/v1/api/v1/admin/columns/1/articles
```

实测：

```text
PUT /api/v1/api/v1/admin/columns/1/articles -> 404
{"code":404,"message":"接口或资源不存在"}
```

同一文件里 `columnApi.articles` 使用 `get('/admin/columns/...')`，是正确的；只有 `setArticles` 多写了一层。

**影响**

- 后台无法把文章加入专栏、调整专栏阅读顺序。
- 前台的专栏文章列表只能依赖演示数据或手工 SQL 维护。
- 该功能在页面上“看起来存在”，但每次保存都失败，属于最容易被忽略的阻断问题。

**修复建议**

```ts
setArticles: (id, articleIds) =>
  instance.put(`/admin/columns/${id}/articles`, { articleIds })
```

或给 `http.ts` 增加 `put<T>()` 辅助函数，统一走 `baseURL`，避免以后再出现同样的拼接错误。

---

### P1-1 文章状态机不完整：保存草稿会下架已发布文章，回收站可直接发布

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重 |
| 位置 | `blog-admin/src/views/ArticleEditView.vue:57-88`；`blog-server/.../AdminArticleService.java:152-193`；`blog-admin/src/views/ArticlesView.vue:168-188` |
| 置信度 | 高（代码级） |

**现象**

编辑页只有两个按钮：「保存草稿」「保存并发布」，但后端 `update()` 对 `status` 做了隐式状态迁移：

```java
if (request.status() != null) {
    if (request.status() == PUBLISHED) {
        article.setStatus(PUBLISHED);
        ...
    } else if (article.getStatus() != null && article.getStatus() == PUBLISHED) {
        // 已发布文章点「保存草稿」= 撤回为草稿（下架）
        article.setStatus(DRAFT);
    }
}
```

由此产生四种不一致：

| 当前状态 | 点「保存草稿」 | 点「保存并发布」 |
| --- | --- | --- |
| 草稿 0 | 保持草稿 | 发布 |
| 已发布 1 | **直接变成草稿（下架）** | 保持发布 |
| 已下线 2 | **仍为已下线** | 重新发布 |
| 回收站 3 | **仍为回收站** | **直接发布，绕过恢复** |

**影响**

- 作者只想保存修改，点「保存草稿」会让线上文章立刻消失，且没有二次确认；对已发布文章尤其危险。
- 「保存草稿」在已下线/回收站文章上又不会真的变成草稿，按钮语义与结果不一致。
- 回收站文章可以在编辑页直接「保存并发布」，绕过 `restore`（回收站 → 草稿）的既定状态机；`publish` 接口本身也不拒绝回收站状态。
- `ArticlesView` 对回收站文章仍显示「编辑」按钮，用户很容易走进这条路径。

**修复建议**

- 编辑页按当前状态显示明确动作：已发布文章用「保存修改」与「下线」；回收站文章只能「恢复」，不显示发布。
- 后端在 `update()`/`publish()` 中显式校验合法状态迁移，拒绝 `3 → 1` 这类越级操作。
- 对会导致下架的操作增加确认弹窗，并在保存后提示“已下线”。

---

### P1-2 生产按 `/admin/` 部署时，后台路由会离开子路径

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重（部署阻断） |
| 位置 | `blog-admin/src/router/index.ts:5-6`；`deploy/nginx/eason-blog.conf:54-59`；`docs/DEPLOYMENT.md:44-45`；`scripts/build-admin.ps1` |
| 置信度 | 高（代码 + 部署配置） |

**现象**

仓库默认 Nginx 把后台放在 `https://域名/admin/`，并让构建时使用 `pnpm build --base=/admin/`。但 Vue Router 仍使用：

```ts
history: createWebHistory()
```

没有任何 base。打开 `/admin/` 时，Router 以 `/` 为 base 解析路径，匹配不到任何路由，落入 catch-all：

```ts
{ path: '/:pathMatch(.*)*', redirect: '/dashboard' }
```

于是浏览器被推到站点根路径 `/dashboard`，离开 `/admin/`，Nginx 会返回前台页面而不是后台。

另外，`scripts/build-admin.ps1` 只执行 `Invoke-Pnpm build`，没有传 `--base=/admin/`；按脚本构建出的 `dist/index.html` 资源路径是 `/assets/...`，部署到 `/admin/` 后静态资源也会 404。

**影响**

- 按仓库默认 Nginx 部署，后台要么白屏、要么跳到前台，无法正常使用。
- 即使按文档手动加了 `--base=/admin/`，路由 base 仍然是错的，刷新/直接访问子路由仍会出问题。
- 如果后台部署在独立域名（`admin.eas0n.top`）根路径，则不受影响；但默认部署路径是 `/admin/`。

**修复建议**

```ts
history: createWebHistory(import.meta.env.BASE_URL)
```

并在构建时统一传入 `--base=/admin/`（或通过 Vite `base` 配置与环境变量控制），同步更新 `build-admin.ps1` 和文档。若后台固定独立域名，则把 Nginx/文档改为独立 server 块，避免两套假设混用。

---

### P1-3 分类编辑会把排序重置为 0，父分类还可能成环

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重 |
| 位置 | `blog-admin/src/views/CategoriesView.vue:40-49`、`141-146`；`blog-server/.../category/vo/CategoryVO.java`；`AdminCategoryService.java:87-96` |
| 置信度 | 高（接口返回字段 + 代码） |

**现象**

编辑任意分类并保存后，该分类的 `sort` 会变成 0，分类列表顺序被重置。

**根因**

后台分类接口返回的 `CategoryVO` 没有 `sort` 字段：

```java
public record CategoryVO(
        Long id, Long parentId, String name, String slug,
        String description, Integer articleCount) {}
```

前端 `Category` 类型也没有 `sort`。编辑弹窗只能写死：

```ts
function openEdit(row: Category) {
  Object.assign(form, {
    ...,
    sort: 0
  })
}
```

保存时后端 `apply()` 使用提交值：

```java
category.setSort(request.sort() == null ? 0 : request.sort());
```

于是每次编辑都把真实排序覆盖成 0。专栏侧已经修复了同类问题（`ColumnVO` 增加 `sort`），分类侧没有同步。

**另一个问题：父分类可成环**

编辑弹窗的父分类下拉只排除了自己：

```ts
v-for="c in list.filter((x) => x.id !== editingId)"
```

后端也只校验“父分类不能是自己”：

```java
if (excludeId != null && excludeId.equals(category.getParentId())) {
    throw BusinessException.badRequest("父分类不能是自己");
}
```

因此可以把 A 的父分类设成 A 的子分类 B，形成环（A → B → A）；目前前台分类是平铺列表，未必立刻报错，但数据已经损坏，后续做分类树/面包屑时会出问题。

**影响**

- 分类顺序维护失效，前台/后台顺序与预期不一致。
- 可能产生循环父子关系，破坏分类树。

**修复建议**

- `CategoryVO` 返回 `sort`，前端 `Category` 类型补 `sort`，`openEdit` 回填。
- 父分类下拉排除自身及所有后代；后端增加“父分类不能是自己的后代”校验。

---

### P1-4 5174 开发环境下资源缩略图与“复制链接”不可用

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重（当前访问方式下） |
| 位置 | `blog-admin/vite.config.ts:24-29`；`blog-admin/src/views/ResourcesView.vue:48-56`、`105`；`ResourceService.java:107` |
| 置信度 | 高（真实请求） |

**现象**

资源管理上传图片后，缩略图不显示；“复制链接”得到的地址在浏览器中打开是后台的 `index.html`，不是图片。

**根因**

后端返回的资源 URL 是站点根路径：

```java
resource.setUrl("/uploads/" + month + "/" + storedName);
```

但后台 Vite 只代理了 `/api`：

```ts
proxy: {
  '/api': { target: 'http://127.0.0.1:8080', changeOrigin: false }
}
```

在 `http://192.168.245.1:5174/` 下，`<img src="/uploads/...">` 请求的是 5174 的 Vite 服务器；Vite 对未知路径走 SPA fallback，返回 `index.html`：

```text
GET http://192.168.245.1:5174/uploads/nonexistent.png -> 200 text/html
```

浏览器把 HTML 当图片解码，自然失败。`copyUrl` 使用 `window.location.origin + item.url`，同样复制出 5174 的地址，打开也是 HTML。

前台 `blog-frontend` 的 Vite 也没有代理 `/uploads`，所以开发环境下文章里的图片走 5173 也一样打不开。

**影响**

- 后台无法预览已上传图片，也无法确认资源是否上传成功。
- 复制出的链接在开发环境不可用。
- 前台文章图片在开发环境同样受影响。
- 生产 Nginx 直接服务 `/uploads/`，所以这是“开发环境专属”，但当前给出的访问地址正是 5174。

**修复建议**

- 在两个 Vite 配置中都增加：
  ```ts
  '/uploads': { target: 'http://127.0.0.1:8080', changeOrigin: false }
  ```
- 或在开发环境通过 `VITE_API_ORIGIN` 拼接绝对地址。
- 生产环境确认 Nginx 的 `/uploads/` alias 与后端 `UPLOAD_DIR` 一致。

---

### P1-5 后台会话与权限边界：普通用户可进入后台壳，401 不会自动退出

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重（体验/权限边界） |
| 位置 | `blog-admin/src/router/index.ts:110-117`；`blog-admin/src/stores/user.ts:10-24`；`blog-admin/src/api/http.ts:10-22` |
| 置信度 | 高（代码级） |

**现象 A：普通用户可以进入后台壳**

路由守卫只判断用户是否存在：

```ts
const user = await userStore.fetchMe()
if (!user) {
  return { name: 'login', query: { redirect: to.fullPath } }
}
return true
```

没有判断 `SUPER_ADMIN`。由于 5173 与 5174 在同一主机下共享 Cookie（Cookie 不区分端口），普通读者在前台登录后，直接打开 5174 会通过守卫、看到后台布局；随后所有 `/admin/**` 请求返回 403，页面弹出一串错误。虽然后端权限没有被突破，但普通用户会看到后台界面，体验和边界都不清晰。

`stores/user.ts` 里其实已经有 `isAdmin` getter，但没有被守卫使用。

**现象 B：会话过期后不会自动回登录页**

`fetchMe()` 只执行一次并把 `initialized` 置为 `true`；token 默认 2 小时过期后，守卫不再请求 `/auth/me`，页面里的 API 返回 401，`http.ts` 只把错误抛给调用方：

```ts
return Promise.reject(new Error(message))
```

没有清理用户态，也没有跳转登录。用户会停留在后台页面，反复看到“登录已过期，请重新登录”的 toast，只能手动刷新。

**影响**

- 普通用户访问后台时看到不该看到的界面。
- 会话过期后后台无法自动恢复，操作全部失败但页面不退出。
- 用户体验与安全预期不一致。

**修复建议**

- 守卫增加 `if (!user.roles.includes('SUPER_ADMIN'))`，非管理员直接回登录页或前台。
- 在 `http.ts` 拦截 401：清空 `userStore`，跳转 `/login?redirect=...`。
- `fetchMe` 不要永久缓存失败；可在路由切换时按需重新校验。

---

### P1-6 专栏文章选择器只加载前 50 篇，且没有排序 UI

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重（数据量增长后） |
| 位置 | `blog-admin/src/views/ColumnsView.vue:52-64`、`179-193`；`AdminArticleController.java:48-55` |
| 置信度 | 高（代码级） |

**现象**

专栏的「文章」弹窗一次性只取：

```ts
articleApi.page({ page: 1, size: 50 })
```

后台文章接口 `size` 上限正好是 50。因此：

- 文章超过 50 篇后，第 51 篇及以后无法被选进专栏。
- 弹窗没有分页、没有服务端搜索；`el-select filterable` 只能过滤已经加载的 50 条。
- 说明文案写“选择顺序即专栏内的阅读顺序”，但 Element Plus 的 multiple select 不支持拖拽排序；调整顺序只能取消再重新选择，已有 4 篇时就已经很别扭。

**影响**

- 长期写作后专栏无法完整维护。
- 阅读顺序调整成本高，容易误操作。
- 与“专栏文章排序”这一功能的预期不符。

**修复建议**

- 弹窗改成“远程搜索 + 分页表格/穿梭框”，或加载全部文章 ID/标题（轻量接口）。
- 已选文章用可拖拽列表展示（如 `vuedraggable`），保存时提交明确顺序。
- 若暂时不做，至少在 UI 上说明“仅显示最近 50 篇”。

---

### P1-7 ZIP 上传被后端拒绝，但界面宣称支持

| 项 | 内容 |
| --- | --- |
| 级别 | P1 严重（功能承诺不成立） |
| 位置 | `ResourceService.java:49-51`；`blog-admin/src/views/ResourcesView.vue:77` |
| 置信度 | 高（真实上传验证） |

**现象**

资源管理页说明“支持图片 / PDF / 文本 / zip”，但上传 `.zip` 返回：

```text
400 {"code":400,"message":"不支持的文件 MIME 类型"}
```

**根因**

扩展名白名单包含 `zip`：

```java
Set.of("png","jpg","jpeg","gif","webp","svg","pdf","txt","md","zip")
```

但 MIME 白名单只有：

```java
Set.of("image/", "text/", "application/pdf")
```

浏览器对 `.zip` 发送 `application/zip` 或 `application/x-zip-compressed`，都不匹配任何前缀，直接被拒。

实测：

```text
upload probe.zip (application/zip) -> 400 不支持的文件 MIME 类型
upload probe.md  (text/markdown)   -> 200 成功
```

**影响**

- 用户按界面提示上传 zip 必然失败。
- 其他扩展名与 MIME 的组合也可能踩坑（例如 `.md` 在部分浏览器上 MIME 为空）。
- 资源管理的能力说明与实现不一致。

**修复建议**

- 要么允许 `application/zip`、`application/x-zip-compressed`，要么从 UI 和扩展名白名单中移除 zip。
- 更稳妥的做法：以扩展名为准做白名单，MIME 只做辅助校验；对空 MIME 做明确处理。
- 补充上传用例，覆盖 zip/md/图片/超限/非法扩展名。

---

### P2-1 文章编辑页 Markdown 编辑器没有撑满内容区

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般（视觉/写作体验） |
| 位置 | `blog-admin/src/components/MarkdownEditor.vue:94-99` |
| 置信度 | 中高（截图 + CSS） |

`MarkdownEditor` 的根节点 `.editor` 没有设置 `width: 100%`，它作为 `.el-form-item__content` 的 flex 子项按内容收缩。现有截图 `docs/screenshots/admin-editor.png` 中，左侧卡片宽度约 890px，但编辑器只占约 430px，右侧留下大片空白。

**建议**

```css
.editor {
  width: 100%;
}
```

或在 `ArticleEditView` 中让表单项内容区 `display: block`。修复后重新截图确认分栏预览也随宽度展开。

---

### P2-2 仪表盘缺少待审核计数，且“累计阅读”提示的排序并不存在

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般 |
| 位置 | `DashboardView.vue:55-59`；`DashboardVO.java`；`AdminArticleController.java` |
| 置信度 | 高（接口字段 + 代码） |

- 仪表盘有文章、用户、分类、标签、下线、回收站，但**没有待审核评论/待审核留言的数量**，侧边栏也没有角标。管理员必须逐个点「评论」「留言」才能发现待处理内容。
- 「累计阅读」卡片的 hint 是“按浏览量排序”，点击后进入 `/articles`，但后台文章列表没有排序参数，也没有“按浏览量排序”的控件，这个承诺落空。

**建议**

- `DashboardVO` 增加 `pendingComments`、`pendingGuestbook`，仪表盘加卡片，菜单加 badge。
- 文章列表增加 `sort=views`（或至少提供“按浏览量”排序），或把卡片 hint 改成“查看全部文章”。

---

### P2-3 后台缺少“查看前台”入口，且无 favicon、深色主题首屏会闪白

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般 |
| 位置 | `AdminLayout.vue`；`blog-admin/index.html`；`stores/theme.ts:14-17`；`App.vue:5-6` |
| 置信度 | 高（代码级） |

- 侧边栏/页头没有“查看前台/打开站点”的链接，管理员写完文章要手动改地址。
- `index.html` 没有 `<link rel="icon">`，后台标签页没有图标。
- 主题在 `onMounted` 才 `init()`，深色模式刷新时会先渲染浅色再切深色，出现闪白；前台已有内联脚本防闪，后台没有。

**建议**

- 页头加“查看前台”（开发 `:5173`，生产 `/`）。
- 复用前台的 favicon 与主题内联初始化脚本。

---

### P2-4 登录页 Enter 行为不一致

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般 |
| 位置 | `blog-admin/src/views/LoginView.vue:58-73` |
| 置信度 | 高（代码级） |

登录表单写了 `@submit.prevent="submit"`，但按钮是 Element Plus 默认的 `native-type="button"`，表单没有 submit 控件；实际只有密码框绑定了 `@keyup.enter="submit"`。结果是：

- 在密码框按 Enter 可以登录；
- 在邮箱框按 Enter 没有任何反应；
- 表单的 `submit` 处理基本是死代码。

**建议**：给登录按钮加 `native-type="submit"`，移除 `@keyup.enter`，让整个表单统一走 submit 入口；或在两个输入框都处理 Enter。

---

### P2-5 数据库里残留较多测试数据

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般（内容卫生） |
| 位置 | 当前开发库 |
| 置信度 | 高（接口实测） |

当前后台能看到：

- 文章 `test`（slug 为 `1`）、`P2 端到端验证文章（已改）` 等测试内容。
- 分类 `验证分类381001`、标签 `验证标签381001`。
- 专栏 `test`。
- 用户 `审计用...` 等测试账号（共 7 个用户）。

这些数据会出现在前台/仪表盘/搜索中，影响演示与上线观感。

**建议**：上线前写一个清理脚本，按 slug/邮箱前缀删除测试文章、分类、标签、专栏与用户；保留一个真实管理员账号。

---

### P2-6 角色权限矩阵未参与鉴权，部分写操作缺少审计

| 项 | 内容 |
| --- | --- |
| 级别 | P2 一般 |
| 位置 | `RolesView.vue`；`AdminUserService.updateRolePermissions`；`AdminModerationController`；`AdminResourceController` |
| 置信度 | 高（代码级） |

- 角色权限页可以勾选/保存权限点，但后端接口鉴权仍只看 `SUPER_ADMIN` 角色，权限矩阵目前只是展示，不产生实际效果（开发日志已承认）。
- 评论/留言审核、资源上传/删除、角色权限更新等写操作没有 `@Audit`，操作日志覆盖不完整；出问题后无法追溯是谁审核/删除了内容。

**建议**

- 要么把权限矩阵接入 `@PreAuthorize`/自定义权限校验，要么在 UI 上明确标注“预留能力，暂不生效”。
- 给审核、上传、权限变更补 `@Audit`。

---

## 4. 功能缺失

以下不是“写错了”，而是后台当前缺少的能力，按对日常写作/运营的影响排序。

| 能力 | 当前状态 | 影响 |
| --- | --- | --- |
| 评论/留言回复 | 只能通过/拒绝；`blog_guestbook.reply` 字段没有写入口 | 博主无法回复读者 |
| 评论上下文 | 审核列表只显示文章标题，不能点进文章看上下文 | 审核容易误判 |
| 批量审核 | 只能逐条通过/拒绝 | 评论多时效率低 |
| 文章预览 | 编辑页不能直接打开前台预览，slug 只是文本 | 发布前确认排版不便 |
| 未保存提醒 | 返回列表/刷新会直接丢失编辑内容 | 误操作丢稿 |
| 自动保存/草稿恢复 | 无 | 长文写作风险高 |
| 文章修订历史 | `blog_article_revision` 表已建但未使用 | 无法回滚/对比 |
| 定时发布 | 无 | 只能手动卡点发布 |
| 批量操作 | 文章列表无多选、无批量发布/下线/回收 | 运营效率低 |
| 回收站彻底删除 | 只能移入回收站，不能清空/彻底删除 | 数据会一直堆积 |
| 用户管理增强 | 无新建用户、重置密码、强制下线、编辑资料 | 只能依赖前台注册 |
| 图片插入/封面选择 | 编辑器无图片上传按钮，封面只能手填 URL | 资源库与写作流程割裂 |
| 日志导出/日期筛选 | 只有分页和少量筛选 | 审计排查不便 |
| 设置项类型化 | 所有配置都是文本框，无校验/重置 | 容易填错 |
| 响应式侧边栏 | 固定 216px，无折叠/移动端抽屉 | 窄屏体验一般 |
| 查看前台入口 | 无 | 每次手动改地址 |

---

## 5. 修复路线建议

### 第一阶段：立即（阻断/高风险）

1. 修 `columnApi.setArticles` 的 URL（一行）。
2. 修文章编辑页状态语义：已发布文章保存不再隐式下架；回收站禁止直接发布；增加确认。
3. 修生产 `/admin/` 的 Router base 与 `build-admin.ps1` 的 `--base`。
4. 修分类 `sort` 回填与后代校验。

### 第二阶段：本周

5. 给 5174 / 5173 的 Vite 增加 `/uploads` 代理。
6. 后台守卫校验 `SUPER_ADMIN`；HTTP 拦截 401 自动退出。
7. 专栏文章选择器改成分页/远程搜索 + 可拖拽排序。
8. ZIP MIME 与 UI 文案对齐（允许或移除）。
9. 仪表盘补待审核计数与菜单 badge。

### 第三阶段：体验与能力

10. 编辑器宽度、预览、未保存提醒、自动保存。
11. 评论/留言回复与上下文链接、批量审核。
12. 用户管理增强、审计补全、权限矩阵落地或标注。
13. 清理测试数据，补后台 E2E/接口回归。

---

## 6. 附录

### 附录 A：关键接口实测

| 请求 | 结果 | 说明 |
| --- | --- | --- |
| `POST /api/v1/auth/login`（经 5174 代理） | 200 | 账号可用，Cookie 正常 |
| `GET /api/v1/admin/dashboard` | 200 | 字段完整，但无待审核计数 |
| `GET /api/v1/admin/articles?page=1&size=10` | 200，6 条 | 含 test/验证文章 |
| `GET /api/v1/admin/users` | 200，7 条 | 含测试用户 |
| `GET /api/v1/admin/categories` | 200，5 条 | **无 `sort` 字段** |
| `GET /api/v1/admin/columns` | 200，2 条 | 有 `sort` |
| `GET /api/v1/admin/columns/1/articles` | 200，4 条 | 读取正常 |
| `PUT /api/v1/api/v1/admin/columns/1/articles` | **404** | 前端拼接错误（复现） |
| `POST /admin/resources/upload`，`probe.zip` | **400** | MIME 不支持 zip |
| `POST /admin/resources/upload`，`probe.md` | 200 | 上传正常，已清理 |
| `GET http://192.168.245.1:5174/uploads/nonexistent.png` | 200 `text/html` | 未代理 `/uploads` |
| `GET /api/v1/admin/login-logs` | 200，46 条 | 日志可用 |
| `GET /api/v1/admin/operation-logs` | 200，14 条 | 日志可用 |
| `vue-tsc --noEmit`（blog-admin） | exit 0 | 无类型错误 |

### 附录 B：最小复现步骤

1. **专栏保存 404**
   - 登录后台 → 专栏 → 某专栏「文章」→ 选择文章 → 保存 → 404。
2. **保存草稿下架**
   - 打开一篇已发布文章 → 不做任何修改 → 点「保存草稿」→ 前台文章消失（状态变为草稿）。
3. **回收站越级发布**
   - 把文章移入回收站 → 仍点「编辑」→ 点「保存并发布」→ 文章直接回到已发布，未经过恢复。
4. **分类排序丢失**
   - 分类管理 → 编辑任一分类 → 保存 → 列表顺序变化（`sort` 被写成 0）。
5. **生产 /admin/ 路由**
   - 按 `deploy/nginx/eason-blog.conf` 部署 → 打开 `/admin/` → 被重定向到 `/dashboard`，离开后台。
6. **资源图片**
   - 上传一张图片 → 缩略图不显示 → 复制链接 → 打开得到后台 HTML。
7. **ZIP 上传**
   - 资源管理 → 上传 `.zip` → 400。
8. **普通用户进后台**
   - 在前台 5173 登录普通账号 → 打开 5174 → 能看到后台布局，接口全部 403。

### 附录 C：审计环境

| 项 | 值 |
| --- | --- |
| 后台地址 | `http://192.168.245.1:5174/`（本机 `127.0.0.1:5174`） |
| 后端 | `127.0.0.1:8080`，健康检查 UP |
| 代码版本 | `dce9d1c` |
| Node / 包管理 | DSH 运行时 Node + pnpm |
| 类型检查 | `vue-tsc --noEmit` exit 0 |
| 浏览器自动化 | 沙箱限制未执行（Edge 命名管道被拒绝） |

---

> 报告中的行号基于审计时工作区源码；后续修改后请以实际文件为准。本文不记录管理员密码。
