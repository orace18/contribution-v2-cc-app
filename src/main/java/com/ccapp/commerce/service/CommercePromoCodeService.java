package com.ccapp.commerce.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.commerce.domain.entity.CommercePromoCode;
import com.ccapp.commerce.domain.enums.CommercePromoCodeStatus;
import com.ccapp.commerce.dto.CommerceCreatePromoCodeRequest;
import com.ccapp.commerce.dto.CommercePromoCodeResponse;
import com.ccapp.commerce.dto.CommerceValidatePromoCodeRequest;
import com.ccapp.commerce.dto.CommerceValidatePromoCodeResponse;
import com.ccapp.commerce.repository.CommercePromoCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommercePromoCodeService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final CommercePromoCodeRepository commercePromoCodeRepository;
    private final CommerceMerchantService commerceMerchantService;

    @Transactional
    public CommerceValidatePromoCodeResponse validate(CommerceValidatePromoCodeRequest request) {
        ValidatedPromoCode validated = validateBasic(request.code(), request.country(), request.cartAmount());
        return new CommerceValidatePromoCodeResponse(
                true,
                validated.code(),
                validated.discountAmount(),
                validated.ownerAccountHolderId(),
                validated.expiryDate()
        );
    }

    @Transactional
    public CommercePromoCodeResponse create(UUID ownerAccountHolderId, CommerceCreatePromoCodeRequest request) {
        commerceMerchantService.ensureMerchant(ownerAccountHolderId);
        String normalizedCode = normalizeCode(request.code());
        if (commercePromoCodeRepository.findByCodeIgnoreCase(normalizedCode).isPresent()) {
            throw new BusinessException("PROMO_CODE_ALREADY_EXISTS");
        }

        CommercePromoCode saved = commercePromoCodeRepository.save(CommercePromoCode.builder()
                .id(UUID.randomUUID())
                .code(normalizedCode)
                .ownerAccountHolderId(ownerAccountHolderId)
                .discountAmount(scale(request.discountAmount()))
                .country(normalizeCountry(request.country()))
                .status(CommercePromoCodeStatus.ACTIVE)
                .expiryDate(request.expiryDate())
                .usageLimit(request.usageLimit())
                .usedCount(0)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public List<CommercePromoCodeResponse> listByOwner(UUID ownerAccountHolderId) {
        return commercePromoCodeRepository.findByOwnerAccountHolderIdOrderByCreatedAtDesc(ownerAccountHolderId).stream()
                .map(this::refreshStatusIfNeeded)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ValidatedPromoCode validateForCheckout(String code,
                                                  String countryCode,
                                                  BigDecimal grossAmount,
                                                  BigDecimal totalDsbAmount) {
        ValidatedPromoCode validated = validateBasic(code, countryCode, grossAmount);
        if (totalDsbAmount == null || scale(totalDsbAmount).signum() <= 0) {
            throw new BusinessException("PROMO_CODE_INVALID_FOR_ORDER");
        }
        if (validated.discountAmount().compareTo(scale(totalDsbAmount)) > 0) {
            throw new BusinessException("PROMO_CODE_INVALID_FOR_ORDER");
        }
        return validated;
    }

    @Transactional
    public void incrementUsage(UUID promoCodeId) {
        if (promoCodeId == null) {
            return;
        }
        CommercePromoCode promoCode = commercePromoCodeRepository.findById(promoCodeId)
                .orElseThrow(() -> new BusinessException("PROMO_CODE_NOT_FOUND"));
        refreshStatusInPlace(promoCode);
        if (promoCode.getStatus() != CommercePromoCodeStatus.ACTIVE) {
            throw new BusinessException(resolveInvalidCodeMessage(promoCode));
        }
        promoCode.setUsedCount((promoCode.getUsedCount() == null ? 0 : promoCode.getUsedCount()) + 1);
        refreshStatusInPlace(promoCode);
        commercePromoCodeRepository.save(promoCode);
    }

    @Transactional(readOnly = true)
    public String findCode(UUID promoCodeId) {
        if (promoCodeId == null) {
            return null;
        }
        return commercePromoCodeRepository.findById(promoCodeId)
                .map(CommercePromoCode::getCode)
                .orElse(null);
    }

    private ValidatedPromoCode validateBasic(String code,
                                             String countryCode,
                                             BigDecimal cartAmount) {
        String normalizedCode = normalizeCode(code);
        CommercePromoCode promoCode = commercePromoCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new BusinessException("PROMO_CODE_NOT_FOUND"));
        promoCode = refreshStatusIfNeeded(promoCode);
        if (promoCode.getStatus() != CommercePromoCodeStatus.ACTIVE) {
            throw new BusinessException(resolveInvalidCodeMessage(promoCode));
        }
        if (!normalizeCountry(countryCode).equalsIgnoreCase(promoCode.getCountry())) {
            throw new BusinessException("PROMO_CODE_COUNTRY_MISMATCH");
        }
        BigDecimal amount = scale(cartAmount);
        if (amount.signum() <= 0 || promoCode.getDiscountAmount().compareTo(amount) >= 0) {
            throw new BusinessException("PROMO_CODE_INVALID_FOR_ORDER");
        }
        return new ValidatedPromoCode(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getOwnerAccountHolderId(),
                scale(promoCode.getDiscountAmount()),
                promoCode.getExpiryDate()
        );
    }

    private CommercePromoCode refreshStatusIfNeeded(CommercePromoCode promoCode) {
        CommercePromoCodeStatus before = promoCode.getStatus();
        refreshStatusInPlace(promoCode);
        if (promoCode.getStatus() != before) {
            return commercePromoCodeRepository.save(promoCode);
        }
        return promoCode;
    }

    private void refreshStatusInPlace(CommercePromoCode promoCode) {
        if (promoCode.getExpiryDate() != null && promoCode.getExpiryDate().isBefore(OffsetDateTime.now())) {
            promoCode.setStatus(CommercePromoCodeStatus.EXPIRED);
            return;
        }
        if (promoCode.getUsageLimit() != null
                && promoCode.getUsedCount() != null
                && promoCode.getUsedCount() >= promoCode.getUsageLimit()) {
            promoCode.setStatus(CommercePromoCodeStatus.USAGE_LIMIT_REACHED);
        }
    }

    private String resolveInvalidCodeMessage(CommercePromoCode promoCode) {
        return switch (promoCode.getStatus()) {
            case DISABLED -> "PROMO_CODE_DISABLED";
            case EXPIRED -> "PROMO_CODE_EXPIRED";
            case USAGE_LIMIT_REACHED -> "PROMO_CODE_USAGE_LIMIT_REACHED";
            case ACTIVE -> "PROMO_CODE_INVALID_FOR_ORDER";
        };
    }

    private CommercePromoCodeResponse toResponse(CommercePromoCode promoCode) {
        return new CommercePromoCodeResponse(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getOwnerAccountHolderId(),
                scale(promoCode.getDiscountAmount()),
                promoCode.getCountry(),
                promoCode.getStatus(),
                promoCode.getExpiryDate(),
                promoCode.getUsageLimit(),
                promoCode.getUsedCount()
        );
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("PROMO_CODE_NOT_FOUND");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCountry(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new BusinessException("PROMO_CODE_COUNTRY_MISMATCH");
        }
        return countryCode.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    public record ValidatedPromoCode(
            UUID promoCodeId,
            String code,
            UUID ownerAccountHolderId,
            BigDecimal discountAmount,
            OffsetDateTime expiryDate
    ) {
    }
}
