-- =============================================================================
-- V1 初始化结构（MySQL 8.x / InnoDB / utf8mb4）
-- 约定：
--   1. 表名前缀 blog_，避免 user/column 等保留字冲突；
--   2. 时间字段统一 create_time / update_time；
--   3. 业务表使用 is_deleted 逻辑删除，日志表不软删；
--   4. 文件本体不入库，blog_resource 只存元数据；
--   5. 不使用 ON UPDATE CURRENT_TIMESTAMP，更新时间由应用层维护（便于测试与审计）。
-- =============================================================================

CREATE TABLE blog_user (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    uid              VARCHAR(32)  NOT NULL COMMENT '对外用户标识',
    email            VARCHAR(128) NOT NULL COMMENT '登录邮箱',
    password         VARCHAR(255) NOT NULL COMMENT 'BCrypt 哈希',
    nickname         VARCHAR(50)  NOT NULL COMMENT '昵称',
    avatar           VARCHAR(500) NULL COMMENT '头像地址',
    bio              VARCHAR(500) NULL COMMENT '个人简介',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '0 禁用，1 正常',
    email_verified   TINYINT      NOT NULL DEFAULT 0 COMMENT '0 未验证，1 已验证',
    last_login_time  DATETIME     NULL COMMENT '最后登录时间',
    last_login_ip    VARCHAR(45)  NULL COMMENT '最后登录 IP',
    register_time    DATETIME     NOT NULL COMMENT '注册时间',
    create_time      DATETIME     NOT NULL COMMENT '创建时间',
    update_time      DATETIME     NOT NULL COMMENT '更新时间',
    is_deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_uid (uid),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_status (status),
    KEY idx_user_create_time (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

CREATE TABLE blog_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL COMMENT '角色编码',
    name        VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE TABLE blog_permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(100) NOT NULL COMMENT '权限点，如 article:write',
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '权限点';

CREATE TABLE blog_user_role (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    role_id     BIGINT   NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role (role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES blog_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES blog_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户-角色';

CREATE TABLE blog_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    role_id       BIGINT   NOT NULL,
    permission_id BIGINT   NOT NULL,
    create_time   DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES blog_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES blog_permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色-权限';

CREATE TABLE blog_category (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '父分类，0 为根',
    name          VARCHAR(50)  NOT NULL,
    slug          VARCHAR(100) NOT NULL COMMENT 'URL 标识',
    description   VARCHAR(500) NULL,
    sort          INT          NOT NULL DEFAULT 0,
    article_count INT          NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL,
    update_time   DATETIME     NOT NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_slug (slug),
    KEY idx_category_parent (parent_id, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分类';

CREATE TABLE blog_tag (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50)  NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    article_count INT          NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '标签';

CREATE TABLE blog_column (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    slug          VARCHAR(100) NOT NULL,
    description   VARCHAR(500) NULL,
    cover         VARCHAR(500) NULL,
    article_count INT          NOT NULL DEFAULT 0,
    sort          INT          NOT NULL DEFAULT 0,
    create_time   DATETIME     NOT NULL,
    update_time   DATETIME     NOT NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_column_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '专栏';

CREATE TABLE blog_article (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    author_id       BIGINT       NOT NULL,
    category_id     BIGINT       NULL,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL,
    summary         VARCHAR(500) NULL,
    cover           VARCHAR(500) NULL,
    content         LONGTEXT     NOT NULL COMMENT 'Markdown 原文',
    content_html    LONGTEXT     NULL COMMENT '可选渲染缓存，必须净化',
    content_format  VARCHAR(20)  NOT NULL DEFAULT 'markdown',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0 草稿 1 已发布 2 下线 3 回收',
    visibility      TINYINT      NOT NULL DEFAULT 0 COMMENT '0 公开 1 私密',
    is_top          TINYINT      NOT NULL DEFAULT 0,
    is_recommend    TINYINT      NOT NULL DEFAULT 0,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    like_count      BIGINT       NOT NULL DEFAULT 0,
    comment_count   BIGINT       NOT NULL DEFAULT 0,
    seo_title       VARCHAR(200) NULL,
    seo_description VARCHAR(500) NULL,
    publish_time    DATETIME     NULL,
    create_time     DATETIME     NOT NULL,
    update_time     DATETIME     NOT NULL,
    is_deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_slug (slug),
    KEY idx_article_status_publish (status, publish_time),
    KEY idx_article_category_status (category_id, status),
    KEY idx_article_top_publish (is_top, publish_time),
    KEY idx_article_author (author_id),
    CONSTRAINT fk_article_author FOREIGN KEY (author_id) REFERENCES blog_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章';

CREATE TABLE blog_article_tag (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_tag (article_id, tag_id),
    KEY idx_article_tag_tag (tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES blog_article (id),
    CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id) REFERENCES blog_tag (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章-标签';

CREATE TABLE blog_column_article (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    column_id  BIGINT NOT NULL,
    article_id BIGINT NOT NULL,
    sort       INT    NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_column_article (column_id, article_id),
    KEY idx_column_article_sort (column_id, sort),
    CONSTRAINT fk_column_article_column FOREIGN KEY (column_id) REFERENCES blog_column (id),
    CONSTRAINT fk_column_article_article FOREIGN KEY (article_id) REFERENCES blog_article (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '专栏-文章';

CREATE TABLE blog_comment (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    article_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父评论，0 为顶层',
    content    VARCHAR(2000) NOT NULL,
    status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 已通过 2 已拒绝',
    ip         VARCHAR(45)  NULL,
    user_agent VARCHAR(500) NULL,
    create_time DATETIME    NOT NULL,
    update_time DATETIME    NOT NULL,
    is_deleted TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_comment_article (article_id, status, create_time),
    KEY idx_comment_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章评论';

CREATE TABLE blog_guestbook (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL COMMENT '游客留言可为空',
    nickname   VARCHAR(50)  NOT NULL,
    email      VARCHAR(128) NULL,
    content    VARCHAR(2000) NOT NULL,
    parent_id  BIGINT       NOT NULL DEFAULT 0,
    status     TINYINT      NOT NULL DEFAULT 0,
    reply      VARCHAR(2000) NULL COMMENT '博主回复',
    reply_time DATETIME     NULL,
    ip         VARCHAR(45)  NULL,
    create_time DATETIME    NOT NULL,
    update_time DATETIME    NOT NULL,
    is_deleted TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_guestbook_status (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '留言板';

CREATE TABLE blog_favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    article_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite (user_id, article_id),
    KEY idx_favorite_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收藏';

CREATE TABLE blog_article_like (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    article_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_like (user_id, article_id),
    KEY idx_like_article (article_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '点赞';

CREATE TABLE blog_notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '接收者',
    type        VARCHAR(30)  NOT NULL COMMENT 'COMMENT/REPLY/LIKE/SYSTEM',
    title       VARCHAR(200) NOT NULL,
    content     VARCHAR(1000) NULL,
    link        VARCHAR(500) NULL,
    is_read     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_user (user_id, is_read, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知';

CREATE TABLE blog_resource (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uploader_id   BIGINT       NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name   VARCHAR(255) NOT NULL COMMENT '随机文件名',
    path          VARCHAR(500) NOT NULL,
    url           VARCHAR(500) NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    size          BIGINT       NOT NULL,
    sha256        CHAR(64)     NULL,
    create_time   DATETIME     NOT NULL,
    is_deleted    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_stored (stored_name),
    KEY idx_resource_uploader (uploader_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '资源元数据';

CREATE TABLE blog_system_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_key   VARCHAR(100) NOT NULL,
    config_value VARCHAR(2000) NULL,
    description  VARCHAR(200) NULL,
    is_public    TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL,
    update_time  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '系统配置';

CREATE TABLE blog_announcement (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    content     VARCHAR(1000) NOT NULL,
    is_active   TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL,
    update_time DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告';

CREATE TABLE blog_friend_link (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    url         VARCHAR(500) NOT NULL,
    description VARCHAR(200) NULL,
    sort        INT          NOT NULL DEFAULT 0,
    is_active   TINYINT      NOT NULL DEFAULT 1,
    create_time DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '友情链接';

CREATE TABLE blog_login_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NULL,
    email        VARCHAR(128) NULL,
    ip           VARCHAR(45)  NULL,
    user_agent   VARCHAR(500) NULL,
    login_status TINYINT      NOT NULL COMMENT '1 成功 0 失败',
    message      VARCHAR(200) NULL,
    create_time  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_login_log_time (create_time),
    KEY idx_login_log_user (user_id),
    KEY idx_login_log_status (login_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '登录日志';

CREATE TABLE blog_operation_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    operator_id  BIGINT       NULL,
    operator_name VARCHAR(50) NULL,
    module       VARCHAR(50)  NOT NULL,
    action       VARCHAR(100) NOT NULL,
    target_id    BIGINT       NULL,
    detail       VARCHAR(2000) NULL,
    ip           VARCHAR(45)  NULL,
    create_time  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_operation_log_time (create_time),
    KEY idx_operation_log_operator (operator_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '后台操作日志';

CREATE TABLE blog_article_revision (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    article_id    BIGINT       NOT NULL,
    editor_id     BIGINT       NOT NULL,
    title         VARCHAR(200) NOT NULL,
    content       LONGTEXT     NOT NULL,
    revision_note VARCHAR(200) NULL,
    create_time   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_revision_article (article_id, create_time),
    CONSTRAINT fk_revision_article FOREIGN KEY (article_id) REFERENCES blog_article (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '文章修订历史';
