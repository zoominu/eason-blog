package com.eason.blog.module.resource.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.response.PageResult;
import com.eason.blog.config.AppProperties;
import com.eason.blog.module.article.entity.Article;
import com.eason.blog.module.article.mapper.ArticleMapper;
import com.eason.blog.module.resource.entity.Resource;
import com.eason.blog.module.resource.mapper.ResourceMapper;
import com.eason.blog.module.resource.vo.ResourceRefVO;
import com.eason.blog.module.resource.vo.ResourceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 资源上传与管理。
 *
 * <p>安全边界（设计文档 9.2）：
 * <ul>
 *   <li>限制 MIME 与扩展名白名单，拒绝可执行脚本；</li>
 *   <li>存储名随机生成，不使用用户原始文件名，防路径穿越；</li>
 *   <li>大小上限 20MB（与 Nginx client_max_body_size 对齐）；</li>
 *   <li>上传目录不参与执行，Nginx 只作为静态资源返回。</li>
 * </ul>
 * 资源按 {@code type} 分类：avatar / article_image / music / video / document / other，
 * 列表可按类型过滤，并统计每张资源被哪些文章引用。</p>
 */
@Service
public class ResourceService {

    public static final String TYPE_AVATAR = "avatar";
    public static final String TYPE_ARTICLE_IMAGE = "article_image";
    public static final String TYPE_MUSIC = "music";
    public static final String TYPE_VIDEO = "video";
    public static final String TYPE_DOCUMENT = "document";
    public static final String TYPE_OTHER = "other";
    public static final Set<String> TYPES = Set.of(
            TYPE_AVATAR, TYPE_ARTICLE_IMAGE, TYPE_MUSIC, TYPE_VIDEO, TYPE_DOCUMENT, TYPE_OTHER);

