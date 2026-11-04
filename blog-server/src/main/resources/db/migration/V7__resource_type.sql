-- 资源分类：头像 / 文章图片 / 音乐 / 视频 / 文档 / 其他
ALTER TABLE blog_resource
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'article_image' AFTER mime_type;

CREATE INDEX idx_resource_type ON blog_resource (type);

-- 取消回收站：历史 status=3 的文章统一转成「已下线」
UPDATE blog_article SET status = 2 WHERE status = 3;
