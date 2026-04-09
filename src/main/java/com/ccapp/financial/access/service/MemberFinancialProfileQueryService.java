package com.ccapp.financial.access.service;

import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.financial.access.dto.MemberFinancialProfileResponse;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberFinancialProfileQueryService {

    private final MemberFinancialProfileService memberFinancialProfileService;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public MemberFinancialProfileResponse getMyProfile(UUID accountHolderId) {
        MemberFinancialProfile profile = memberFinancialProfileService.getProfile(accountHolderId);
        Wallet latestWallet = walletRepository.findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, "ACTIVE")
                .orElseGet(() -> walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId).stream()
                        .findFirst()
                        .orElse(null));

        boolean walletPinConfigured = latestWallet != null
                && latestWallet.getPinHash() != null
                && !latestWallet.getPinHash().isBlank();

        return new MemberFinancialProfileResponse(
                profile.accountHolderId(),
                profile.membershipStatus() == null ? "NONE" : profile.membershipStatus().name(),
                profile.membershipExpiresAt(),
                profile.walletActive(),
                walletPinConfigured,
                profile.financialReadiness() == FinancialAccessState.FINANCIAL_ALLOWED ? "ALLOWED" : "DENIED",
                profile.blockingReasons(),
                profile.imfChannelStatus(),
                profile.imfChannelReady()
        );
    }
}
