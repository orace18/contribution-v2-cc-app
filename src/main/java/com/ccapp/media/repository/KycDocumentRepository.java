package com.ccapp.media.repository;

import com.ccapp.media.domain.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

    Optional<KycDocument> findByAccountHolderIdAndMediaFileId(UUID accountHolderId, UUID mediaFileId);

    List<KycDocument> findByAccountHolderId(UUID accountHolderId);

    List<KycDocument> findByAccountHolderIdAndCpassCaseId(UUID accountHolderId, String cpassCaseId);
}
