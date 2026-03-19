package com.ccapp.community.repository;

import com.ccapp.community.domain.entity.CircleChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CircleChatMessageRepository extends JpaRepository<CircleChatMessage, UUID> {

    @Query("SELECT m FROM CircleChatMessage m WHERE m.circleId = :circleId ORDER BY m.sentAt DESC")
    List<CircleChatMessage> findByCircleIdOrderBySentAtDesc(UUID circleId, Pageable pageable);

    @Query("SELECT m FROM CircleChatMessage m WHERE m.circleId = :circleId AND m.sentAt < :before ORDER BY m.sentAt DESC")
    List<CircleChatMessage> findByCircleIdBeforeOrderBySentAtDesc(UUID circleId, OffsetDateTime before, Pageable pageable);

    Optional<CircleChatMessage> findByCircleIdAndSenderIdAndClientMessageId(UUID circleId, UUID senderId, String clientMessageId);
}
