-- 兼容历史数据/测试夹具：账号名允许为空，唯一索引仍保证非空值不重复
ALTER TABLE blog_user MODIFY COLUMN username VARCHAR(50) NULL;
