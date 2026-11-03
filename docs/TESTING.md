# 测试说明

## 1. 原则

- **不声称测试通过而未实际运行**；报告里区分「已执行」与「未执行」。
- 后端集成测试跑真实 MySQL（`eason_blog_test`）+ 真实 Flyway 迁移，不用 H2 模拟，避免 SQL 方言差异掩盖问题。
- 权限、校验、异常、边界是必测项；只测「happy path」不算完成。
- 前端当前以类型检查 + 构建 + 人工验收为主，组件测试与 E2E 在 P6 引入。

## 2. 运行

```powershell
# 一键：确保 MySQL 已启动 + 建测试库 + 跑测试
powershell -File scripts/test-backend.ps1

# 或手动
$env:JAVA_HOME="...jdk-21"
mvn -s .tools/settings.xml -f blog-server/pom.xml test
```

测试库连接参数（`application-test.yml`）：`TEST_DB_HOST` / `TEST_DB_PORT` / `TEST_DB_NAME` / `TEST_DB_USER` / `TEST_DB_PASSWORD`，默认 `127.0.0.1:3306/eason_blog_test`。

## 3. 现有用例

| 测试类 | 覆盖点 |
| --- | --- |
| `JwtServiceTest` | 签发/解析、角色声明、过期时间、篡改拒绝、密钥过短拒绝 |
| `AuthApiTest` | 注册→登录→me→登出全链路；错误密码 401 且文案统一；普通用户访问 admin 403；匿名 401；管理员 200；弱密码 422 |
| `ArticleApiTest` | 列表只含已发布公开文章（草稿不可见）；详情返回 Markdown；草稿详情 404；size>50 422；非法 sort 422 |

## 4. 断言约定

- 只断言对外契约（状态码、字段、错误文案），不断言内部实现细节。
- 测试数据用 `TestUsers.uniqueEmail()` 生成唯一邮箱，避免用例间相互污染。
- 不使用 `@Transactional` 回滚测试：Flyway 迁移 + 真实提交更贴近生产；数据隔离靠唯一值。

## 5. 新增测试清单（后续阶段）

- P2：文章 CRUD、发布/下线状态机、slug 唯一冲突、Markdown 净化（XSS payload）
- P3：SEO 字段、分类/标签筛选组合、分页边界（第 1 页/最后 1 页/空页）
- P4：评论审核流、点赞收藏幂等（重复请求计数不变）、越权删除他人评论
- P5：RBAC 矩阵逐项、操作日志落库、上传类型/大小/路径穿越
- P6：登录/评论限流（429）、N+1 与慢查询检查、前端 Lighthouse、Playwright 冒烟

## 6. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Access denied for user 'blog'` | 未执行 `init-db.ps1` | 运行脚本创建库与账号 |
| Flyway checksum mismatch | 修改了已应用的迁移 | 新增 V3 修复，不回改历史文件 |
| 端口 3306 被占用 | 本机已有 MySQL | 停掉旧实例或改 `DB_PORT` |
| 测试残留数据 | 曾用同一库跑过旧版本 | 删除 `eason_blog_test` 后重跑，Flyway 会重建 |
