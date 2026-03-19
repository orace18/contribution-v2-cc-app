package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.CircleChatReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CircleChatReadReceiptRepository extends JpaRepository<CircleChatReadReceipt, UUID> {

    List<CircleChatReadReceipt> findByMessageId(UUID messageId);

    boolean existsByMessageIdAndMemberId(UUID messageId, UUID memberId);

    @Query("SELECT r FROM CircleChatReadReceipt r WHERE r.messageId IN :messageIds")
    List<CircleChatReadReceipt> findByMessageIdIn(Set<UUID> messageIds);
}
