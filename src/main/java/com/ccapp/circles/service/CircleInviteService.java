package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleInvite;
import com.ccapp.circles.domain.entity.InviteCode;
import com.ccapp.circles.domain.enums.CircleInviteStatus;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.CircleInviteRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.circles.repository.InviteCodeRepository;
import com.ccapp.common.events.DomainEventPublisher;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.notifications.dispatcher.PhoneMessageDispatchResult;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
import com.ccapp.progress.event.FriendInvitedEvent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CircleInviteService {

    private static final int DEFAULT_MAX_MEMBERS = 6;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final String CIRCLE_INVITE_EXPIRED = "CIRCLE_INVITE_EXPIRED";
    private static final String CIRCLE_INVITE_EXHAUSTED = "CIRCLE_INVITE_EXHAUSTED";

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleInviteRepository circleInviteRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeRateLimiter inviteCodeRateLimiter;
    private final CircleService circleService;
    private final PhoneMessageGateway phoneMessageGateway;
    private final CountryConfigResolver countryConfigResolver;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${circles.invite.default-expiration-hours:72}")
    private int defaultExpirationHours;

    @Value("${circles.invite.default-max-uses:3}")
    private int defaultMaxUses;

    @Value("${circles.invite.base-url:ccapp://circles/join}")
    private String inviteBaseUrl;

    @Value("${circles.invite.deep-link-base:ccapp://circles/join}")
    private String inviteDeepLinkBase;

    @Value("${circles.invite.qr-base-url:ccapp://circles/join}")
    private String inviteQrBaseUrl;

    @Value("${circles.invite.code-expiration-hours:24}")
    private int codeExpirationHours;

    @Value("${circles.invite.code-rate-limit-per-minute:5}")
    private int codeRateLimitPerMinute;

    @Transactional
    public CircleInviteResponse createInvite(UUID actorAccountHolderId, UUID circleId, CreateCircleInviteRequest request) {
        Circle circle = loadCircle(circleId);
        assertCircleParticipant(actorAccountHolderId, circleId);

        int remainingSlots = remainingMemberSlots(circleId);
        if (remainingSlots <= 0) {
            throw new BusinessException("CIRCLE_FULL");
        }

        int requestedMaxUses = request != null && request.maxUses() != null
                ? request.maxUses()
                : defaultMaxUses;
        int effectiveMaxUses = Math.max(1, Math.min(requestedMaxUses, remainingSlots));

        int expiresInHours = request != null && request.expiresInHours() != null
                ? request.expiresInHours()
                : defaultExpirationHours;

        CircleInvite invite = circleInviteRepository.save(CircleInvite.builder()
                .circle(circle)
                .createdByAccountHolderId(actorAccountHolderId)
                .invitationToken(generateToken())
                .status(CircleInviteStatus.ACTIVE)
                .maxUses(effectiveMaxUses)
                .usedCount(0)
                .expiresAt(OffsetDateTime.now().plusHours(expiresInHours))
                .build());

        InviteCode inviteCode = inviteCodeRepository.save(InviteCode.builder()
                .code(generateShortCode())
                .invite(invite)
                .circle(circle)
                .expiresAt(OffsetDateTime.now().plusHours(codeExpirationHours))
                .build());

        domainEventPublisher.publish(FriendInvitedEvent.create(actorAccountHolderId, circleId, invite.getId()));

        return toResponse(invite, inviteCode.getCode());
    }

    @Transactional(readOnly = true)
    public ResolveInviteCodeResponse resolveInviteCode(String code, String clientIp) {
        if (!inviteCodeRateLimiter.allow(clientIp, codeRateLimitPerMinute)) {
            throw new BusinessException("RATE_LIMIT_EXCEEDED");
        }

        InviteCode inviteCode = inviteCodeRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Invite code not found"));

        if (inviteCode.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(CIRCLE_INVITE_EXPIRED);
        }

        CircleInvite invite = inviteCode.getInvite();
        ensureInviteIsUsable(invite);

        Circle circle = inviteCode.getCircle();
        long membersCount = circleMemberRepository.countByCircle_Id(circle.getId());

        return new ResolveInviteCodeResponse(
                circle.getId(),
                circle.getName(),
                (int) membersCount,
                invite.getInvitationToken()
        );
    }

    @Transactional
    public CircleInviteQrResponse getInviteQr(UUID actorAccountHolderId, UUID circleId, UUID inviteId) {
        assertCircleParticipant(actorAccountHolderId, circleId);
        CircleInvite invite = getInvite(circleId, inviteId);
        ensureInviteIsUsable(invite);

        InviteCode inviteCode = inviteCodeRepository.findByInvite_Id(invite.getId())
                .orElseThrow(() -> new NotFoundException("Invite code not found"));

        return new CircleInviteQrResponse(
                invite.getId(),
                buildQrPayload(inviteCode.getCode()),
                buildInviteLink(invite.getInvitationToken()),
                invite.getExpiresAt()
        );
    }

    @Transactional
    public SendCircleInviteWhatsAppResponse sendInviteViaWhatsApp(UUID actorAccountHolderId,
                                                                  UUID circleId,
                                                                  UUID inviteId,
                                                                  SendCircleInviteWhatsAppRequest request) {
        assertCircleParticipant(actorAccountHolderId, circleId);
        CircleInvite invite = getInvite(circleId, inviteId);
        ensureInviteIsUsable(invite);

        String normalizedPhone = normalizePhone(request.phoneNumber());
        String message = request.message() == null || request.message().isBlank()
                ? defaultInviteMessage(invite)
                : request.message().trim();

        PhoneMessageDispatchResult dispatchResult = phoneMessageGateway.sendWhatsApp(normalizedPhone, message);
        return new SendCircleInviteWhatsAppResponse(
                invite.getId(),
                normalizedPhone,
                dispatchResult.success(),
                dispatchResult.providerResponse()
        );
    }

    @Transactional
    public CircleMemberResponse joinViaInviteToken(UUID accountHolderId, String invitationToken, JoinCircleRequest request) {
        CircleInvite invite = circleInviteRepository.findByInvitationToken(invitationToken)
                .orElseThrow(() -> new NotFoundException("Circle invite not found"));
        ensureInviteIsUsable(invite);

        if (invite.getUsedCount() >= invite.getMaxUses()) {
            invite.setStatus(CircleInviteStatus.EXPIRED);
            circleInviteRepository.save(invite);
            throw new BusinessException(CIRCLE_INVITE_EXHAUSTED);
        }

        CircleMemberResponse response = circleService.joinCircle(accountHolderId, invite.getCircle().getId(), request);
        invite.setUsedCount(invite.getUsedCount() + 1);
        if (invite.getUsedCount() >= invite.getMaxUses()) {
            invite.setStatus(CircleInviteStatus.EXPIRED);
        }
        circleInviteRepository.save(invite);
        return response;
    }

    private Circle loadCircle(UUID circleId) {
        return circleRepository.findByIdAndStatus(circleId, CircleStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Active circle not found"));
    }

    private void assertCircleParticipant(UUID actorAccountHolderId, UUID circleId) {
        if (!circleMemberRepository.existsByCircle_IdAndAccountHolderId(circleId, actorAccountHolderId)) {
            throw new BusinessException("NOT_CIRCLE_PARTICIPANT");
        }
    }

    private CircleInvite getInvite(UUID circleId, UUID inviteId) {
        return circleInviteRepository.findByIdAndCircle_Id(inviteId, circleId)
                .orElseThrow(() -> new NotFoundException("Circle invite not found"));
    }

    private int remainingMemberSlots(UUID circleId) {
        long count = circleMemberRepository.countByCircle_Id(circleId);
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new NotFoundException("Circle not found"));
        int maxMembers = resolveMaxMembers(circle.getCountryCode());
        return Math.max(0, maxMembers - (int) count);
    }

    private void ensureInviteIsUsable(CircleInvite invite) {
        if (invite.getStatus() != CircleInviteStatus.ACTIVE) {
            throw new BusinessException(CIRCLE_INVITE_EXPIRED);
        }
        if (invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            invite.setStatus(CircleInviteStatus.EXPIRED);
            circleInviteRepository.save(invite);
            throw new BusinessException(CIRCLE_INVITE_EXPIRED);
        }
    }

    private CircleInviteResponse toResponse(CircleInvite invite, String shortCode) {
        return new CircleInviteResponse(
                invite.getId(),
                invite.getCircle().getId(),
                invite.getStatus(),
                invite.getInvitationToken(),
                buildInviteLink(invite.getInvitationToken()),
                shortCode,
                invite.getMaxUses(),
                invite.getUsedCount(),
                invite.getExpiresAt(),
                invite.getCreatedAt()
        );
    }

    private String defaultInviteMessage(CircleInvite invite) {
        return "Rejoins mon cercle \"" + invite.getCircle().getName() + "\" via ce lien: "
                + buildInviteLink(invite.getInvitationToken());
    }

    private String buildInviteLink(String token) {
        return inviteBaseUrl + "?token=" + token;
    }

    private String buildQrPayload(String code) {
        return inviteQrBaseUrl + "?code=" + code;
    }

    private String generateToken() {
        byte[] buffer = new byte[32];
        TOKEN_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String generateShortCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = String.format("%06d", TOKEN_RANDOM.nextInt(1_000_000));
            if (!inviteCodeRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new BusinessException("INVITE_CODE_GENERATION_FAILED");
    }

    private String normalizePhone(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("PHONE_NUMBER_INVALID");
        }
        return normalized;
    }

    private int resolveMaxMembers(String countryCode) {
        CountryConfigResolver.ResolvedConfig circleConfig = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.GENERAL
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(circleConfig);
        ObjectNode circlePayload = payload.has("circle") && payload.get("circle").isObject()
                ? (ObjectNode) payload.get("circle")
                : payload;
        if (!circlePayload.has("maxMembers") || circlePayload.get("maxMembers").isNull()) {
            return DEFAULT_MAX_MEMBERS;
        }
        if (!circlePayload.get("maxMembers").canConvertToInt()) {
            throw new BusinessException("general.circle.maxMembers must be an integer");
        }
        int value = circlePayload.get("maxMembers").intValue();
        return value > 0 ? value : DEFAULT_MAX_MEMBERS;
    }
}
