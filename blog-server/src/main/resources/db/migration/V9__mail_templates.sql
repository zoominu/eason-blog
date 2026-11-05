-- 邮件模板（可在后台修改）
INSERT INTO blog_system_config (config_key, config_value, description, is_public, create_time, update_time)
VALUES
    ('mail.template.register-code',
     '你好：\n\n你的注册验证码是 {code}，{minutes} 分钟内有效。\n\n如果不是你本人操作，请忽略这封邮件。\n\n{site}',
     '注册验证码邮件模板，支持 {code} {minutes} {site}', 0, NOW(), NOW()),
    ('mail.template.reset',
     '你好 {name}：\n\n点击下面的链接重置密码（30 分钟内有效，仅可使用一次）：\n{link}\n\n如果不是你本人操作，请忽略这封邮件。\n\n{site}',
     '重置密码邮件模板，支持 {name} {link} {site}', 0, NOW(), NOW()),
    ('mail.template.verify',
     '你好 {name}：\n\n点击下面的链接完成邮箱验证（24 小时内有效）：\n{link}\n\n如果这不是你本人注册，请忽略这封邮件。\n\n{site}',
     '邮箱验证邮件模板，支持 {name} {link} {site}', 0, NOW(), NOW()),
    ('mail.template.subscription',
     '发布了新文章《{title}》\n\n{link}\n\n如果不想再收到，可在个人中心关闭订阅。\n\n{site}',
     '新文章订阅通知模板，支持 {title} {link} {site}', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), update_time = NOW();

-- 站点对外地址为空时给一个明确的提示值，避免邮件里出现 localhost
UPDATE blog_system_config
SET config_value = '', update_time = NOW()
WHERE config_key = 'site.url' AND config_value = 'http://localhost';
