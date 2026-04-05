package com.ccapp.media.controller;

import com.ccapp.media.storage.LocalDevStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@ConditionalOnBean(LocalDevStorageService.class)
public class DevMediaController {

    private final LocalDevStorageService localDevStorageService;

    @PutMapping("/dev-media/{fileId}")
    public ResponseEntity<Void> uploadDevMedia(@PathVariable String fileId,
                                               @RequestBody byte[] data,
                                               @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {
        localDevStorageService.putPublicObject(fileId, data, contentType);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/dev-media/{fileId}")
    public ResponseEntity<Resource> getDevMedia(@PathVariable String fileId) {
        LocalDevStorageService.StoredLocalMediaFile storedFile = localDevStorageService.loadPublicObject(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(storedFile.contentType()))
                .contentLength(storedFile.contentLength())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(new FileSystemResource(storedFile.path()));
    }
}
