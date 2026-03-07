package com.ccapp.admin.service;

import com.ccapp.admin.dto.AdminPlatformUserResponse;
import com.ccapp.admin.dto.AdminUserDetailsResponse;
import com.ccapp.circles.repository.CircleRepository;
import com.ccapp.credit.domain.enums.LoanStatus;
import com.ccapp.credit.repository.LoanRepository;
import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.domain.enums.AccountStatus;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final LoanRepository loanRepository;
    private final CircleRepository circleRepository;

    @Transactional(readOnly = true)
    public List<AdminPlatformUserResponse> listUsers() {
        return accountHolderRepository.findAll().stream()
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminUserDetailsResponse getUser(UUID accountHolderId) {
        AccountHolder holder = accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        List<AppUser> appUsers = appUserRepository.findByAccountHolderId(accountHolderId);
        AccountStatus accountStatus = resolveAccountStatus(accountHolderId, appUsers);
        long activeLoans = loanRepository.countByAccountHolderIdAndStatusIn(
                accountHolderId,
                List.of(LoanStatus.APPROVED, LoanStatus.DISBURSED, LoanStatus.ACTIVE)
        );
        long activeCircles = circleRepository.findActiveCirclesForUser(accountHolderId).size();

        return new AdminUserDetailsResponse(
                holder.getId(),
                holder.getFirstName(),
                holder.getLastName(),
                holder.getPhoneNumber(),
                holder.getKycStatus(),
                accountStatus,
                appUsers.size(),
                activeLoans,
                activeCircles,
                holder.getCreatedAt()
        );
    }

    @Transactional
    public void blockUser(UUID accountHolderId) {
        accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        List<User> impactedUsers = loadLinkedLegacyUsers(accountHolderId);
        if (impactedUsers.isEmpty()) {
            throw new NotFoundException("No linked user found to block");
        }

        impactedUsers.forEach(user -> {
            user.setAccountStatus(AccountStatus.BLOCKED);
            user.setBlockedReason("Blocked by admin back office");
        });
        userRepository.saveAll(impactedUsers);
    }

    @Transactional
    public void unblockUser(UUID accountHolderId) {
        accountHolderRepository.findById(accountHolderId)
                .orElseThrow(() -> new NotFoundException("Account holder not found"));

        List<User> impactedUsers = loadLinkedLegacyUsers(accountHolderId);
        if (impactedUsers.isEmpty()) {
            throw new NotFoundException("No linked user found to unblock");
        }

        impactedUsers.forEach(user -> {
            user.setAccountStatus(AccountStatus.ACTIVE);
            user.setBlockedReason(null);
        });
        userRepository.saveAll(impactedUsers);
    }

    private AdminPlatformUserResponse toSummary(AccountHolder holder) {
        List<AppUser> appUsers = appUserRepository.findByAccountHolderId(holder.getId());
        AccountStatus accountStatus = resolveAccountStatus(holder.getId(), appUsers);

        return new AdminPlatformUserResponse(
                holder.getId(),
                holder.getFirstName(),
                holder.getLastName(),
                holder.getPhoneNumber(),
                holder.getKycStatus(),
                accountStatus,
                appUsers.size(),
                holder.getCreatedAt()
        );
    }

    private AccountStatus resolveAccountStatus(UUID accountHolderId, List<AppUser> appUsers) {
        return userRepository.findById(accountHolderId)
                .map(User::getAccountStatus)
                .orElseGet(() -> appUsers.stream()
                        .map(AppUser::getId)
                        .map(userRepository::findById)
                        .flatMap(Optional::stream)
                        .findFirst()
                        .map(User::getAccountStatus)
                        .orElse(AccountStatus.ACTIVE));
    }

    private List<User> loadLinkedLegacyUsers(UUID accountHolderId) {
        Set<UUID> userIds = new LinkedHashSet<>();
        userIds.add(accountHolderId);
        appUserRepository.findByAccountHolderId(accountHolderId).forEach(appUser -> userIds.add(appUser.getId()));

        List<User> users = new ArrayList<>();
        for (UUID userId : userIds) {
            userRepository.findById(userId).ifPresent(users::add);
        }
        return users;
    }
}
