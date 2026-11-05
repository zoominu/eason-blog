package com.eason.blog.module.interaction.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.common.security.RateLimiter;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.article.service.ArticleTagResolver;
import com.eason.blog.module.article.vo.ArticleSummaryVO;
import com.eason.blog.module.category.entity.Category;
import com.eason.blog.module.category.mapper.CategoryMapper;
import com.eason.blog.module.comment.entity.Comment;
import com.eason.blog.module.comment.mapper.CommentMapper;
import com.eason.blog.module.comment.vo.AdminCommentVO;
import com.eason.blog.module.comment.vo.CommentVO;
import com.eason.blog.module.comment.vo.InteractionVO;
import com.eason.blog.module.comment.vo.MyCommentVO;
import com.eason.blog.module.favorite.entity.Favorite;
import com.eason.blog.module.favorite.mapper.FavoriteMapper;
import com.eason.blog.module.interaction.entity.Subscription;
import com.eason.blog.module.interaction.mapper.SubscriptionMapper;
import com.eason.blog.module.guestbook.entity.Guestbook;
import com.eason.blog.module.guestbook.mapper.GuestbookMapper;
import com.eason.blog.module.guestbook.vo.AdminGuestbookVO;
import com.eason.blog.module.like.entity.ArticleLike;
import com.eason.blog.module.like.mapper.ArticleLikeMapper;
import com.eason.blog.module.notification.entity.Notification;
import com.eason.blog.module.notification.mapper.NotificationMapper;
import com.eason.blog.module.notification.vo.NotificationVO;
import com.eason.blog.module.user.entity.User;
import com.eason.blog.module.user.mapper.UserMapper;
import com.eason.blog.module.user.mapper.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户互动：评论、点赞、收藏、通知、留言，以及后台审核。
 *
 * <p>关键约束：
 * <ul>
 *   <li>评论与留言默认待审核（status=0），审核通过后才对访客可见；</li>
 *   <li>点赞/收藏依赖联合唯一键，重复请求不报错也不重复计数；</li>
 *   <li>文章上的 like_count / comment_count 用原子 UPDATE 维护，避免读改写丢更新。</li>
 * </ul></p>
 */
@Service
public class InteractionService {

    private static final int PENDING = 0;
    private static final int APPROVED = 1;
    private static final int REJECTED = 2;

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final FavoriteMapper favoriteMapper;
    private final ArticleLikeMapper articleLikeMapper;
    private final NotificationMapper notificationMapper;
    private final GuestbookMapper guestbookMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final RateLimiter rateLimiter;
    private final ArticleTagResolver tagResolver;
    private final UserRoleMapper userRoleMapper;
    private final ContentAuditPolicy auditPolicy;
    private final SubscriptionMapper subscriptionMapper;

    public InteractionService(ArticleMapper articleMapper,
                              CommentMapper commentMapper,
                              FavoriteMapper favoriteMapper,
                              ArticleLikeMapper articleLikeMapper,
                              NotificationMapper notificationMapper,
                              GuestbookMapper guestbookMapper,
                              UserMapper userMapper,
                              CategoryMapper categoryMapper,
                              RateLimiter rateLimiter,
                              ArticleTagResolver tagResolver,
                              UserRoleMapper userRoleMapper,
                              ContentAuditPolicy auditPolicy,
                              SubscriptionMapper subscriptionMapper) {
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.favoriteMapper = favoriteMapper;
        this.articleLikeMapper = articleLikeMapper;
        this.notificationMapper = notificationMapper;
        this.guestbookMapper = guestbookMapper;
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
        this.rateLimiter = rateLimiter;
        this.tagResolver = tagResolver;
        this.userRoleMapper = userRoleMapper;
        this.auditPolicy = auditPolicy;
        this.subscriptionMapper = subscriptionMapper;
    }

    // ------------------------------------------------------------------ 评论

