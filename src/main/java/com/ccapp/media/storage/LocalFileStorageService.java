package com.ccapp.media.storage;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Slf4j
public class LocalFileStorageService implements ObjectStorageService, AutoCloseable {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final Path rootPath;
    private final String configuredPublicBaseUrl;
    private final String fallbackPublicBaseUrl;

    public LocalFileStorageService(String rootPath,
                                   String configuredPublicBaseUrl,
                                   String fallbackPublicBaseUrl) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
        this.configuredPublicBaseUrl = normalizeBaseUrl(configuredPublicBaseUrl);
        this.fallbackPublicBaseUrl = normalizeBaseUrl(fallbackPublicBaseUrl);
        ensureRootDirectory();
    }

    @Override
    public PresignedUrl createUploadUrl(String bucket, String objectKey, String contentType, Duration ttl) {
        String fileId = resolveFileId(bucket, objectKey);
        writeMetadata(fileId, bucket, objectKey, contentType);
        return new PresignedUrl(buildPublicUrl(fileId), expiresAt(ttl));
    }

    @Override
    public PresignedUrl createDownloadUrl(String bucket, String objectKey, Duration ttl) {
        String fileId = resolveFileId(bucket, objectKey);
        return new PresignedUrl(buildPublicUrl(fileId), expiresAt(ttl));
    }

    @Override
    public String publicObjectUrl(String bucket, String objectKey) {
        return buildPublicUrl(resolveFileId(bucket, objectKey));
    }

    @Override
    public boolean objectExists(String bucket, String objectKey) {
        return Files.exists(dataPath(resolveFileId(bucket, objectKey)));
    }

    @Override
    public void putObject(String bucket, String objectKey, byte[] data, String contentType) {
        String fileId = resolveFileId(bucket, objectKey);
        writeMetadata(fileId, bucket, objectKey, contentType);
        writeFile(fileId, data, contentType);
    }

    @Override
    public byte[] readObject(String bucket, String objectKey) {
        String fileId = resolveFileId(bucket, objectKey);
        Path dataPath = dataPath(fileId);
        if (!Files.exists(dataPath)) {
            throw new NotFoundException("Local fallback media file not found");
        }
        try {
            return Files.readAllBytes(dataPath);
        } catch (IOException ex) {
            throw new BusinessException("Unable to read local fallback media file");
        }
    }

    public void putPublicObject(String fileId, byte[] data, String contentType) {
        String normalizedFileId = normalizeFileId(fileId);
        writeFile(normalizedFileId, data, contentType);
    }

    public StoredLocalMediaFile loadPublicObject(String fileId) {
        String normalizedFileId = normalizeFileId(fileId);
        Path dataPath = dataPath(normalizedFileId);
        if (!Files.exists(dataPath)) {
            throw new NotFoundException("Local fallback media file not found");
        }

        Properties metadata = readMetadata(normalizedFileId);
        String contentType = metadata.getProperty("contentType", DEFAULT_CONTENT_TYPE);
        long size;
        try {
            size = Files.size(dataPath);
        } catch (IOException ex) {
            throw new BusinessException("Unable to read local fallback media file");
        }
        return new StoredLocalMediaFile(dataPath, contentType, size);
    }

    private void writeFile(String fileId, byte[] data, String contentType) {
        ensureRootDirectory();
        Path targetPath = dataPath(fileId);
        try {
            Files.write(
                    targetPath,
                    data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            Properties metadata = readMetadata(fileId);
            metadata.setProperty("contentType", defaultContentType(contentType));
            writeMetadataFile(fileId, metadata);
            log.info(
                    "Stored local fallback media file fileId={} path={} bytes={} contentType={}",
                    fileId,
                    targetPath,
                    data.length,
                    defaultContentType(contentType)
            );
        } catch (IOException ex) {
            log.error("Unable to store local fallback media file fileId={} path={}", fileId, targetPath, ex);
            throw new BusinessException("Unable to store media file");
        }
    }

    private void writeMetadata(String fileId, String bucket, String objectKey, String contentType) {
        Properties metadata = readMetadata(fileId);
        metadata.setProperty("bucket", bucket);
        metadata.setProperty("objectKey", objectKey);
        metadata.setProperty("contentType", defaultContentType(contentType));
        writeMetadataFile(fileId, metadata);
    }

    private Properties readMetadata(String fileId) {
        Properties metadata = new Properties();
        Path metadataPath = metadataPath(fileId);
        if (!Files.exists(metadataPath)) {
            return metadata;
        }
        try (InputStream inputStream = Files.newInputStream(metadataPath, StandardOpenOption.READ)) {
            metadata.load(inputStream);
        } catch (IOException ex) {
            throw new BusinessException("Unable to read local fallback media metadata");
        }
        return metadata;
    }

    private void writeMetadataFile(String fileId, Properties metadata) {
        ensureRootDirectory();
        try (OutputStream outputStream = Files.newOutputStream(
                metadataPath(fileId),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        )) {
            metadata.store(outputStream, "ccapp local fallback media");
        } catch (IOException ex) {
            throw new BusinessException("Unable to write local fallback media metadata");
        }
    }

    private String buildPublicUrl(String fileId) {
        String baseUrl = resolvePublicBaseUrl();
        return baseUrl + "/dev-media/" + fileId;
    }

    private String resolvePublicBaseUrl() {
        if (!MediaStorageConfig.isBlank(configuredPublicBaseUrl)) {
            return configuredPublicBaseUrl;
        }
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return fallbackPublicBaseUrl;
        }
        return buildRequestBaseUrl(request);
    }

    private OffsetDateTime expiresAt(Duration ttl) {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(ttl);
    }

    private String resolveFileId(String bucket, String objectKey) {
        return extractUuidFromObjectKey(objectKey)
                .orElseGet(() -> UUID.nameUUIDFromBytes((bucket + ":" + objectKey).getBytes(StandardCharsets.UTF_8)).toString());
    }

    private Optional<String> extractUuidFromObjectKey(String objectKey) {
        if (MediaStorageConfig.isBlank(objectKey)) {
            return Optional.empty();
        }
        String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        int separatorIndex = fileName.indexOf('-');
        if (separatorIndex <= 0) {
            return Optional.empty();
        }
        String candidate = fileName.substring(0, separatorIndex);
        try {
            return Optional.of(UUID.fromString(candidate).toString());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String normalizeFileId(String fileId) {
        try {
            return UUID.fromString(fileId).toString();
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Local fallback media file not found");
        }
    }

    private String defaultContentType(String contentType) {
        return MediaStorageConfig.isBlank(contentType) ? DEFAULT_CONTENT_TYPE : contentType.trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (MediaStorageConfig.isBlank(baseUrl)) {
            return "";
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }

    private HttpServletRequest currentRequest() {
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private String buildRequestBaseUrl(HttpServletRequest request) {
        String scheme = resolveScheme(request);
        String authority = resolveAuthority(request, scheme);
        return scheme + "://" + authority + MediaStorageConfig.normalizeContextPath(request.getContextPath());
    }

    private String resolveScheme(HttpServletRequest request) {
        String forwardedProto = firstHeaderValue(request, "X-Forwarded-Proto");
        if (!MediaStorageConfig.isBlank(forwardedProto)) {
            return forwardedProto.toLowerCase(Locale.ROOT);
        }
        return "https";
    }

    private String resolveAuthority(HttpServletRequest request, String scheme) {
        String forwardedHost = firstHeaderValue(request, "X-Forwarded-Host");
        if (!MediaStorageConfig.isBlank(forwardedHost)) {
            return normalizeAuthority(forwardedHost, scheme);
        }

        String hostHeader = firstHeaderValue(request, "Host");
        if (!MediaStorageConfig.isBlank(hostHeader)) {
            return normalizeAuthority(hostHeader, scheme);
        }

        int port = resolvePort(request, scheme);
        if (isDefaultPort(port, scheme)) {
            return request.getServerName();
        }
        return request.getServerName() + ":" + port;
    }

    private String normalizeAuthority(String authority, String scheme) {
        String trimmedAuthority = authority.trim();
        try {
            java.net.URI parsedAuthority = java.net.URI.create(scheme + "://" + trimmedAuthority);
            String host = parsedAuthority.getHost();
            int port = parsedAuthority.getPort();
            if (host == null) {
                return trimmedAuthority;
            }
            if (port < 0 || isDefaultPort(port, scheme)) {
                return host;
            }
            return host + ":" + port;
        } catch (IllegalArgumentException ex) {
            return trimmedAuthority;
        }
    }

    private int resolvePort(HttpServletRequest request, String scheme) {
        String forwardedPort = firstHeaderValue(request, "X-Forwarded-Port");
        if (!MediaStorageConfig.isBlank(forwardedPort)) {
            try {
                return Integer.parseInt(forwardedPort);
            } catch (NumberFormatException ignored) {
                // Fall back to the servlet request port.
            }
        }
        return request.getServerPort();
    }

    private boolean isDefaultPort(int port, String scheme) {
        if (port <= 0) {
            return true;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return port == 443;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return port == 80;
        }
        return false;
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (MediaStorageConfig.isBlank(value)) {
            return null;
        }
        int commaIndex = value.indexOf(',');
        if (commaIndex >= 0) {
            return value.substring(0, commaIndex).trim();
        }
        return value.trim();
    }

    private void ensureRootDirectory() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to initialize local fallback media directory: " + rootPath, ex);
        }
    }

    private Path dataPath(String fileId) {
        return rootPath.resolve(fileId + ".bin");
    }

    private Path metadataPath(String fileId) {
        return rootPath.resolve(fileId + ".properties");
    }

    public record StoredLocalMediaFile(
            Path path,
            String contentType,
            long contentLength
    ) {
    }

    @Override
    public void close() {
        // no-op
    }
}
