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
import java.nio.file.StandardCopyOption;
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
 *   <li>大小上限 5MB，超过直接拒绝；</li>
 *   <li>上传目录不参与执行，Nginx 只作为静态资源返回。</li>
 * </ul></p>
 */
@Service
public class ResourceService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "svg", "pdf", "txt", "md", "zip");
    private static final Set<String> ALLOWED_MIME_PREFIX = Set.of("image/", "text/", "application/pdf");
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
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BusinessException.badRequest("文件不能超过 5MB");
        }

        String original = StringUtils.hasText(file.getOriginalFilename())
                ? Paths.get(file.getOriginalFilename()).getFileName().toString()
                : "unnamed";
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        try {
            return store(file.getBytes(), original, mime, uploaderId);
        } catch (IOException e) {
            throw new BusinessException(com.eason.blog.common.exception.ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }
    }

    /**
     * 直接保存字节内容并登记到资源表。
     *
     * <p>Markdown 导入（base64、绝对路径、远程图片）统一走这里，
     * 保证导入的图片在「资源管理」里可见、可复用。</p>
     */
    @Transactional
    public ResourceVO store(byte[] bytes, String originalName, String mime, Long uploaderId) {
        if (bytes == null || bytes.length == 0) {
            throw BusinessException.badRequest("文件为空");
        }
        if (bytes.length > MAX_SIZE) {
            throw BusinessException.badRequest("文件不能超过 5MB");
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
        resource.setSize((long) bytes.length);
        resource.setSha256(sha256(target));
        resource.setCreateTime(LocalDateTime.now());
        resource.setIsDeleted(0);
        resourceMapper.insert(resource);

        return toVO(resource);
    }

    public PageResult<ResourceVO> page(String keyword, int pageNo, int size) {
        var wrapper = Wrappers.<Resource>lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Resource::getOriginalName, keyword.trim());
        }
        wrapper.orderByDesc(Resource::getCreateTime);
        Page<Resource> page = new Page<>(Math.max(pageNo, 1), Math.min(Math.max(size, 1), 100));
        resourceMapper.selectPage(page, wrapper);
        return PageResult.of(page, this::toVO);
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
        return new ResourceVO(r.getId(), r.getOriginalName(), r.getUrl(), r.getMimeType(),
                r.getSize(), r.getSha256(), r.getCreateTime());
    }

    /** 供上传目录静态映射使用。 */
    public List<String> allowedExtensions() {
        return ALLOWED_EXT.stream().sorted().toList();
    }
}