    /**
     * 评论分页。
     *
     * <p>按“顶层评论”分页，每条顶层评论连同其全部回复一起返回：
     * 如果按记录数分页，会把一条对话拆到两页，阅读体验很差。
     * 数据量是单篇文章级别，内存分页足够。</p>
     *
     * <p>可见性：已通过的评论正常展示；被拒绝/待审核但下面还有已通过回复的评论，
     * 以「当前评论已被隐藏」占位保留，避免回复凭空消失。</p>
     */
    public PageResult<CommentVO> comments(String slug, Long currentUserId, int pageNo, int size) {
        Article article = requirePublicArticleBySlug(slug);
        int page = Math.max(pageNo, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);

        List<Comment> all = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getArticleId, article.getId())
                .orderByAsc(Comment::getCreateTime));
        if (all.isEmpty()) {
            return PageResult.empty(page, pageSize);
        }

        Map<Long, Comment> byId = all.stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
        Map<Long, List<Comment>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));

        // 已通过的节点 + 它们的祖先链可见；其余节点不展示
        Set<Long> visible = new HashSet<>();
        for (Comment c : all) {
            if (c.getStatus() != null && c.getStatus() == APPROVED) {
                Long cursor = c.getId();
                int guard = 0;
                while (cursor != null && cursor > 0 && visible.add(cursor) && guard++ < 50) {
                    Comment node = byId.get(cursor);
                    cursor = node == null ? null : node.getParentId();
                }
            }
        }
        if (visible.isEmpty()) {
            return PageResult.empty(page, pageSize);
        }

        Map<Long, User> users = userMapper.selectBatchIds(
                        all.stream().map(Comment::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> authorIds = new HashSet<>(userRoleMapper.selectUserIdsByRoleCode("SUPER_ADMIN"));

        List<Comment> roots = byParent.getOrDefault(0L, List.of()).stream()
                .filter(c -> visible.contains(c.getId()))
                .toList();
        int total = roots.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<CommentVO> records = new ArrayList<>(to - from);
        for (Comment root : roots.subList(from, to)) {
            records.add(toVO(root, byParent, users, currentUserId, authorIds, visible));
        }

        long pages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;
        return new PageResult<>(records, total, page, pageSize, pages);
    }

    private CommentVO toVO(Comment c, Map<Long, List<Comment>> byParent,
                           Map<Long, User> users, Long currentUserId,
                           Set<Long> authorIds, Set<Long> visible) {
        User user = users.get(c.getUserId());
        boolean hidden = c.getStatus() == null || c.getStatus() != APPROVED;
        return new CommentVO(
                c.getId(),
                c.getUserId(),
                user == null ? "已注销用户" : user.getNickname(),
                user == null ? null : user.getAvatar(),
                hidden ? "当前评论已被隐藏" : c.getContent(),
                c.getParentId(),
                c.getCreateTime(),
                currentUserId != null && currentUserId.equals(c.getUserId()),
                authorIds.contains(c.getUserId()),
                hidden,
                byParent.getOrDefault(c.getId(), List.of()).stream()
                        .filter(child -> visible.contains(child.getId()))
                        .map(child -> toVO(child, byParent, users, currentUserId, authorIds, visible))
                        .toList());
    }

    /** 新增评论的结果：前端据此决定是刷新展示还是提示待审核。 */
    public record CommentCreated(Long id, int status) {
    }

    @Transactional
    public CommentCreated addComment(Long userId, Long articleId, String content, Long parentId, String ip) {
        // 防刷：同一用户每分钟最多 5 条评论
        rateLimiter.check("comment:" + userId, 5, 60);
        Article article = requirePublicArticle(articleId);

        Comment parent = null;
        if (parentId != null && parentId > 0) {
            parent = commentMapper.selectById(parentId);
            if (parent == null || !Objects.equals(parent.getArticleId(), articleId)) {
                throw BusinessException.badRequest("回复的评论不存在");
            }
            // 不允许自己回复自己：既无意义，也容易变成刷楼
            if (Objects.equals(parent.getUserId(), userId)) {
                throw BusinessException.badRequest("不能回复自己的评论");
            }
            // 评论最多四级：父评论已经是第 4 级时不允许再回复
            if (commentLevel(parent) >= 4) {
                throw BusinessException.badRequest("评论最多支持四级回复");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = userRoleMapper.selectRoleCodesByUserId(userId).contains("SUPER_ADMIN");
        // 管理员评论直接发布；普通用户按「审核开关 + 违禁词」策略决定
        ContentAuditPolicy.Result audit = isAdmin
                ? new ContentAuditPolicy.Result(APPROVED, content.trim(), null)
                : auditPolicy.check(content.trim(), "comment.audit");

        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setUserId(userId);
        comment.setParentId(parent == null ? 0L : parent.getId());
        comment.setContent(audit.content());
        comment.setStatus(audit.status());
        comment.setIp(ip);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);
        comment.setIsDeleted(0);
        commentMapper.insert(comment);

        if (audit.status() == APPROVED) {
            articleMapper.updateCommentCount(articleId, 1);
        }

        User author = userMapper.selectById(userId);
        String nickname = author == null ? "访客" : author.getNickname();
        boolean published = audit.status() == APPROVED;

        // 通知文章作者（本站只有一个作者，即管理员）：带评论摘要与定位参数
        if (!Objects.equals(article.getAuthorId(), userId)) {
            notify(article.getAuthorId(), "COMMENT", published ? "收到新评论" : "收到待审核评论",
                    nickname + " 评论了《" + article.getTitle() + "》：「" + excerpt(audit.content())
                            + "」" + (published ? "，已发布" : "，待审核"),
                    published
                            ? "/article/" + article.getSlug() + "?comment=" + comment.getId()
                            : "/admin/comments?highlight=" + comment.getId());
        }
        if (parent != null && !Objects.equals(parent.getUserId(), userId)) {
            notify(parent.getUserId(), "REPLY", "有人回复了你的评论",
                    nickname + " 回复了你在《" + article.getTitle() + "》下的评论：「" + excerpt(audit.content()) + "」",
                    "/article/" + article.getSlug() + "?comment=" + comment.getId());
        }
        return new CommentCreated(comment.getId(), audit.status());
    }

    /** 计算评论层级：顶层=1，回复顶层=2…… 最多向上追 10 层，避免脏数据死循环。 */
    private int commentLevel(Comment comment) {
        int level = 1;
        Comment cursor = comment;
        while (cursor.getParentId() != null && cursor.getParentId() > 0 && level < 10) {
            cursor = commentMapper.selectById(cursor.getParentId());
            if (cursor == null) {
                break;
            }
            level++;
        }
        return level;
    }

    /** 通知里只放摘要，避免长评论把列表撑爆 */
    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("\\s+", " ").trim();
        return plain.length() <= 40 ? plain : plain.substring(0, 40) + "…";
    }

    // --------------------------------------------------------------- 点赞收藏

    public InteractionVO interaction(Long userId, Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }
        boolean liked = userId != null && articleLikeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getUserId, userId).eq(ArticleLike::getArticleId, articleId)) > 0;
        boolean favorited = userId != null && favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).eq(Favorite::getArticleId, articleId)) > 0;
        return new InteractionVO(liked, favorited,
                article.getLikeCount() == null ? 0 : article.getLikeCount(),
                article.getCommentCount() == null ? 0 : article.getCommentCount());
    }

    @Transactional
    public InteractionVO like(Long userId, Long articleId) {
        requirePublicArticle(articleId);
        Long exists = articleLikeMapper.selectCount(Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getUserId, userId).eq(ArticleLike::getArticleId, articleId));
        if (exists == null || exists == 0) {
            ArticleLike like = new ArticleLike();
            like.setUserId(userId);
            like.setArticleId(articleId);
            like.setCreateTime(LocalDateTime.now());
            try {
                articleLikeMapper.insert(like);
                articleMapper.updateLikeCount(articleId, 1);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点赞：唯一键兜底，计数不加
            }
        }
        return interaction(userId, articleId);
    }

    @Transactional
    public InteractionVO unlike(Long userId, Long articleId) {
        int deleted = articleLikeMapper.delete(Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getUserId, userId).eq(ArticleLike::getArticleId, articleId));
        if (deleted > 0) {
            articleMapper.updateLikeCount(articleId, -1);
        }
        return interaction(userId, articleId);
    }

    @Transactional
    public InteractionVO favorite(Long userId, Long articleId) {
        requirePublicArticle(articleId);
        Long exists = favoriteMapper.selectCount(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).eq(Favorite::getArticleId, articleId));
        if (exists == null || exists == 0) {
            Favorite favorite = new Favorite();
            favorite.setUserId(userId);
            favorite.setArticleId(articleId);
            favorite.setCreateTime(LocalDateTime.now());
            try {
                favoriteMapper.insert(favorite);
            } catch (DuplicateKeyException ignored) {
                // 幂等：重复收藏忽略
            }
        }
        return interaction(userId, articleId);
    }

    @Transactional
    public InteractionVO unfavorite(Long userId, Long articleId) {
        favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId).eq(Favorite::getArticleId, articleId));
        return interaction(userId, articleId);
    }

    /**
     * 我的收藏。
     *
     * <p>只返回仍然公开可读的文章：文章被下线或改为私密后，收藏列表不能再暴露标题与摘要，
     * 同时把 total 调整为“可读收藏数”，避免出现空白页。</p>
     */
    public PageResult<ArticleSummaryVO> favorites(Long userId, int pageNo, int size) {
        Page<Favorite> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        favoriteMapper.selectPage(page, Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreateTime));
        if (page.getRecords().isEmpty()) {
            return PageResult.empty(pageNo, size);
        }

        List<Long> favoriteIds = page.getRecords().stream().map(Favorite::getArticleId).toList();
        List<Article> readable = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .in(Article::getId, favoriteIds)
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0));

        // 按收藏时间顺序还原
        Map<Long, Article> byId = readable.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, List<String>> tagNames = tagResolver.byArticleIds(readable.stream()
                .map(Article::getId).toList());

        List<ArticleSummaryVO> records = favoriteIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(a -> toSummary(a, tagNames.getOrDefault(a.getId(), List.of())))
                .toList();

        long total = Math.max(0, page.getTotal() - (favoriteIds.size() - records.size()));
        return new PageResult<>(records, total, page.getCurrent(), page.getSize(), page.getPages());
    }

    // ------------------------------------------------------------------ 通知

    public List<NotificationVO> notifications(Long userId, int limit, String type) {
        return notificationMapper.selectList(Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(org.springframework.util.StringUtils.hasText(type), Notification::getType, type)
                        .orderByDesc(Notification::getCreateTime)
                        .last("limit " + Math.min(Math.max(limit, 1), 50)))
                .stream()
                .map(n -> new NotificationVO(n.getId(), n.getType(), n.getTitle(),
                        n.getContent(), n.getLink(), n.getIsRead() != null && n.getIsRead() == 1,
                        n.getCreateTime()))
                .toList();
    }

    // ---------------------------------------------------------- 邮件订阅

    /** 当前用户是否订阅了新文章邮件。 */
    public boolean subscribed(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null) {
            return false;
        }
        Long count = subscriptionMapper.selectCount(Wrappers.<Subscription>lambdaQuery()
                .eq(Subscription::getEmail, user.getEmail())
                .eq(Subscription::getEnabled, 1));
        return count != null && count > 0;
    }

    /** 开启/关闭订阅（按邮箱去重）。 */
    @Transactional
    public void setSubscription(Long userId, boolean enabled) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getEmail() == null) {
            throw BusinessException.notFound("用户不存在");
        }
        Subscription existing = subscriptionMapper.selectOne(Wrappers.<Subscription>lambdaQuery()
                .eq(Subscription::getEmail, user.getEmail())
                .last("limit 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            Subscription sub = new Subscription();
            sub.setUserId(userId);
            sub.setEmail(user.getEmail());
            sub.setEnabled(enabled ? 1 : 0);
            sub.setCreateTime(now);
            sub.setUpdateTime(now);
            subscriptionMapper.insert(sub);
        } else {
            existing.setEnabled(enabled ? 1 : 0);
            existing.setUserId(userId);
            existing.setUpdateTime(now);
            subscriptionMapper.updateById(existing);
        }
    }

    // ------------------------------------------------------- 个人中心管理

    /** 我点赞过的文章（只返回仍然公开可读的）。 */
    public PageResult<ArticleSummaryVO> likes(Long userId, int pageNo, int size) {
        Page<ArticleLike> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        articleLikeMapper.selectPage(page, Wrappers.<ArticleLike>lambdaQuery()
                .eq(ArticleLike::getUserId, userId)
                .orderByDesc(ArticleLike::getCreateTime));
        if (page.getRecords().isEmpty()) {
            return PageResult.empty(pageNo, size);
        }
        List<Long> ids = page.getRecords().stream().map(ArticleLike::getArticleId).toList();
        List<Article> readable = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                .in(Article::getId, ids)
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0));
        Map<Long, Article> byId = readable.stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, List<String>> tagNames = tagResolver.byArticleIds(readable.stream()
                .map(Article::getId).toList());

        List<ArticleSummaryVO> records = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(a -> toSummary(a, tagNames.getOrDefault(a.getId(), List.of())))
                .toList();
        long total = Math.max(0, page.getTotal() - (ids.size() - records.size()));
        return new PageResult<>(records, total, page.getCurrent(), page.getSize(), page.getPages());
    }

    /** 批量取消点赞，返回实际取消数量。 */
    @Transactional
    public int unlikeBatch(Long userId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (Long articleId : articleIds.stream().filter(Objects::nonNull).distinct().toList()) {
            int deleted = articleLikeMapper.delete(Wrappers.<ArticleLike>lambdaQuery()
                    .eq(ArticleLike::getUserId, userId)
                    .eq(ArticleLike::getArticleId, articleId));
            if (deleted > 0) {
                articleMapper.updateLikeCount(articleId, -1);
                removed++;
            }
        }
        return removed;
    }

    /** 批量取消收藏，返回实际取消数量。 */
    @Transactional
    public int unfavoriteBatch(Long userId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return 0;
        }
        return favoriteMapper.delete(Wrappers.<Favorite>lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .in(Favorite::getArticleId, articleIds.stream().filter(Objects::nonNull).distinct().toList()));
    }

    /** 我的评论（含待审核/已拒绝），并给出是否可自行删除。 */
    public PageResult<MyCommentVO> myComments(Long userId, int pageNo, int size) {
        Page<Comment> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 50));
        commentMapper.selectPage(page, Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getUserId, userId)
                .orderByDesc(Comment::getCreateTime));
        Map<Long, Article> articles = batchArticles(page.getRecords().stream()
                .map(Comment::getArticleId).collect(Collectors.toSet()));

        return PageResult.of(page, c -> {
            Article article = articles.get(c.getArticleId());
            boolean canDelete = Objects.equals(c.getUserId(), userId)
                    && withinDeleteWindow(c)
                    && !hasReplies(c.getId());
            return new MyCommentVO(c.getId(), c.getArticleId(),
                    article == null ? "文章已删除" : article.getTitle(),
                    article == null ? null : article.getSlug(),
                    c.getContent(), c.getStatus(), c.getCreateTime(), canDelete);
        });
    }

    /** 删除自己的评论：限 3 分钟内、且没有回复。 */
    @Transactional
    public void deleteOwnComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw BusinessException.notFound("评论不存在");
        }
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }
        if (!withinDeleteWindow(comment)) {
            throw BusinessException.badRequest("评论发布超过 3 分钟，无法删除");
        }
        if (hasReplies(commentId)) {
            throw BusinessException.badRequest("该评论已有回复，无法删除");
        }
        commentMapper.deleteById(commentId);
        if (comment.getStatus() != null && comment.getStatus() == APPROVED) {
            articleMapper.updateCommentCount(comment.getArticleId(), -1);
        }
    }

    /** 批量删除自己的评论：跳过超过时限或有回复的，返回删除数量。 */
    @Transactional
    public int deleteOwnComments(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            try {
                deleteOwnComment(userId, id);
                deleted++;
            } catch (BusinessException ignored) {
                // 超时/有回复/不属于自己：跳过
            }
        }
        return deleted;
    }

    private boolean withinDeleteWindow(Comment comment) {
        return comment.getCreateTime() != null
                && comment.getCreateTime().isAfter(LocalDateTime.now().minusMinutes(3));
    }

    private boolean hasReplies(Long commentId) {
        Long count = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                .eq(Comment::getParentId, commentId));
        return count != null && count > 0;
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !Objects.equals(notification.getUserId(), userId)) {
            throw BusinessException.notFound("通知不存在");
        }
        notification.setIsRead(1);
        notificationMapper.updateById(notification);
    }

    /** 一键已读：只更新当前用户未读的通知。 */
    @Transactional
    public int markAllRead(Long userId) {
        Notification update = new Notification();
        update.setIsRead(1);
        return notificationMapper.update(update, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
    }

    /** 删除已读通知。 */
    @Transactional
    public int deleteRead(Long userId) {
        return notificationMapper.delete(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 1));
    }

    private void notify(Long userId, String type, String title, String content, String link) {
        if (userId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setLink(link);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    // ----------------------------------------------------------------- 留言

    @Transactional
    public void addGuestbook(Long userId, String content, String ip) {
        // 防刷：同一用户 5 分钟最多 3 条留言
        rateLimiter.check("guestbook:" + userId, 3, 300);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean isAdmin = userRoleMapper.selectRoleCodesByUserId(userId).contains("SUPER_ADMIN");
        ContentAuditPolicy.Result audit = isAdmin
                ? new ContentAuditPolicy.Result(APPROVED, content.trim(), null)
                : auditPolicy.check(content.trim(), "guestbook.audit");

        Guestbook guestbook = new Guestbook();
        guestbook.setUserId(userId);
        guestbook.setNickname(user.getNickname());
        guestbook.setEmail(user.getEmail());
        guestbook.setContent(audit.content());
        guestbook.setParentId(0L);
        guestbook.setStatus(audit.status());
        guestbook.setIp(ip);
        guestbook.setCreateTime(now);
        guestbook.setUpdateTime(now);
        guestbook.setIsDeleted(0);
        guestbookMapper.insert(guestbook);
    }

    // ------------------------------------------------------------ 后台审核

    /**
     * 后台评论列表：支持按状态、文章、用户、关键字（内容/昵称/邮箱/文章标题）筛选。
     */
    public PageResult<AdminCommentVO> adminComments(Integer status, String keyword,
                                                    Long articleId, Long userId,
                                                    int pageNo, int size) {
        var wrapper = Wrappers.<Comment>lambdaQuery()
                .eq(status != null, Comment::getStatus, status)
                .eq(articleId != null, Comment::getArticleId, articleId)
                .eq(userId != null, Comment::getUserId, userId);

        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            List<Long> matchedUsers = userMapper.selectList(Wrappers.<User>lambdaQuery()
                            .and(w -> w.like(User::getNickname, kw).or().like(User::getEmail, kw))
                            .last("limit 500"))
                    .stream().map(User::getId).toList();
            List<Long> matchedArticles = articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                            .like(Article::getTitle, kw)
                            .last("limit 500"))
                    .stream().map(Article::getId).toList();
            wrapper.and(w -> {
                w.like(Comment::getContent, kw);
                if (!matchedUsers.isEmpty()) {
                    w.or().in(Comment::getUserId, matchedUsers);
                }
                if (!matchedArticles.isEmpty()) {
                    w.or().in(Comment::getArticleId, matchedArticles);
                }
            });
        }

        Page<Comment> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 100));
        commentMapper.selectPage(page, wrapper.orderByDesc(Comment::getCreateTime));

        Map<Long, Article> articles = batchArticles(page.getRecords().stream()
                .map(Comment::getArticleId).collect(Collectors.toSet()));
        Map<Long, User> users = batchUsers(page.getRecords().stream()
                .map(Comment::getUserId).collect(Collectors.toSet()));

        return PageResult.of(page, c -> {
            Article article = articles.get(c.getArticleId());
            User user = users.get(c.getUserId());
            return new AdminCommentVO(c.getId(), c.getArticleId(),
                    article == null ? "（已删除）" : article.getTitle(),
                    user == null ? "—" : user.getNickname(),
                    user == null ? "—" : user.getEmail(),
                    c.getContent(), c.getStatus(), c.getCreateTime());
        });
    }

    @Transactional
    public void moderateComment(Long id, boolean approve) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BusinessException.notFound("评论不存在");
        }
        int before = comment.getStatus() == null ? PENDING : comment.getStatus();
        int after = approve ? APPROVED : REJECTED;
        if (before == after) {
            return;
        }
        comment.setStatus(after);
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.updateById(comment);

        // 计数只统计已通过：待审核→通过 +1；已通过→拒绝 -1
        if (after == APPROVED) {
            articleMapper.updateCommentCount(comment.getArticleId(), 1);
        } else if (before == APPROVED) {
            articleMapper.updateCommentCount(comment.getArticleId(), -1);
        }
        if (approve) {
            notifyApprovedComment(comment);
        }
    }

    /** 审核通过通知：带上评论摘要与文章跳转（?comment=id 用于前端高亮）。 */
    private void notifyApprovedComment(Comment comment) {
        Article article = articleMapper.selectById(comment.getArticleId());
        String title = article == null ? titleOf(comment.getArticleId()) : article.getTitle();
        String slug = article == null ? "" : article.getSlug();
        String link = "/article/" + slug + "?comment=" + comment.getId();
        // 只在第一次通过时通知：拒绝后再通过不重复打扰
        Long exists = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, comment.getUserId())
                .eq(Notification::getLink, link));
        if (exists != null && exists > 0) {
            return;
        }
        notify(comment.getUserId(), "SYSTEM", "评论已通过审核",
                "你在《" + title + "》下的评论「" + excerpt(comment.getContent()) + "」已展示", link);
    }

    /** 批量审核评论，返回实际处理条数（已是目标状态的跳过）。 */
    @Transactional
    public int batchModerateComments(List<Long> ids, boolean approve) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Long id : ids) {
            Comment comment = commentMapper.selectById(id);
            if (comment == null) {
                continue;
            }
            int before = comment.getStatus() == null ? PENDING : comment.getStatus();
            int after = approve ? APPROVED : REJECTED;
            if (before == after) {
                continue;
            }
            comment.setStatus(after);
            comment.setUpdateTime(LocalDateTime.now());
            commentMapper.updateById(comment);
            if (after == APPROVED) {
                articleMapper.updateCommentCount(comment.getArticleId(), 1);
                notifyApprovedComment(comment);
            } else if (before == APPROVED) {
                articleMapper.updateCommentCount(comment.getArticleId(), -1);
            }
            changed++;
        }
        return changed;
    }

    /**
     * 删除评论：连同其下所有回复一起逻辑删除。
     *
     * <p>只减少「已通过」的计数，避免把待审核评论也算进文章评论数。</p>
     */
    @Transactional
    public int deleteComment(Long id) {
        Comment root = commentMapper.selectById(id);
        if (root == null) {
            throw BusinessException.notFound("评论不存在");
        }

        List<Long> ids = new ArrayList<>();
        ids.add(id);
        List<Long> frontier = List.of(id);
        while (!frontier.isEmpty() && ids.size() < 5000) {
            List<Comment> children = commentMapper.selectList(Wrappers.<Comment>lambdaQuery()
                    .in(Comment::getParentId, frontier));
            if (children.isEmpty()) {
                break;
            }
            frontier = children.stream().map(Comment::getId).toList();
            ids.addAll(frontier);
        }

        Long approved = commentMapper.selectCount(Wrappers.<Comment>lambdaQuery()
                .in(Comment::getId, ids)
                .eq(Comment::getStatus, APPROVED));
        for (Long commentId : ids) {
            commentMapper.deleteById(commentId);
        }
        if (approved != null && approved > 0) {
            articleMapper.updateCommentCount(root.getArticleId(), -approved.intValue());
        }
        return ids.size();
    }

    /** 批量删除评论，返回实际删除的评论+回复总数。 */
    @Transactional
    public int batchDeleteComments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            try {
                deleted += deleteComment(id);
            } catch (BusinessException ignored) {
                // 已被删除的跳过
            }
        }
        return deleted;
    }

    /** 删除单条留言。 */
    @Transactional
    public void deleteGuestbook(Long id) {
        Guestbook guestbook = guestbookMapper.selectById(id);
        if (guestbook == null) {
            throw BusinessException.notFound("留言不存在");
        }
        guestbookMapper.deleteById(id);
    }

    /** 批量删除留言。 */
    @Transactional
    public int batchDeleteGuestbook(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int deleted = 0;
        for (Long id : ids.stream().filter(Objects::nonNull).distinct().toList()) {
            Guestbook guestbook = guestbookMapper.selectById(id);
            if (guestbook == null) {
                continue;
            }
            guestbookMapper.deleteById(id);
            deleted++;
        }
        return deleted;
    }

    /** 后台留言列表：支持状态、用户、关键字（内容/昵称/邮箱）筛选。 */
    public PageResult<AdminGuestbookVO> adminGuestbook(Integer status, String keyword,
                                                       Long userId, int pageNo, int size) {
        var wrapper = Wrappers.<Guestbook>lambdaQuery()
                .eq(status != null, Guestbook::getStatus, status)
                .eq(userId != null, Guestbook::getUserId, userId);
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Guestbook::getContent, kw)
                    .or().like(Guestbook::getNickname, kw)
                    .or().like(Guestbook::getEmail, kw));
        }
        Page<Guestbook> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 100));
        guestbookMapper.selectPage(page, wrapper.orderByDesc(Guestbook::getCreateTime));
        return PageResult.of(page, g -> new AdminGuestbookVO(g.getId(), g.getNickname(), g.getEmail(),
                g.getContent(), g.getReply(), g.getStatus(), g.getCreateTime()));
    }

    public void moderateGuestbook(Long id, boolean approve) {
        Guestbook guestbook = guestbookMapper.selectById(id);
        if (guestbook == null) {
            throw BusinessException.notFound("留言不存在");
        }
        guestbook.setStatus(approve ? APPROVED : REJECTED);
        guestbook.setUpdateTime(LocalDateTime.now());
        guestbookMapper.updateById(guestbook);
    }

    /** 批量审核留言。 */
    @Transactional
    public int batchModerateGuestbook(List<Long> ids, boolean approve) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int changed = 0;
        for (Long id : ids) {
            Guestbook guestbook = guestbookMapper.selectById(id);
            if (guestbook == null) {
                continue;
            }
            int after = approve ? APPROVED : REJECTED;
            if (guestbook.getStatus() != null && guestbook.getStatus() == after) {
                continue;
            }
            guestbook.setStatus(after);
            guestbook.setUpdateTime(LocalDateTime.now());
            guestbookMapper.updateById(guestbook);
            changed++;
        }
        return changed;
    }

    /**
     * 博主回复留言：回复即视为审核通过（用户看到回复才有意义）。
     */
    public void replyGuestbook(Long id, String reply) {
        Guestbook guestbook = guestbookMapper.selectById(id);
        if (guestbook == null) {
            throw BusinessException.notFound("留言不存在");
        }
        if (reply == null || reply.isBlank()) {
            throw BusinessException.badRequest("回复内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        guestbook.setReply(reply.trim());
        guestbook.setReplyTime(now);
        guestbook.setStatus(APPROVED);
        guestbook.setUpdateTime(now);
        guestbookMapper.updateById(guestbook);

        notify(guestbook.getUserId(), "SYSTEM", "留言收到回复",
                "博主回复了你的留言：" + reply.trim(), "/guestbook");
    }

    // ------------------------------------------------------------------ 工具

    private Article requirePublicArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || article.getStatus() == null || article.getStatus() != 1
                || article.getVisibility() == null || article.getVisibility() != 0) {
            throw BusinessException.notFound("文章不存在或未发布");
        }
        return article;
    }

    private Article requirePublicArticleBySlug(String slug) {
        Article article = articleMapper.selectOne(Wrappers.<Article>lambdaQuery()
                .eq(Article::getSlug, slug)
                .eq(Article::getStatus, 1)
                .eq(Article::getVisibility, 0)
                .last("limit 1"));
        if (article == null) {
            throw BusinessException.notFound("文章不存在或未发布");
        }
        return article;
    }

    private String titleOf(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        return article == null ? "文章" : article.getTitle();
    }

    private Map<Long, Article> batchArticles(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity()));
    }

    private Map<Long, User> batchUsers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private ArticleSummaryVO toSummary(Article a, List<String> tags) {
        Category category = a.getCategoryId() == null ? null : categoryMapper.selectById(a.getCategoryId());
        return new ArticleSummaryVO(
                a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getCover(),
                a.getCategoryId(), category == null ? null : category.getName(), tags,
                a.getIsTop() != null && a.getIsTop() == 1,
                a.getIsRecommend() != null && a.getIsRecommend() == 1,
                a.getViewCount() == null ? 0 : a.getViewCount(),
                a.getLikeCount() == null ? 0 : a.getLikeCount(),
                a.getCommentCount() == null ? 0 : a.getCommentCount(),
                Math.max(1, (a.getContent() == null ? 0 : a.getContent().length()) / 400),
                a.getPublishTime());
    }
}
