package com.ccapp.identity.managedaccounts.service;

import com.ccapp.common.exception.BusinessException;
import com.ccapp.common.exception.UnauthorizedException;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.domain.enums.AccountType;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.account_holder.service.AccountHolderService;
import com.ccapp.identity.delegation.domain.entity.AccountDelegation;
import com.ccapp.identity.delegation.domain.enums.DelegatedActionType;
import com.ccapp.identity.delegation.domain.enums.PermissionLevel;
import com.ccapp.identity.delegation.service.AccessControlService;
import com.ccapp.identity.delegation.service.DelegatedActionService;
import com.ccapp.identity.delegation.service.DelegationService;
import com.ccapp.identity.managedaccounts.dto.CreateManagedAccountRequest;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountDetailResponse;
import com.ccapp.identity.managedaccounts.dto.ManagedAccountSummaryResponse;
import com.ccapp.identity.managedaccounts.dto.UpdateManagedAccountRequest;
import com.ccapp.identity.verification.service.PhoneVerificationService;
import com.ccapp.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagedAccountsService {

    private static final String MANAGED_ACCOUNT_REQUIRED = "MANAGED_ACCOUNT_REQUIRED";

    private final AccountHolderService accountHolderService;
    private final AccountHolderRepository accountHolderRepository;
    private final DelegationService delegationService;
    private final AccessControlService accessControlService;
    private final PhoneVerificationService phoneVerificationService;
    private final NotificationService notificationService;
    private final DelegatedActionService delegatedActionService;

    @Transactional(readOnly = true)
    public List<ManagedAccountSummaryResponse> listManagedAccounts(UUID actorUserId) {
        log.info("Managed accounts list actorUserId={}", actorUserId);
        List<AccountDelegation> deduplicatedDelegations = delegationService.getActiveDelegationsForDelegate(actorUserId)
                .stream()
                .sorted(Comparator.comparing(AccountDelegation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(
                        LinkedHashMap<UUID, AccountDelegation>::new,
                        (map, delegation) -> map.putIfAbsent(delegation.getAccountHolderId(), delegation),
                        Map::putAll
                )
                .values()
                .stream()
                .toList();

        Map<UUID, AccountHolder> holdersById = accountHolderRepository.findAllById(
                        deduplicatedDelegations.stream()
                                .map(AccountDelegation::getAccountHolderId)
                                .toList()
                ).stream()
                .collect(Collectors.toMap(AccountHolder::getId, holder -> holder));

        return deduplicatedDelegations.stream()
                .map(delegation -> {
                    AccountHolder accountHolder = holdersById.get(delegation.getAccountHolderId());
                    if (accountHolder == null || accountHolder.getAccountType() != AccountType.ASSISTED) {
                        return null;
                    }
                    return toSummary(accountHolder, delegation);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public ManagedAccountDetailResponse getManagedAccountDetail(UUID actorUserId, UUID accountHolderId) {
        log.info("Managed accounts detail actorUserId={} accountHolderId={}", actorUserId, accountHolderId);
        ManagedAccountContext context = requireManagedAccountAccess(actorUserId, accountHolderId, PermissionLevel.VIEW);
        return toDetail(context.accountHolder(), context.delegation());
    }

    @Transactional
    public ManagedAccountDetailResponse createManagedAccount(UUID actorUserId,
                                                             UUID actorAccountHolderId,
                                                             CreateManagedAccountRequest request) {
        log.info("Managed accounts create actorUserId={} actorAccountHolderId={}", actorUserId, actorAccountHolderId);
        phoneVerificationService.assertVerifiedForPhone(
                actorAccountHolderId,
                request.phoneNumber(),
                request.phoneVerificationId()
        );

        AccountHolder created = accountHolderService.createManagedAccount(
                actorUserId,
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                request.countryCode()
        );
        delegationService.deactivateActiveDelegations(created.getId());
        AccountDelegation delegation = delegationService.createManagerDelegation(created.getId(), actorUserId);
        return toDetail(created, delegation);
    }

    @Transactional
    public ManagedAccountDetailResponse updateManagedAccount(UUID actorUserId,
                                                             UUID actorAccountHolderId,
                                                             UUID accountHolderId,
                                                             UpdateManagedAccountRequest request) {
        log.info("Managed accounts update actorUserId={} actorAccountHolderId={} accountHolderId={}",
                actorUserId, actorAccountHolderId, accountHolderId);
        ManagedAccountContext context = requireManagedAccountAccess(actorUserId, accountHolderId, PermissionLevel.FULL);
        if (isPhoneChanged(context.accountHolder(), request.phoneNumber())) {
            phoneVerificationService.assertVerifiedForPhone(
                    actorAccountHolderId,
                    request.phoneNumber(),
                    request.phoneVerificationId()
            );
        }

        AccountHolder saved = accountHolderService.updateProfile(
                accountHolderId,
                request.firstName(),
                request.lastName(),
                request.phoneNumber(),
                request.countryCode()
        );
        delegatedActionService.log(
                accountHolderId,
                actorUserId,
                DelegatedActionType.PROFILE_UPDATED,
                "managed_account_profile_updated"
        );
        return toDetail(saved, context.delegation());
    }

    @Transactional
    public void revokeManagedAccount(UUID actorUserId, UUID accountHolderId) {
        log.info("Managed accounts revoke actorUserId={} accountHolderId={}", actorUserId, accountHolderId);
        requireManagedAccountAccess(actorUserId, accountHolderId, PermissionLevel.FULL);
        delegationService.revokeActiveManagerDelegation(accountHolderId, actorUserId);
    }

    private ManagedAccountContext requireManagedAccountAccess(UUID actorUserId,
                                                              UUID accountHolderId,
                                                              PermissionLevel requiredPermission) {
        AccountHolder accountHolder = accountHolderService.getById(accountHolderId);
        if (accountHolder.getAccountType() != AccountType.ASSISTED) {
            throw new BusinessException(MANAGED_ACCOUNT_REQUIRED);
        }
        AccountDelegation delegation = delegationService.findActiveManagerDelegation(accountHolderId, actorUserId)
                .orElseThrow(() -> new UnauthorizedException("Managed account access denied"));
        accessControlService.assertCanActOnAccountHolder(actorUserId, accountHolderId, requiredPermission);
        return new ManagedAccountContext(accountHolder, delegation);
    }

    private ManagedAccountSummaryResponse toSummary(AccountHolder accountHolder, AccountDelegation delegation) {
        return new ManagedAccountSummaryResponse(
                accountHolder.getId(),
                displayName(accountHolder),
                accountHolder.getPhoneNumber(),
                accountHolder.getCountryCode(),
                accountHolder.getServiceCountryCode(),
                accountHolder.getAccountType(),
                accountHolder.getKycStatus(),
                accountHolder.resolveKycLevel(),
                accountHolder.getPhoneVerifiedAt() != null,
                delegation.getPermissionLevel(),
                notificationService.getUnreadCount(accountHolder.getId()),
                delegation.getCreatedAt(),
                accountHolder.getCreatedAt()
        );
    }

    private ManagedAccountDetailResponse toDetail(AccountHolder accountHolder, AccountDelegation delegation) {
        return new ManagedAccountDetailResponse(
                accountHolder.getId(),
                accountHolder.getFirstName(),
                accountHolder.getLastName(),
                displayName(accountHolder),
                accountHolder.getPhoneNumber(),
                accountHolder.getCountryCode(),
                accountHolder.getServiceCountryCode(),
                accountHolder.getAccountType(),
                accountHolder.getCreatedVia(),
                accountHolder.getCreatedByUserId(),
                delegation.getDelegateUserId(),
                delegation.getPermissionLevel(),
                accountHolder.getKycStatus(),
                accountHolder.resolveKycLevel(),
                accountHolder.getPhoneVerifiedAt() != null,
                notificationService.getUnreadCount(accountHolder.getId()),
                delegation.getCreatedAt(),
                accountHolder.getCreatedAt()
        );
    }

    private boolean isPhoneChanged(AccountHolder accountHolder, String requestedPhoneNumber) {
        String currentPhone = accountHolder.getPhoneNumber() == null ? null : accountHolder.getPhoneNumber().trim();
        String requestedPhone = requestedPhoneNumber == null ? null : requestedPhoneNumber.trim();
        return !Objects.equals(currentPhone, requestedPhone);
    }

    private String displayName(AccountHolder accountHolder) {
        String firstName = accountHolder.getFirstName() == null ? "" : accountHolder.getFirstName().trim();
        String lastName = accountHolder.getLastName() == null ? "" : accountHolder.getLastName().trim();
        String displayName = (firstName + " " + lastName).trim();
        return displayName.isBlank() ? "Unknown" : displayName;
    }

    private record ManagedAccountContext(AccountHolder accountHolder, AccountDelegation delegation) {
    }
}
