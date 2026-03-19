package com.ccapp.community.service;

import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.community.domain.entity.CircleChatMessage;
import com.ccapp.community.domain.entity.CircleChatReadReceipt;
import com.ccapp.community.domain.enums.CircleChatMessageType;
import com.ccapp.community.dto.CircleChatEvent;
import com.ccapp.community.dto.CircleChatMessageRequest;
import com.ccapp.community.dto.CircleChatMessageResponse;
import com.ccapp.community.repository.CircleChatMessageRepository;
import com.ccapp.community.repository.CircleChatReadReceiptRepository;
import com.ccapp.media.domain.entity.MediaFile;
import com.ccapp.media.domain.enums.MediaStatus;
import com.ccapp.media.repository.MediaFileRepository;
import com.ccapp.media.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircleChatService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CircleChatMessageRepository circleChatMessageRepository;
    private final CircleChatReadReceiptRepository readReceiptRepository;
    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final MediaFileRepository mediaFileRepository;
    private final ObjectStorageService objectStorageService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${media.download-url-ttl-minutes:15}")
    private long downloadUrlTtlMinutes;

    @Transactional
    public CircleChatMessageResponse sendMessage(UUID circleId, UUID senderId, CircleChatMessageRequest request) {
        ensureCircleExists(circleId);
        ensureIsMember(circleId, senderId);

        CircleChatMessageType type = request.messageType() != null ? request.messageType() : CircleChatMessageType.TEXT;
        validateContent(type, request);

        // déduplication côté client
        if (request.clientMessageId() != null) {
            var existing = circleChatMessageRepository
                    .findByCircleIdAndSenderIdAndClientMessageId(circleId, senderId, request.clientMessageId());
            if (existing.isPresent()) {
                MediaFile media = existing.get().getMediaId() != null
                        ? mediaFileRepository.findById(existing.get().getMediaId()).orElse(null)
                        : null;
                return toResponse(existing.get(), List.of(), media);
            }
        }

        // vérification du media sur MinIO
        MediaFile mediaFile = null;
        if (request.mediaId() != null) {
            mediaFile = mediaFileRepository.findById(request.mediaId())
                    .orElseThrow(() -> new NotFoundException("Media file not found"));
            if (mediaFile.getStatus() != MediaStatus.READY) {
                throw new BusinessException("Media file is not ready — confirm the upload first");
            }
        }

        CircleChatMessage message = circleChatMessageRepository.save(CircleChatMessage.builder()
                .circleId(circleId)
                .senderId(senderId)
                .messageType(type)
                .content(request.content() != null ? request.content().trim() : null)
                .mediaId(request.mediaId())
                .mediaDurationSecs(request.mediaDurationSecs())
                .clientMessageId(request.clientMessageId())
                .build());

        CircleChatMessageResponse response = toResponse(message, List.of(), mediaFile);

        messagingTemplate.convertAndSend(
                "/topic/circles/" + circleId + "/chat",
                CircleChatEvent.newMessage(response)
        );

        return response;
    }

    @Transactional(readOnly = true)
    public List<CircleChatMessageResponse> getMessages(UUID circleId, OffsetDateTime before, int limit) {
        ensureCircleExists(circleId);
        int pageSize = Math.min(Math.max(1, limit), MAX_PAGE_SIZE);

        List<CircleChatMessage> messages;
        if (before != null) {
            messages = circleChatMessageRepository.findByCircleIdBeforeOrderBySentAtDesc(
                    circleId, before, PageRequest.of(0, pageSize));
        } else {
            messages = circleChatMessageRepository.findByCircleIdOrderBySentAtDesc(
                    circleId, PageRequest.of(0, pageSize));
        }

        // charger read receipts en batch
        Set<UUID> messageIds = messages.stream().map(CircleChatMessage::getId).collect(Collectors.toSet());
        Map<UUID, List<CircleChatReadReceipt>> receiptsByMessage = messageIds.isEmpty() ? Map.of()
                : readReceiptRepository.findByMessageIdIn(messageIds).stream()
                        .collect(Collectors.groupingBy(CircleChatReadReceipt::getMessageId));

        // charger les media en batch
        Set<UUID> mediaIds = messages.stream()
                .map(CircleChatMessage::getMediaId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, MediaFile> mediaById = mediaIds.isEmpty() ? Map.of()
                : mediaFileRepository.findAllById(mediaIds).stream()
                        .collect(Collectors.toMap(MediaFile::getId, m -> m));

        return messages.stream()
                .map(m -> toResponse(
                        m,
                        receiptsByMessage.getOrDefault(m.getId(), List.of()),
                        m.getMediaId() != null ? mediaById.get(m.getMediaId()) : null
                ))
                .toList();
    }

    @Transactional
    public void markRead(UUID circleId, UUID messageId, UUID memberId) {
        ensureIsMember(circleId, memberId);

        CircleChatMessage message = circleChatMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!message.getCircleId().equals(circleId)) {
            throw new BusinessException("Message does not belong to this circle");
        }
        if (message.getSenderId().equals(memberId)) {
            return;
        }

        if (!readReceiptRepository.existsByMessageIdAndMemberId(messageId, memberId)) {
            OffsetDateTime now = OffsetDateTime.now();
            readReceiptRepository.save(CircleChatReadReceipt.builder()
                    .messageId(messageId)
                    .memberId(memberId)
                    .readAt(now)
                    .build());

            messagingTemplate.convertAndSend(
                    "/topic/circles/" + circleId + "/chat",
                    CircleChatEvent.readReceipt(messageId, memberId, now)
            );
        }
    }

    @Transactional
    public void markAllRead(UUID circleId, UUID memberId) {
        ensureCircleExists(circleId);
        ensureIsMember(circleId, memberId);

        List<CircleChatMessage> messages = circleChatMessageRepository
                .findByCircleIdOrderBySentAtDesc(circleId, PageRequest.of(0, MAX_PAGE_SIZE));

        OffsetDateTime now = OffsetDateTime.now();
        messages.stream()
                .filter(m -> !m.getSenderId().equals(memberId))
                .filter(m -> !readReceiptRepository.existsByMessageIdAndMemberId(m.getId(), memberId))
                .forEach(m -> {
                    readReceiptRepository.save(CircleChatReadReceipt.builder()
                            .messageId(m.getId())
                            .memberId(memberId)
                            .readAt(now)
                            .build());
                    messagingTemplate.convertAndSend(
                            "/topic/circles/" + circleId + "/chat",
                            CircleChatEvent.readReceipt(m.getId(), memberId, now)
                    );
                });
    }

    private void validateContent(CircleChatMessageType type, CircleChatMessageRequest request) {
        if (type == CircleChatMessageType.TEXT) {
            if (request.content() == null || request.content().isBlank()) {
                throw new BusinessException("content is required for TEXT messages");
            }
        } else {
            if (request.mediaId() == null) {
                throw new BusinessException("mediaId is required for " + type + " messages — upload first via /api/media/upload-url");
            }
        }
    }

    private CircleChatMessageResponse toResponse(CircleChatMessage message,
                                                  List<CircleChatReadReceipt> receipts,
                                                  MediaFile mediaFile) {
        String presignedUrl = null;
        String contentType = null;

        if (mediaFile != null && mediaFile.getStatus() == MediaStatus.READY) {
            presignedUrl = objectStorageService
                    .createDownloadUrl(mediaFile.getBucket(), mediaFile.getObjectKey(), Duration.ofMinutes(downloadUrlTtlMinutes))
                    .url();
            contentType = mediaFile.getContentType();
        }

        List<CircleChatMessageResponse.ReadReceiptInfo> readBy = receipts.stream()
                .map(r -> new CircleChatMessageResponse.ReadReceiptInfo(r.getMemberId(), r.getReadAt()))
                .toList();

        return new CircleChatMessageResponse(
                message.getId(),
                message.getCircleId(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                message.getMediaId(),
                presignedUrl,
                contentType,
                message.getMediaDurationSecs(),
                message.getClientMessageId(),
                readBy,
                message.getSentAt()
        );
    }

    private void ensureCircleExists(UUID circleId) {
        if (!circleRepository.existsById(circleId)) {
            throw new NotFoundException("Circle not found");
        }
    }

    private void ensureIsMember(UUID circleId, UUID accountHolderId) {
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, accountHolderId)) {
            throw new BusinessException("User is not a member of this circle");
        }
    }
}
