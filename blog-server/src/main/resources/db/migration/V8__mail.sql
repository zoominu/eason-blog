-- 邮件：验证码 + 订阅
CREATE TABLE IF NOT EXISTS blog_email_code (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    email       VARCHAR(128) NOT NULL,
    code_hash   CHAR(64)    NOT NULL,
    purpose     VARCHAR(32) NOT NULL,
    expires_at  DATETIME    NOT NULL,
    used        TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_email_purpose (email, purpose, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邮箱验证码';

CREATE TABLE IF NOT EXISTS blog_subscription (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NULL,
    email       VARCHAR(128) NOT NULL,
    enabled     TINYINT     NOT NULL DEFAULT 1,
    create_time DATETIME    NOT NULL,
    update_time DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邮件订阅';

INSERT INTO blog_system_config (config_key, config_value, description, is_public, create_time, update_time)
VALUES
    ('mail.enabled', 'false', '是否启用邮件（开启后注册需要邮箱验证码）', 0, NOW(), NOW()),
    ('mail.host', '', 'SMTP 服务器，例如 smtp.qq.com', 0, NOW(), NOW()),
    ('mail.port', '465', 'SMTP 端口，SSL 一般为 465', 0, NOW(), NOW()),
    ('mail.username', '', 'SMTP 登录账号（完整邮箱）', 0, NOW(), NOW()),
    ('mail.password', '', 'SMTP 授权码（不是邮箱登录密码）', 0, NOW(), NOW()),
    ('mail.from', '', '发件人显示地址，留空则用 SMTP 账号', 0, NOW(), NOW()),
    ('mail.ssl', 'true', '是否使用 SSL（465 端口一般选是）', 0, NOW(), NOW()),
    ('site.url', '', '站点对外地址，用于邮件里的文章链接', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), update_time = NOW();
