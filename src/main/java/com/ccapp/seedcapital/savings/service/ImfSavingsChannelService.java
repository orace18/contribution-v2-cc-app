package com.ccapp.seedcapital.savings.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.NotFoundException;
import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financing.domain.entity.FinancialPartner;
import com.ccapp.financing.domain.enums.FinancialPartnerStatus;
import com.ccapp.financing.domain.enums.FinancialPartnerType;
import com.ccapp.financing.repository.FinancialPartnerRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;
import com.ccapp.seedcapital.savings.dto.ConfigureImfSavingsChannelRequest;
import com.ccapp.seedcapital.savings.dto.ImfSavingsChannelResponse;
import com.ccapp.seedcapital.savings.repository.ImfSavingsChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImfSavingsChannelService {

    private static final String NOT_CONFIGURED_STATUS = "NOT_CONFIGURED";

    private final AccountHolderService accountHolderService;
    private final CountryContextService countryContextService;
    private final FinancialPartnerRepository financialPartnerRepository;
    private final ImfSavingsChannelRepository imfSavingsChannelRepository;

    @Transactional
    public ImfSavingsChannelResponse upsertMyChannel(UUID requesterAccountHolderId, ConfigureImfSavingsChannelRequest request) {
        accountHolderService.getById(requesterAccountHolderId);
        CountryContext countryContext = countryContextService.resolve(requesterAccountHolderId);
        FinancialPartner partner = loadEligibleImfPartner(request.partnerId(), countryContext.countryCode());

        ImfSavingsChannel channel = imfSavingsChannelRepository
                .findFirstByAccountHolderIdAndCountryCodeOrderByUpdatedAtDesc(requesterAccountHolderId, countryContext.countryCode())
                .orElseGet(() -> ImfSavingsChannel.builder()
                        .accountHolderId(requesterAccountHolderId)
                        .countryCode(countryContext.countryCode())
                        .build());
        channel.setPartnerId(partner.getId());
        channel.setPartnerType(partner.getType());
        channel.setCountryCode(countryContext.countryCode());
        channel.setPartnerMemberReference(normalizeOptionalText(request.partnerMemberReference()));
        channel.setExternalChannelReference(normalizeOptionalText(request.externalChannelReference()));
        channel.setReadinessNote(normalizeOptionalText(request.readinessNote()));
        channel.setConfiguredAt(channel.getConfiguredAt() == null ? OffsetDateTime.now() : channel.getConfiguredAt());
        channel.setStatus(resolveStatus(channel.getExternalChannelReference()));
        channel.setActivatedAt(channel.getStatus() == ImfSavingsChannelStatus.ACTIVE
                ? channel.getActivatedAt() == null ? OffsetDateTime.now() : channel.getActivatedAt()
                : null);

        return toResponse(imfSavingsChannelRepository.save(channel));
    }

    @Transactional(readOnly = true)
    public ImfSavingsChannelResponse getMyChannel(UUID requesterAccountHolderId) {
        accountHolderService.getById(requesterAccountHolderId);
        CountryContext countryContext = countryContextService.resolve(requesterAccountHolderId);
        return findChannelForCountry(requesterAccountHolderId, countryContext.countryCode())
                .map(this::toResponse)
                .orElse(new ImfSavingsChannelResponse(
                        null,
                        requesterAccountHolderId,
                        null,
                        FinancialPartnerType.IMF,
                        countryContext.countryCode(),
                        null,
                        false,
                        null,
                        null,
                        NOT_CONFIGURED_STATUS,
                        null,
                        null,
                        null
                ));
    }

    @Transactional(readOnly = true)
    public Optional<ImfSavingsChannel> findChannelForCountry(UUID accountHolderId, String countryCode) {
        return imfSavingsChannelRepository.findFirstByAccountHolderIdAndCountryCodeOrderByUpdatedAtDesc(accountHolderId, countryCode);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ImfSavingsChannel> findChannelsForCountry(Collection<UUID> accountHolderIds, String countryCode) {
        Map<UUID, ImfSavingsChannel> channelsByMember = new LinkedHashMap<>();
        imfSavingsChannelRepository.findByAccountHolderIdInAndCountryCodeOrderByUpdatedAtDesc(accountHolderIds, countryCode)
                .forEach(channel -> channelsByMember.putIfAbsent(channel.getAccountHolderId(), channel));
        return channelsByMember;
    }

    @Transactional(readOnly = true)
    public ImfSavingsChannel requireReadyChannel(UUID accountHolderId, String countryCode) {
        ImfSavingsChannel channel = findChannelForCountry(accountHolderId, countryCode)
                .orElseThrow(() -> new BusinessException("Active IMF savings channel is required"));
        if (!isReady(channel)) {
            throw new BusinessException("IMF savings channel is not ready");
        }
        return channel;
    }

    @Transactional(readOnly = true)
    public boolean isReady(ImfSavingsChannel channel) {
        if (channel == null || channel.getStatus() != ImfSavingsChannelStatus.ACTIVE) {
            return false;
        }
        return financialPartnerRepository.findById(channel.getPartnerId())
                .filter(partner -> partner.getType() == FinancialPartnerType.IMF)
                .filter(partner -> partner.getStatus() == FinancialPartnerStatus.ACTIVE)
                .isPresent();
    }

    private FinancialPartner loadEligibleImfPartner(UUID partnerId, String countryCode) {
        FinancialPartner partner = financialPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new NotFoundException("Financial partner not found"));
        if (partner.getType() != FinancialPartnerType.IMF) {
            throw new BusinessException("Selected partner is not an IMF partner");
        }
        if (partner.getStatus() != FinancialPartnerStatus.ACTIVE) {
            throw new BusinessException("Selected IMF partner is not active");
        }
        if (!partner.getCountryCode().equalsIgnoreCase(countryCode)) {
            throw new BusinessException("IMF partner country must match member country");
        }
        return partner;
    }

    private ImfSavingsChannelStatus resolveStatus(String externalChannelReference) {
        return externalChannelReference == null || externalChannelReference.isBlank()
                ? ImfSavingsChannelStatus.PENDING_PARTNER_CONFIRMATION
                : ImfSavingsChannelStatus.ACTIVE;
    }

    private ImfSavingsChannelResponse toResponse(ImfSavingsChannel channel) {
        return new ImfSavingsChannelResponse(
                channel.getId(),
                channel.getAccountHolderId(),
                channel.getPartnerId(),
                channel.getPartnerType(),
                channel.getCountryCode(),
                channel.getStatus(),
                isReady(channel),
                channel.getPartnerMemberReference(),
                channel.getExternalChannelReference(),
                channel.getReadinessNote(),
                channel.getConfiguredAt(),
                channel.getActivatedAt(),
                channel.getUpdatedAt()
        );
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
