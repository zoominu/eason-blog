-- 账号名：站内唯一、不可修改；登录改用账号，邮箱只做验证/订阅/找回密码
ALTER TABLE blog_user
    ADD COLUMN username VARCHAR(50) NULL AFTER email;

UPDATE blog_user SET username = NULLIF(TRIM(nickname), '') WHERE username IS NULL;
UPDATE blog_user SET username = CONCAT('user', id) WHERE username IS NULL OR username = '';
UPDATE blog_user SET username = 'admin' WHERE email = 'admin@eason.blog';

-- 去掉重名：保留最早一条，其余追加 id
UPDATE blog_user u
    JOIN (SELECT username FROM blog_user GROUP BY username HAVING COUNT(*) > 1) d
    ON u.username = d.username
SET u.username = CONCAT(u.username, '_', u.id);

ALTER TABLE blog_user
    MODIFY COLUMN username VARCHAR(50) NOT NULL,
    ADD UNIQUE KEY uk_user_username (username);
