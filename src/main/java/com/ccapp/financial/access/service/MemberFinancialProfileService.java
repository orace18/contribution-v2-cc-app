package com.ccapp.financial.access.service;

import com.ccapp.country.dto.CountryContext;
import com.ccapp.country.service.CountryContextService;
import com.ccapp.financial.access.domain.FinancialAccessReasonCode;
import com.ccapp.financial.access.domain.FinancialAccessState;
import com.ccapp.financial.access.domain.MemberFinancialProfile;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.wallet.domain.entity.Wallet;
import com.ccapp.identity.wallet.repository.WalletRepository;
import com.ccapp.payments.subscription.domain.entity.Membership;
import com.ccapp.payments.subscription.domain.enums.MembershipStatus;
import com.ccapp.payments.subscription.service.MembershipService;
import com.ccapp.seedcapital.savings.domain.entity.ImfSavingsChannel;
import com.ccapp.seedcapital.savings.domain.enums.ImfSavingsChannelStatus;
import com.ccapp.seedcapital.savings.repository.ImfSavingsChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberFinancialProfileService {

    private static final String ACTIVE_WALLET_STATUS = "ACTIVE";
    private static final String IMF_CHANNEL_NOT_CONFIGURED = "NOT_CONFIGURED";

    private final AccountHolderService accountHolderService;
    private final CountryContextService countryContextService;
    private final MembershipService membershipService;
    private final WalletRepository walletRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final ImfSavingsChannelRepository imfSavingsChannelRepository;

    @Transactional(readOnly = true)
    public MemberFinancialProfile getProfile(UUID accountHolderId) {
        accountHolderService.getById(accountHolderId);

        OffsetDateTime now = OffsetDateTime.now();
        User linkedUser = resolveLinkedUser(accountHolderId);
        Membership latestMembership = membershipService.findLatestMembership(accountHolderId).orElse(null);
        Wallet latestWallet = walletRepository.findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)
                .stream()
                .findFirst()
                .orElse(null);
        CountryContext countryContext = countryContextService.resolve(accountHolderId);
        ImfSavingsChannel imfSavingsChannel = imfSavingsChannelRepository
                .findFirstByAccountHolderIdAndCountryCodeOrderByUpdatedAtDesc(accountHolderId, countryContext.countryCode())
                .orElse(null);
        boolean walletActive = walletRepository
                .findFirstByAccountHolderIdAndStatusOrderByCreatedAtDesc(accountHolderId, ACTIVE_WALLET_STATUS)
                .isPresent();

        List<FinancialAccessReasonCode> blockingReasons = new ArrayList<>();
        boolean accountRestricted = appendAccountRestrictionReasons(linkedUser, blockingReasons);
        boolean membershipActive = appendMembershipReasons(latestMembership, now, blockingReasons);

        return new MemberFinancialProfile(
                accountHolderId,
                linkedUser != null ? linkedUser.getAccountStatus() : null,
                linkedUser != null ? linkedUser.getBlockedReason() : null,
                latestMembership != null ? latestMembership.getStatus() : null,
                latestMembership != null ? latestMembership.getExpiresAt() : null,
                membershipActive,
                latestWallet != null ? latestWallet.getStatus() : null,
                walletActive,
                accountRestricted,
                blockingReasons.isEmpty() ? FinancialAccessState.FINANCIAL_ALLOWED : FinancialAccessState.FINANCIAL_DENIED,
                imfSavingsChannel != null && imfSavingsChannel.getStatus() == ImfSavingsChannelStatus.ACTIVE,
                imfSavingsChannel != null ? imfSavingsChannel.getStatus().name() : IMF_CHANNEL_NOT_CONFIGURED,
                blockingReasons
        );
    }

    private User resolveLinkedUser(UUID accountHolderId) {
        AppUser appUser = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId).orElse(null);
        if (appUser == null) {
            return null;
        }
        return userRepository.findById(appUser.getId()).orElse(null);
    }

    private boolean appendAccountRestrictionReasons(User linkedUser, List<FinancialAccessReasonCode> blockingReasons) {
        if (linkedUser == null || linkedUser.getAccountStatus() == null || linkedUser.getAccountStatus() == AccountStatus.ACTIVE) {
            return false;
        }
        if (linkedUser.getAccountStatus() == AccountStatus.BLOCKED) {
            blockingReasons.add(FinancialAccessReasonCode.ACCOUNT_BLOCKED);
            return true;
        }
        if (linkedUser.getAccountStatus() == AccountStatus.SUSPENDED) {
            blockingReasons.add(FinancialAccessReasonCode.ACCOUNT_SUSPENDED);
            return true;
        }
        return true;
    }

    private boolean appendMembershipReasons(Membership membership,
                                            OffsetDateTime now,
                                            List<FinancialAccessReasonCode> blockingReasons) {
        if (membership == null) {
            blockingReasons.add(FinancialAccessReasonCode.MEMBERSHIP_MISSING);
            return false;
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            if (membership.getStatus() == MembershipStatus.EXPIRED) {
                blockingReasons.add(FinancialAccessReasonCode.MEMBERSHIP_EXPIRED);
            } else {
                blockingReasons.add(FinancialAccessReasonCode.MEMBERSHIP_NOT_ACTIVE);
            }
            return false;
        }

        if (membership.getExpiresAt() != null && !membership.getExpiresAt().isAfter(now)) {
            blockingReasons.add(FinancialAccessReasonCode.MEMBERSHIP_EXPIRED);
            return false;
        }

        return true;
    }
}
