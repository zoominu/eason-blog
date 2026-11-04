package com.eason.blog.module.article.service;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导入 Markdown：解析正文、上传随文图片并把引用改写成 /uploads 地址。
 *
 * <p>图片有两种来源：
 * <ul>
 *   <li>正文里的 base64 内嵌图片（data:image/...;base64,...）；</li>
 *   <li>随 md 一起选中的本地图片文件，按文件名匹配 md 里的相对路径。</li>
 * </ul>
 * 返回的是改写后的 Markdown，管理员确认后再保存文章。</p>
 */
@Service
public class MarkdownImportService {

    private static final long MAX_MD_SIZE = 2L * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMAGES = 30;
    private static final Set<String> MD_EXT = Set.of("md", "markdown");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("(<img[^>]+src=[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|gif|webp|svg\\+xml);base64,(.+)$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final AppProperties properties;

    public MarkdownImportService(AppProperties properties) {
        this.properties = properties;
    }

    public record ImportedImage(String name, String url) {
    }

    public record ImportResult(String title, String slug, String summary, String cover,
                               String content, List<ImportedImage> images, List<String> warnings) {
    }

    public ImportResult importMarkdown(MultipartFile file, List<MultipartFile> images) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择 Markdown 文件");
        }
        if (file.getSize() > MAX_MD_SIZE) {
            throw BusinessException.badRequest("Markdown 文件不能超过 2MB");
        }
        String originalName = file.getOriginalFilename() == null ? "article.md" : file.getOriginalFilename();
        String ext = extension(originalName);
        if (!MD_EXT.contains(ext)) {
            throw BusinessException.badRequest("只支持 .md / .markdown 文件");
        }

        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取 Markdown 失败");
        }
        text = stripBom(text);

        FrontMatter front = parseFrontMatter(text);
        String body = front.body;

        Map<String, String> uploadedByBaseName = new LinkedHashMap<>();
        List<ImportedImage> imported = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (images != null && !images.isEmpty()) {
            if (images.size() > MAX_IMAGES) {
                throw BusinessException.badRequest("一次最多上传 " + MAX_IMAGES + " 张图片");
            }
            for (MultipartFile image : images) {
                if (image == null || image.isEmpty()) {
                    continue;
                }
                ImportedImage saved;
                try {
                    saved = saveImage(image, image.getBytes(), extension(nameOf(image)));
                } catch (IOException e) {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败：" + nameOf(image));
                }
                imported.add(saved);
                uploadedByBaseName.put(baseName(saved.name()).toLowerCase(Locale.ROOT), saved.url());
            }
        }

        // 1) base64 内嵌图片
        body = replaceDataImages(body, imported);

        // 2) 相对路径图片：按文件名匹配本次上传的图片
        body = replaceRelativeImages(body, uploadedByBaseName, warnings);

        String title = front.values.getOrDefault("title", "");
        if (!StringUtils.hasText(title)) {
            String[] lines = body.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("# ")) {
                    title = line.substring(2).trim();
                    lines[i] = "";
                    body = String.join("\n", lines);
                    break;
                }
            }
        }
        if (!StringUtils.hasText(title)) {
            title = baseName(originalName).replaceAll("\\.(md|markdown)$", "");
        }

        String cover = front.values.getOrDefault("cover", "");
        if (!StringUtils.hasText(cover) && !imported.isEmpty()) {
            cover = imported.get(0).url();
        }

        return new ImportResult(title.trim(),
                front.values.getOrDefault("slug", ""),
                front.values.getOrDefault("summary", ""),
                cover,
                body.strip(),
                imported,
                warnings);
    }

    // ------------------------------------------------------------ 图片处理

    private String replaceDataImages(String body, List<ImportedImage> imported) {
        String result = replaceDataInMarkdown(body, imported);
        return replaceDataInHtml(result, imported);
    }

    private String replaceDataInMarkdown(String body, List<ImportedImage> imported) {
        Matcher matcher = MD_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String alt = matcher.group(1);
            String target = matcher.group(2);
            String replacement = matcher.group(0);
            Matcher data = DATA_IMAGE.matcher(target);
            if (data.matches()) {
                ImportedImage saved = saveBase64(data.group(1), data.group(2), imported);
                if (saved != null) {
                    replacement = "![" + alt + "](" + saved.url() + ")";
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replaceDataInHtml(String body, List<ImportedImage> imported) {
        Matcher matcher = HTML_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2);
            String replacement = matcher.group(0);
            Matcher data = DATA_IMAGE.matcher(target);
            if (data.matches()) {
                ImportedImage saved = saveBase64(data.group(1), data.group(2), imported);
                if (saved != null) {
                    replacement = matcher.group(1) + saved.url() + matcher.group(3);
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private ImportedImage saveBase64(String subtype, String encoded, List<ImportedImage> imported) {
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(encoded.replaceAll("\\s", ""));
            String ext = "svg+xml".equalsIgnoreCase(subtype) ? "svg" : subtype.toLowerCase(Locale.ROOT);
            if ("jpeg".equals(ext)) {
                ext = "jpg";
            }
            ImportedImage saved = saveImage(null, bytes, ext);
            imported.add(saved);
            return saved;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String replaceRelativeImages(String body, Map<String, String> uploaded, List<String> warnings) {
        String result = replaceRelativeInMarkdown(body, uploaded, warnings);
        return replaceRelativeInHtml(result, uploaded, warnings);
    }

    private String replaceRelativeInMarkdown(String body, Map<String, String> uploaded, List<String> warnings) {
        Matcher matcher = MD_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2);
            String replacement = matcher.group(0);
            String url = uploaded.get(baseName(target).toLowerCase(Locale.ROOT));
            if (url != null) {
                replacement = "![" + matcher.group(1) + "](" + url + ")";
            } else if (isLocalReference(target)) {
                warnings.add("未找到图片文件：" + target);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String replaceRelativeInHtml(String body, Map<String, String> uploaded, List<String> warnings) {
        Matcher matcher = HTML_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2);
            String replacement = matcher.group(0);
            String url = uploaded.get(baseName(target).toLowerCase(Locale.ROOT));
            if (url != null) {
                replacement = matcher.group(1) + url + matcher.group(3);
            } else if (isLocalReference(target)) {
                warnings.add("未找到图片文件：" + target);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private boolean isLocalReference(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        return !lower.startsWith("http://") && !lower.startsWith("https://")
                && !lower.startsWith("//") && !lower.startsWith("data:")
                && !lower.startsWith("/uploads/");
    }

    private ImportedImage saveImage(MultipartFile file, byte[] bytes, String ext) {
        if (bytes.length > MAX_IMAGE_SIZE) {
            throw BusinessException.badRequest("单张图片不能超过 5MB");
        }
        String normalized = ext.toLowerCase(Locale.ROOT);
        if (!IMAGE_EXT.contains(normalized)) {
            throw BusinessException.badRequest("不支持的图片类型：" + ext);
        }
        if (file != null) {
            String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
            if (!mime.startsWith("image/")) {
                throw BusinessException.badRequest("文件不是图片：" + nameOf(file));
            }
        }

        String month = LocalDate.now().format(MONTH);
        String stored = UUID.randomUUID().toString().replace("-", "") + "." + normalized;
        Path dir = Paths.get(properties.storage().uploadDir())
                .toAbsolutePath().normalize()
                .resolve("markdown")
                .resolve(month);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(stored), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "图片保存失败");
        }
        String url = "/uploads/markdown/" + month + "/" + stored;
        return new ImportedImage(file == null ? stored : nameOf(file), url);
    }

    // ------------------------------------------------------------ 文本解析

    private record FrontMatter(Map<String, String> values, String body) {
    }

    private FrontMatter parseFrontMatter(String text) {
        Map<String, String> values = new HashMap<>();
        if (!text.startsWith("---")) {
            return new FrontMatter(values, text);
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return new FrontMatter(values, text);
        }
        String header = text.substring(3, end);
        for (String line : header.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        String body = text.substring(end + 4);
        return new FrontMatter(values, body);
    }

    private String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String nameOf(MultipartFile file) {
        String name = file.getOriginalFilename();
        return StringUtils.hasText(name) ? Paths.get(name).getFileName().toString() : "image";
    }

    private String baseName(String path) {
        String clean = path;
        int cut = clean.indexOf('?');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        cut = clean.indexOf('#');
        if (cut >= 0) {
            clean = clean.substring(0, cut);
        }
        clean = clean.replace('\\', '/');
        int slash = clean.lastIndexOf('/');
        return slash >= 0 ? clean.substring(slash + 1) : clean;
    }
}
