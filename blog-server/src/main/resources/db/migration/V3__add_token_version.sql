-- V3：JWT 版本号
-- 改密后自增，使旧 Cookie 里的 token 立即失效（JWT 无状态，无法主动撤销）。
ALTER TABLE blog_user
    ADD COLUMN token_version INT NOT NULL DEFAULT 0 COMMENT 'JWT 版本号，改密后自增';
