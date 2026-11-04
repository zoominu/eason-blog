package com.eason.blog.module.article.service;

import com.eason.blog.common.exception.BusinessException;
import com.eason.blog.common.exception.ErrorCode;
import com.eason.blog.module.resource.service.ResourceService;
import com.eason.blog.module.resource.vo.ResourceVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 导入 Markdown：解析正文，并把随文图片收进「资源库」后改写引用。
 *
 * <p>支持的图片引用：
 * <ul>
 *   <li>base64 内嵌：{@code data:image/png;base64,...}</li>
 *   <li>http(s) 绝对地址：服务端下载后存资源库（做了内网地址拦截）</li>
 *   <li>本机绝对路径：如果后端能读到该路径（本地开发），直接读取上传</li>
 *   <li>相对路径 / 本地路径但后端读不到：按文件名匹配随 md 一起选中的图片</li>
 * </ul>
 * 所有图片最终都会写入 {@code blog_resource}，在「资源管理」里可查。
 * 返回的是改写后的 Markdown，管理员确认后再保存文章。</p>
 */
@Service
public class MarkdownImportService {

    private static final long MAX_MD_SIZE = 2L * 1024 * 1024;
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMAGES = 30;
    private static final Set<String> MD_EXT = Set.of("md", "markdown");
    private static final Set<String> IMAGE_EXT = Set.of("png", "jpg", "jpeg", "gif", "webp", "svg");

    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(([^)\\s]+)\\)");
    private static final Pattern HTML_IMAGE = Pattern.compile("(<img[^>]+src=[\"'])([^\"']+)([\"'])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_IMAGE = Pattern.compile(
            "^data:image/(png|jpeg|jpg|gif|webp|svg\\+xml);base64,(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");

    private final ResourceService resourceService;

    public MarkdownImportService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    public record ImportedImage(String name, String url, Long resourceId) {
    }

    public record ImportResult(String title, String slug, String summary, String cover,
                               String content, List<ImportedImage> images, List<String> warnings) {
    }

    public ImportResult importMarkdown(MultipartFile file, List<MultipartFile> images, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("请选择 Markdown 文件");
        }
        if (file.getSize() > MAX_MD_SIZE) {
            throw BusinessException.badRequest("Markdown 文件不能超过 2MB");
        }
        String originalName = file.getOriginalFilename() == null ? "article.md" : file.getOriginalFilename();
        if (!MD_EXT.contains(extension(originalName))) {
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
                String name = nameOf(image);
                ImportedImage saved = storeImage(bytesOf(image, name), name,
                        image.getContentType(), uploaderId, warnings);
                if (saved != null) {
                    imported.add(saved);
                    uploadedByBaseName.put(baseName(name).toLowerCase(Locale.ROOT), saved.url());
                }
            }
        }

        body = rewriteMarkdownImages(body, uploadedByBaseName, imported, warnings, uploaderId);
        body = rewriteHtmlImages(body, uploadedByBaseName, imported, warnings, uploaderId);

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

    // ------------------------------------------------------------ 图片改写

    private String rewriteMarkdownImages(String body, Map<String, String> uploaded,
                                         List<ImportedImage> imported, List<String> warnings, Long uploaderId) {
        Matcher matcher = MD_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String alt = matcher.group(1);
            String target = matcher.group(2);
            String url = resolveImage(target, uploaded, imported, warnings, uploaderId);
            String replacement = url == null ? matcher.group(0) : "![" + alt + "](" + url + ")";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String rewriteHtmlImages(String body, Map<String, String> uploaded,
                                     List<ImportedImage> imported, List<String> warnings, Long uploaderId) {
        Matcher matcher = HTML_IMAGE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2);
            String url = resolveImage(target, uploaded, imported, warnings, uploaderId);
            String replacement = url == null ? matcher.group(0) : matcher.group(1) + url + matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 返回可用的图片 URL；解析失败返回 null（保留原文并记一条 warning）。 */
    private String resolveImage(String target, Map<String, String> uploaded,
                                List<ImportedImage> imported, List<String> warnings, Long uploaderId) {
        if (!StringUtils.hasText(target)) {
            return null;
        }
        if (target.startsWith("/uploads/")) {
            return target; // 已经是本地上传地址
        }

        // 1) base64 内嵌
        Matcher data = DATA_IMAGE.matcher(target);
        if (data.matches()) {
            byte[] bytes = decodeBase64(data.group(2));
            if (bytes == null) {
                warnings.add("base64 图片解析失败");
                return null;
            }
            String ext = "svg+xml".equalsIgnoreCase(data.group(1)) ? "svg" : data.group(1).toLowerCase(Locale.ROOT);
            if ("jpeg".equals(ext)) {
                ext = "jpg";
            }
            ImportedImage saved = storeImage(bytes, "inline-" + System.nanoTime() + "." + ext,
                    "image/" + data.group(1).replace("+xml", ""), uploaderId, warnings);
            if (saved != null) {
                imported.add(saved);
                return saved.url();
            }
            return null;
        }

        // 2) 随 md 一起选中的文件（按文件名匹配，兼容绝对路径）
        String base = baseName(target);
        String fromUpload = uploaded.get(base.toLowerCase(Locale.ROOT));
        if (fromUpload != null) {
            return fromUpload;
        }

        // 3) http(s) 绝对地址：下载后存资源库
        if (isHttp(target)) {
            byte[] bytes = download(target, warnings);
            if (bytes == null) {
                return null;
            }
            ImportedImage saved = storeImage(bytes, base, guessMime(base), uploaderId, warnings);
            if (saved != null) {
                imported.add(saved);
                return saved.url();
            }
            return null;
        }

        // 4) 后端能直接读到的绝对路径（本地开发时常见）
        Path local = toLocalPath(target);
        if (local != null) {
            try {
                if (Files.isReadable(local) && Files.isRegularFile(local)) {
                    ImportedImage saved = storeImage(Files.readAllBytes(local), base,
                            guessMime(base), uploaderId, warnings);
                    if (saved != null) {
                        imported.add(saved);
                        return saved.url();
                    }
                    return null;
                }
            } catch (IOException ignored) {
                // 落到下面的提示
            }
        }

        warnings.add("未找到图片：" + target + "（本地图片请与 md 一起选中再导入）");
        return null;
    }

    private ImportedImage storeImage(byte[] bytes, String name, String mime, Long uploaderId,
                                     List<String> warnings) {
        if (bytes == null || bytes.length == 0) {
            warnings.add("空图片：" + name);
            return null;
        }
        if (bytes.length > MAX_IMAGE_SIZE) {
            warnings.add("图片超过 5MB，已跳过：" + name);
            return null;
        }
        String ext = extension(name);
        if (!IMAGE_EXT.contains(ext)) {
            warnings.add("不支持的图片格式：" + name);
            return null;
        }
        String contentType = StringUtils.hasText(mime) ? mime : guessMime(name);
        try {
            ResourceVO resource = resourceService.store(bytes, name, contentType, uploaderId);
            return new ImportedImage(name, resource.url(), resource.id());
        } catch (BusinessException e) {
            warnings.add("图片上传失败（" + e.getMessage() + "）：" + name);
            return null;
        }
    }

    private byte[] download(String url, List<String> warnings) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                warnings.add("无效图片地址：" + url);
                return null;
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                warnings.add("出于安全原因不下载内网地址：" + url);
                return null;
            }
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                warnings.add("图片下载失败（HTTP " + response.statusCode() + "）：" + url);
                return null;
            }
            String type = response.headers().firstValue("content-type").orElse("");
            if (!type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                warnings.add("地址不是图片：" + url);
                return null;
            }
            return response.body();
        } catch (Exception e) {
            warnings.add("图片下载失败：" + url);
            return null;
        }
    }

    // ------------------------------------------------------------ 工具

    private byte[] bytesOf(MultipartFile file, String name) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取图片失败：" + name);
        }
    }

    private byte[] decodeBase64(String encoded) {
        try {
            return Base64.getMimeDecoder().decode(encoded.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isHttp(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private Path toLocalPath(String target) {
        if (!(target.startsWith("/") || WINDOWS_ABSOLUTE.matcher(target).matches())) {
            return null;
        }
        try {
            return Paths.get(target);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private String guessMime(String name) {
        String ext = extension(name);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

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
        return new FrontMatter(values, text.substring(end + 4));
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
        return StringUtils.hasText(name) ? Paths.get(name.replace('\\', '/')).getFileName().toString() : "image";
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
