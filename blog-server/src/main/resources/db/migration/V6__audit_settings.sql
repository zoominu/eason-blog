-- V6：评论/留言违禁词审核配置
INSERT INTO blog_system_config (config_key, config_value, description, is_public, create_time, update_time) VALUES
    ('audit.banned-words',  '',
     '违禁词（每行一个，逗号也可）；审核关闭时命中才拦截',
     0, NOW(), NOW()),
    ('audit.banned-action', 'review',
     '命中违禁词的处理方式：review=转人工审核 / mask=替换为*后展示',
     0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description);
