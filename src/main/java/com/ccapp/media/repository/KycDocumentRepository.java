package com.ccapp.media.repository;

import com.ccapp.media.domain.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    Optional<KycDocument> findByAccountHolderIdAndMediaFileId(UUID accountHolderId, UUID mediaFileId);
}
