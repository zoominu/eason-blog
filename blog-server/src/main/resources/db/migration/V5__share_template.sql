-- V5：分享文案模板（公开配置，后台可改）
-- {title} {url} {summary} 三个占位符由前端替换。
INSERT INTO blog_system_config (config_key, config_value, description, is_public, create_time, update_time)
VALUES ('share.template',
        '推荐一篇好文：{title}\n{url}',
        '分享文案模板，支持 {title} / {url} / {summary}',
        1, NOW(), NOW());
