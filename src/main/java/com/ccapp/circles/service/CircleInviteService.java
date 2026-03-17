package com.ccapp.circles.service;

import com.ccapp.circles.domain.entity.Circle;
import com.ccapp.circles.domain.entity.CircleInvite;
import com.ccapp.circles.domain.enums.CircleInviteStatus;
import com.ccapp.circles.domain.enums.CircleStatus;
import com.ccapp.circles.dto.*;
import com.ccapp.circles.repository.CircleInviteRepository;
import com.ccapp.circles.repository.CircleMemberRepository;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.config.domain.enums.CountryConfigType;
import com.ccapp.country.config.service.CountryConfigResolver;
import com.ccapp.notifications.dispatcher.PhoneMessageDispatchResult;
import com.ccapp.notifications.dispatcher.PhoneMessageGateway;
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

    private static final int DEFAULT_MAX_FINANCIAL_MEMBERS = 5;
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final String CIRCLE_INVITE_EXPIRED = "CIRCLE_INVITE_EXPIRED";
    private static final String CIRCLE_INVITE_EXHAUSTED = "CIRCLE_INVITE_EXHAUSTED";

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final CircleInviteRepository circleInviteRepository;
    private final CircleService circleService;
    private final PhoneMessageGateway phoneMessageGateway;
    private final CountryConfigResolver countryConfigResolver;

    @Value("${circles.invite.default-expiration-hours:72}")
    private int defaultExpirationHours;

    @Value("${circles.invite.default-max-uses:3}")
    private int defaultMaxUses;

    @Value("${circles.invite.base-url:https://app.ccapp.com/circles/join}")
    private String inviteBaseUrl;

    @Value("${circles.invite.deep-link-base:ccapp://circles/join}")
    private String inviteDeepLinkBase;

    @Transactional
    public CircleInviteResponse createInvite(UUID actorAccountHolderId, UUID circleId, CreateCircleInviteRequest request) {
        Circle circle = loadCircle(circleId);
        assertCircleParticipant(actorAccountHolderId, circleId);

        int remainingSlots = remainingFinancialSlots(circleId);
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

        return toResponse(invite);
    }

    @Transactional
    public CircleInviteQrResponse getInviteQr(UUID actorAccountHolderId, UUID circleId, UUID inviteId) {
        assertCircleParticipant(actorAccountHolderId, circleId);
        CircleInvite invite = getInvite(circleId, inviteId);
        ensureInviteIsUsable(invite);
        return new CircleInviteQrResponse(
                invite.getId(),
                buildQrPayload(invite.getInvitationToken()),
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

    private int remainingFinancialSlots(UUID circleId) {
        long count = circleMemberRepository.countByCircle_IdAndFinancialMemberTrue(circleId);
        Circle circle = circleRepository.findById(circleId)
                .orElseThrow(() -> new NotFoundException("Circle not found"));
        int maxFinancialMembers = resolveMaxFinancialMembers(circle.getCountryCode());
        return Math.max(0, maxFinancialMembers - (int) count);
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

    private CircleInviteResponse toResponse(CircleInvite invite) {
        return new CircleInviteResponse(
                invite.getId(),
                invite.getCircle().getId(),
                invite.getStatus(),
                invite.getInvitationToken(),
                buildInviteLink(invite.getInvitationToken()),
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

    private String buildQrPayload(String token) {
        return inviteDeepLinkBase + "?token=" + token;
    }

    private String generateToken() {
        byte[] buffer = new byte[32];
        TOKEN_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String normalizePhone(String phoneNumber) {
        String normalized = phoneNumber == null ? "" : phoneNumber.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("PHONE_NUMBER_INVALID");
        }
        return normalized;
    }

    private int resolveMaxFinancialMembers(String countryCode) {
        CountryConfigResolver.ResolvedConfig circleConfig = countryConfigResolver.resolveActiveConfig(
                countryCode,
                CountryConfigType.CIRCLE_CONFIG
        );
        ObjectNode payload = countryConfigResolver.payloadAsObjectNode(circleConfig);
        if (!payload.has("maxMembers") || payload.get("maxMembers").isNull()) {
            return DEFAULT_MAX_FINANCIAL_MEMBERS;
        }
        if (!payload.get("maxMembers").canConvertToInt()) {
            throw new BusinessException("circle_config.maxMembers must be an integer");
        }
        int value = payload.get("maxMembers").intValue();
        return value > 0 ? value : DEFAULT_MAX_FINANCIAL_MEMBERS;
    }
}
