package com.ccapp.media.repository;

import com.ccapp.media.domain.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Optional<MediaFile> findByIdAndAccountHolderId(UUID id, UUID accountHolderId);
}
