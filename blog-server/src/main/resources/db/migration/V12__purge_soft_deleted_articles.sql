-- 清理历史上“逻辑删除”的文章及其孤立的关联数据。
-- 旧的 delete 只置 is_deleted=1，不释放 slug 唯一索引，导致重建同 slug 直接 500。
-- 这些文章在界面上已不可见，这里一次性物理清理；之后删除走硬删除。
DELETE FROM blog_article_revision
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_article_tag
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_column_article
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_comment
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_favorite
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_article_like
WHERE article_id IN (SELECT id FROM blog_article WHERE is_deleted = 1);

DELETE FROM blog_article WHERE is_deleted = 1;
