package com.ccapp.media.controller;

import com.ccapp.common.exception.NotFoundException;
import com.ccapp.media.storage.LocalFileStorageService;
import com.ccapp.media.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dev-media")
public class DevMediaController {

    private final ObjectStorageService objectStorageService;
    private final LocalFileStorageService localFileStorageService;

    @PutMapping(value = "/{uploadId}", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> uploadDevMedia(@PathVariable String uploadId,
                                               @RequestBody byte[] data,
                                               @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {
        activeLocalFileStorageService().putPublicObject(uploadId, data, contentType);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{uploadId}")
    public ResponseEntity<Resource> getDevMedia(@PathVariable String uploadId) {
        LocalFileStorageService.StoredLocalMediaFile storedFile = activeLocalFileStorageService().loadPublicObject(uploadId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.contentType()))
                .contentLength(storedFile.contentLength())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new FileSystemResource(storedFile.path()));
    }

    private LocalFileStorageService activeLocalFileStorageService() {
        if (objectStorageService instanceof LocalFileStorageService) {
            return localFileStorageService;
        }
        throw new NotFoundException("Resource not found");
    }
}
