-- =============================================================================
-- V2 基础字典与站点配置（不包含任何真实账号/密码）
-- 管理员账号由 AdminBootstrapRunner 依据环境变量创建，见 docs/DEPLOYMENT.md。
-- =============================================================================

INSERT INTO blog_role (id, code, name, description) VALUES
    (1, 'SUPER_ADMIN', '超级管理员', '站点作者本人，拥有全部权限'),
    (2, 'USER', '普通用户', '注册用户，可互动但不可发布文章');

INSERT INTO blog_permission (id, code, name, description) VALUES
    (1,  'article:read',     '阅读文章',   '查看已发布文章'),
    (2,  'article:write',    '管理文章',   '创建/编辑/发布/下线/删除文章'),
    (3,  'category:write',   '管理分类',   '维护分类'),
    (4,  'tag:write',        '管理标签',   '维护标签'),
    (5,  'column:write',     '管理专栏',   '维护专栏'),
    (6,  'comment:moderate', '评论管理',   '审核/回复/删除评论与留言'),
    (7,  'user:manage',      '用户管理',   '禁用/启用用户、分配角色'),
    (8,  'resource:manage',  '资源管理',   '上传与删除资源'),
    (9,  'system:manage',    '系统设置',   '修改站点配置'),
    (10, 'log:read',         '日志查看',   '查看登录与操作日志');

-- 超级管理员拥有全部权限；普通用户仅保留阅读权限（互动权限由登录态控制）
INSERT INTO blog_role_permission (role_id, permission_id, create_time)
SELECT 1, id, NOW() FROM blog_permission;

INSERT INTO blog_role_permission (role_id, permission_id, create_time) VALUES
    (2, 1, NOW());

INSERT INTO blog_category (id, parent_id, name, slug, description, sort, article_count, create_time, update_time, is_deleted) VALUES
    (1, 0, 'Java',        'java',        'Java 语言与 JVM 相关笔记',        10, 0, NOW(), NOW(), 0),
    (2, 0, 'Spring Boot', 'spring-boot', 'Spring Boot 实战与源码阅读',      20, 0, NOW(), NOW(), 0),
    (3, 0, 'MySQL',       'mysql',       '数据库设计、索引与优化',          30, 0, NOW(), NOW(), 0),
    (4, 0, 'Vue',         'vue',         'Vue 3 与前端工程化',              40, 0, NOW(), NOW(), 0),
    (5, 0, '工程实践',     'engineering', '构建、测试、部署与工具链',        50, 0, NOW(), NOW(), 0);

INSERT INTO blog_tag (id, name, slug, article_count, create_time) VALUES
    (1, 'Java',          'java',          0, NOW()),
    (2, 'Spring Boot',   'spring-boot',   0, NOW()),
    (3, 'MyBatis-Plus',  'mybatis-plus',  0, NOW()),
    (4, 'MySQL',         'mysql',         0, NOW()),
    (5, 'Vue 3',         'vue3',          0, NOW()),
    (6, 'TypeScript',    'typescript',    0, NOW()),
    (7, 'Flyway',        'flyway',        0, NOW()),
    (8, 'JWT',           'jwt',           0, NOW());

INSERT INTO blog_column (id, name, slug, description, cover, article_count, sort, create_time, update_time, is_deleted) VALUES
    (1, '从零搭建个人博客', 'build-blog-from-scratch', '从需求、设计到部署的完整过程记录', NULL, 0, 1, NOW(), NOW(), 0);

-- 公开配置通过 /api/v1/site/config 输出；is_public=0 的配置不会暴露
INSERT INTO blog_system_config (config_key, config_value, description, is_public, create_time, update_time) VALUES
    ('site.title',        'Eason Blog',                         '站点标题',       1, NOW(), NOW()),
    ('site.subtitle',     '记录 · 思考 · 分享',                  '站点副标题',     1, NOW(), NOW()),
    ('site.description',  '一个关于 Java、Spring Boot、MySQL 与 Vue 的个人技术博客', '站点描述', 1, NOW(), NOW()),
    ('site.author',       'Eason',                              '作者昵称',       1, NOW(), NOW()),
    ('site.avatar',       '',                                   '作者头像地址',   1, NOW(), NOW()),
    ('site.startDate',    '2026-11-03',                         '建站日期',       1, NOW(), NOW()),
    ('site.github',       'https://github.com/',                'GitHub 地址',    1, NOW(), NOW()),
    ('site.icp',          '',                                   '备案号',         1, NOW(), NOW()),
    ('comment.audit',     'true',                               '评论是否先审后发', 0, NOW(), NOW()),
    ('guestbook.audit',   'true',                               '留言是否先审后发', 0, NOW(), NOW()),
    ('register.enabled',  'true',                               '是否开放注册',    0, NOW(), NOW()),
    ('storage.driver',    'local',                              '资源存储方式',    0, NOW(), NOW());

INSERT INTO blog_announcement (content, is_active, create_time, update_time) VALUES
    ('博客 V1.0 正在搭建中，欢迎留言交流。', 1, NOW(), NOW());

INSERT INTO blog_friend_link (name, url, description, sort, is_active, create_time) VALUES
    ('Vue 3 官方文档', 'https://cn.vuejs.org/', '渐进式 JavaScript 框架', 10, 1, NOW()),
    ('Spring 官方',    'https://spring.io/',    'Spring 生态入口',        20, 1, NOW());