    private static final long MAX_SIZE = 200L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "svg",
            "mp3", "wav", "ogg", "m4a", "flac",
            "mp4", "webm", "mov",
            "pdf", "txt", "md", "zip");
    private static final Set<String> ALLOWED_MIME_PREFIX = Set.of(
            "image/", "audio/", "video/", "text/", "application/pdf", "application/zip",
            "application/octet-stream");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final ResourceMapper resourceMapper;
    private final ArticleMapper articleMapper;
    private final AppProperties properties;

    public ResourceService(ResourceMapper resourceMapper, ArticleMapper articleMapper, AppProperties properties) {
        this.resourceMapper = resourceMapper;
        this.articleMapper = articleMapper;
        this.properties = properties;
    }

    @Transactional
    public ResourceVO upload(MultipartFile file, Long uploaderId) {
        return upload(file, uploaderId, null);
    }

    @Transactional
    public ResourceVO upload(MultipartFile file, Long uploaderId, String type) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BusinessException.badRequest("文件不能超过 200MB");
        }

        String original = StringUtils.hasText(file.getOriginalFilename())
                ? Paths.get(file.getOriginalFilename().replace('\\', '/')).getFileName().toString()
                : "unnamed";
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BusinessException.badRequest("不支持的文件类型：" + ext);
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (ALLOWED_MIME_PREFIX.stream().noneMatch(mime::startsWith)) {
            throw BusinessException.badRequest("不支持的文件 MIME 类型");
        }

        String month = LocalDate.now().format(MONTH);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(properties.storage().uploadDir(), month).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName);
        if (!target.startsWith(dir)) {
            throw BusinessException.badRequest("非法的文件路径");
        }

        // 大文件（视频）直接流式落盘，避免 getBytes() 把整个文件读进 256MB 堆
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(com.eason.blog.common.exception.ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        Resource resource = new Resource();
        resource.setUploaderId(uploaderId);
        resource.setOriginalName(original);
        resource.setStoredName(storedName);
        resource.setPath(month + "/" + storedName);
        resource.setUrl("/uploads/" + month + "/" + storedName);
        resource.setMimeType(mime);
        resource.setType(detectType(mime, ext, type));
        resource.setSize(file.getSize());
        resource.setSha256(sha256(target));
        resource.setCreateTime(LocalDateTime.now());
        resource.setIsDeleted(0);
        resourceMapper.insert(resource);

        return toVO(resource);
    }

    /**
     * 直接保存字节内容并登记到资源表。
     *
     * <p>Markdown 导入、头像上传统一走这里，保证资源在「资源管理」里可见、可复用。</p>
     */
    @Transactional
    public ResourceVO store(byte[] bytes, String originalName, String mime, Long uploaderId) {
        return store(bytes, originalName, mime, uploaderId, null);
    }

    @Transactional
    public ResourceVO store(byte[] bytes, String originalName, String mime, Long uploaderId, String type) {
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("文件为空");
        }
        if (bytes.length > MAX_SIZE) {
            throw BusinessException.badRequest("文件不能超过 20MB");
        }
        String original = StringUtils.hasText(originalName)
                ? Paths.get(originalName.replace('\\', '/')).getFileName().toString()
                : "unnamed";
        String ext = extensionOf(original);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BusinessException.badRequest("不支持的文件类型：" + ext);
        }
        String normalizedMime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (ALLOWED_MIME_PREFIX.stream().noneMatch(normalizedMime::startsWith)) {
            throw BusinessException.badRequest("不支持的文件 MIME 类型");
        }

        String month = LocalDate.now().format(MONTH);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(properties.storage().uploadDir(), month).toAbsolutePath().normalize();
        Path target = dir.resolve(storedName);
        if (!target.startsWith(dir)) {
            throw BusinessException.badRequest("非法的文件路径");
        }

        try {
            Files.createDirectories(dir);
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new BusinessException(com.eason.blog.common.exception.ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }

        Resource resource = new Resource();
        resource.setUploaderId(uploaderId);
        resource.setOriginalName(original);
        resource.setStoredName(storedName);
        resource.setPath(month + "/" + storedName);
        resource.setUrl("/uploads/" + month + "/" + storedName);
        resource.setMimeType(normalizedMime);
        resource.setType(detectType(normalizedMime, ext, type));
        resource.setSize((long) bytes.length);
        resource.setSha256(sha256(target));
        resource.setCreateTime(LocalDateTime.now());
        resource.setIsDeleted(0);
        resourceMapper.insert(resource);

        return toVO(resource);
    }

    public PageResult<ResourceVO> page(String keyword, int pageNo, int size) {
        return page(keyword, null, pageNo, size);
    }

    public PageResult<ResourceVO> page(String keyword, String type, int pageNo, int size) {
        var wrapper = Wrappers.<Resource>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Resource::getOriginalName, keyword.trim());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Resource::getType, type.trim());
        }
        wrapper.orderByDesc(Resource::getCreateTime);
        Page<Resource> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 100));
        resourceMapper.selectPage(page, wrapper);
        return PageResult.of(page, this::toVO);
    }

    /** 资源被哪些文章引用（按正文是否包含该资源 URL 判断）。 */
    public List<ResourceRefVO> references(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery()
                        .like(Article::getContent, resource.getUrl())
                        .orderByDesc(Article::getUpdateTime))
                .stream()
                .map(a -> new ResourceRefVO(a.getId(), a.getTitle(), a.getSlug(), a.getStatus()))
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) {
            throw BusinessException.notFound("资源不存在");
        }
        // 被文章引用时禁止删除，避免前台图片 404
        Long used = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                .like(Article::getContent, resource.getUrl()));
        if (used != null && used > 0) {
            throw BusinessException.conflict("该资源被 " + used + " 篇文章引用，请先移除引用");
        }
        resourceMapper.deleteById(id);
        try {
            Path path = Paths.get(properties.storage().uploadDir(), resource.getPath())
                    .toAbsolutePath().normalize();
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 文件已不存在不影响数据库删除
        }
    }

    private String detectType(String mime, String ext, String explicit) {
        if (StringUtils.hasText(explicit) && TYPES.contains(explicit)) {
            return explicit;
        }
        if (mime.startsWith("image/")) {
            return TYPE_ARTICLE_IMAGE;
        }
        if (mime.startsWith("audio/")) {
            return TYPE_MUSIC;
        }
        if (mime.startsWith("video/")) {
            return TYPE_VIDEO;
        }
        if (mime.startsWith("text/") || mime.startsWith("application/pdf") || "zip".equals(ext)) {
            return TYPE_DOCUMENT;
        }
        return switch (ext) {
            case "mp3", "wav", "ogg", "m4a", "flac" -> TYPE_MUSIC;
            case "mp4", "webm", "mov" -> TYPE_VIDEO;
            case "pdf", "txt", "md", "zip" -> TYPE_DOCUMENT;
            case "png", "jpg", "jpeg", "gif", "webp", "svg" -> TYPE_ARTICLE_IMAGE;
            default -> TYPE_OTHER;
        };
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return null;
        }
    }

    private ResourceVO toVO(Resource r) {
        long count = 0;
        if (StringUtils.hasText(r.getUrl())) {
            Long used = articleMapper.selectCount(Wrappers.<Article>lambdaQuery()
                    .like(Article::getContent, r.getUrl()));
            count = used == null ? 0 : used;
        }
        return new ResourceVO(r.getId(), r.getOriginalName(), r.getUrl(), r.getMimeType(),
                r.getType() == null ? TYPE_OTHER : r.getType(),
                r.getSize(), r.getSha256(), count, r.getCreateTime());
    }

    /** 供上传目录静态映射使用。 */
    public List<String> allowedExtensions() {
        return ALLOWED_EXT.stream().sorted().toList();
    }
}
