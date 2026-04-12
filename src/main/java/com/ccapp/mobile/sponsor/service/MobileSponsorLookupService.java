package com.ccapp.mobile.sponsor.service;

import com.ccapp.circles.service.InviteCodeRateLimiter;
import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.security.ClientRequestMetadataResolver;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.mobile.sponsor.dto.SponsorLookupRequest;
import com.ccapp.mobile.sponsor.dto.SponsorLookupResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileSponsorLookupService {

    private static final int MAX_PER_IP      = 20;
    private static final int MAX_PER_SESSION = 10;

    private static final String NOT_ELIGIBLE_MESSAGE =
            "Ce numéro n'appartient pas à un membre du réseau Club Chrétien.";
    private static final String NOT_IN_NETWORK_REASON = "NOT_IN_NETWORK";

    private final AccountHolderRepository accountHolderRepository;
    private final SponsorEligibilityValidator eligibilityValidator;
    private final PhoneValidator phoneValidator;
    private final InviteCodeRateLimiter rateLimiter;
    private final ClientRequestMetadataResolver metadataResolver;

    @Transactional(readOnly = true)
    public SponsorLookupResponse lookup(UUID requestingAccountHolderId, SponsorLookupRequest request) {
        String clientIp = metadataResolver.current().ipAddress();
        enforceRateLimit("sponsor-ip:" + clientIp, MAX_PER_IP);
        enforceRateLimit("sponsor-session:" + requestingAccountHolderId, MAX_PER_SESSION);

        String normalizedPhone = normalizePhone(request.phoneNumber().trim());
        phoneValidator.validate(normalizedPhone, request.country());
        log.info("MobileSponsorLookupService lookup requestedBy={} phone={} country={}",
                requestingAccountHolderId, maskPhone(normalizedPhone), request.country());

        Optional<AccountHolder> found = accountHolderRepository.findByPhoneNumber(normalizedPhone);
        if (found.isEmpty()) {
            // Try alternate format (with/without leading +)
            String altPhone = normalizedPhone.startsWith("+")
                    ? normalizedPhone.substring(1)
                    : "+" + normalizedPhone;
            found = accountHolderRepository.findByPhoneNumber(altPhone);
        }

        if (found.isEmpty()) {
            log.info("MobileSponsorLookupService not found phone={}", maskPhone(normalizedPhone));
            return SponsorLookupResponse.notFound();
        }

        AccountHolder sponsor = found.get();

        if (!eligibilityValidator.isEligible(sponsor)) {
            log.info("MobileSponsorLookupService not eligible accountHolderId={}", sponsor.getId());
            return SponsorLookupResponse.notEligible(NOT_ELIGIBLE_MESSAGE, NOT_IN_NETWORK_REASON);
        }

        log.info("MobileSponsorLookupService eligible accountHolderId={}", sponsor.getId());
        return SponsorLookupResponse.eligible(
                sponsor.getId(),
                sponsor.getFirstName(),
                sponsor.getLastName(),
                maskPhone(sponsor.getPhoneNumber())
        );
    }

    private void enforceRateLimit(String key, int maxPerMinute) {
        if (!rateLimiter.allow(key, maxPerMinute)) {
            throw new BusinessException("TOO_MANY_REQUESTS");
        }
    }

    /**
     * Ensures international format: always starts with '+'.
     * "+22961000000" stays as-is, "22961000000" becomes "+22961000000".
     */
    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return phone;
        return phone.startsWith("+") ? phone : "+" + phone;
    }

    /**
     * Masks the middle of the phone number for safe logging and response.
     * Example: "+22961000000" → "+22961****00"
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) return "****";
        int prefixLen = Math.min(6, phone.length() - 2);
        String prefix = phone.substring(0, prefixLen);
        String suffix = phone.substring(phone.length() - 2);
        int maskedLen = phone.length() - prefixLen - 2;
        return prefix + "*".repeat(Math.max(maskedLen, 2)) + suffix;
    }
}
