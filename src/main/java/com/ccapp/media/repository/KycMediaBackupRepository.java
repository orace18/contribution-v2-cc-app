package com.ccapp.media.repository;

import com.ccapp.media.domain.entity.KycMediaBackup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycMediaBackupRepository extends JpaRepository<KycMediaBackup, UUID> {

    Optional<KycMediaBackup> findByMediaFileIdAndAccountHolderId(UUID mediaFileId, UUID accountHolderId);
}
