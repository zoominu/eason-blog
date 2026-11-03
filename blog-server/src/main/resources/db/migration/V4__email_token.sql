-- V4：邮箱验证与找回密码的一次性 token
CREATE TABLE blog_email_token (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL COMMENT 'VERIFY_EMAIL / RESET_PASSWORD',
    token_hash  CHAR(64)    NOT NULL COMMENT 'SHA-256(token)，不存明文',
    expires_at  DATETIME    NOT NULL,
    used        TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_email_token_hash (token_hash),
    KEY idx_email_token_user (user_id, type),
    CONSTRAINT fk_email_token_user FOREIGN KEY (user_id) REFERENCES blog_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '邮箱验证/重置密码 token';
