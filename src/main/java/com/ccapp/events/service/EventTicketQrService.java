package com.ccapp.events.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.events.domain.entity.EventTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EventTicketQrService {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Pattern TICKET_ID_PATTERN = Pattern.compile("\"ticketId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BUYER_ID_PATTERN = Pattern.compile("\"buyerId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]+)\"");

    private final String qrSecret;

    public EventTicketQrService(@Value("${events.qr.secret:events-qr-dev-secret}") String qrSecret) {
        this.qrSecret = qrSecret;
    }

    public String generate(EventTicket ticket) {
        try {
            return String.format(
                    Locale.ROOT,
                    "{\"ticketId\":\"%s\",\"eventId\":\"%s\",\"buyerId\":\"%s\",\"signature\":\"%s\"}",
                    ticket.getId(),
                    ticket.getEvent().getId(),
                    ticket.getBuyerAccountHolderId(),
                    signature(ticket.getId(), ticket.getEvent().getId(), ticket.getBuyerAccountHolderId())
            );
        } catch (Exception ex) {
            throw new BusinessException("Unable to generate event ticket QR code");
        }
    }

    public DecodedQrPayload decodeAndVerify(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return DecodedQrPayload.invalid();
        }
        try {
            UUID ticketId = parseUuid(extractField(qrCode, TICKET_ID_PATTERN));
            UUID eventId = parseUuid(extractField(qrCode, EVENT_ID_PATTERN));
            UUID buyerId = parseUuid(extractField(qrCode, BUYER_ID_PATTERN));
            String signature = extractField(qrCode, SIGNATURE_PATTERN);
            if (ticketId == null || eventId == null || buyerId == null || signature == null || signature.isBlank()) {
                return DecodedQrPayload.invalid();
            }
            String expectedSignature = signature(ticketId, eventId, buyerId);
            boolean valid = MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
            return valid
                    ? new DecodedQrPayload(ticketId, eventId, buyerId, true)
                    : DecodedQrPayload.invalid();
        } catch (Exception ex) {
            return DecodedQrPayload.invalid();
        }
    }

    private String signature(UUID ticketId, UUID eventId, UUID buyerId) {
        try {
            String content = ticketId + "|" + eventId + "|" + buyerId;
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("Unable to sign event ticket QR code");
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractField(String payload, Pattern pattern) {
        Matcher matcher = pattern.matcher(payload);
        return matcher.find() ? matcher.group(1) : null;
    }

    public record DecodedQrPayload(UUID ticketId, UUID eventId, UUID buyerId, boolean valid) {
        public static DecodedQrPayload invalid() {
            return new DecodedQrPayload(null, null, null, false);
        }
    }
}
