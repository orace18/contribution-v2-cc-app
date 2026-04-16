package com.ccapp.devtools.service;

import com.ccapp.identity.account_holder.domain.entity.AccountHolder;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevUserResetService {

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final AppUserRepository appUserRepository;
    private final AccountHolderRepository accountHolderRepository;

    @Transactional
    public DevUserResetResult deleteUser(String email, UUID userId, UUID performedBy, String performedByEmail) {
        Optional<ResolvedUserTarget> targetOptional = resolveTarget(email, userId);
        OffsetDateTime timestamp = OffsetDateTime.now();
        String performedByValue = performedByEmail != null && !performedByEmail.isBlank()
                ? performedByEmail
                : String.valueOf(performedBy);

        if (targetOptional.isEmpty()) {
            log.warn(
                    "DEV_USER_DELETE executed userId={} email={} timestamp={} performedBy={} deleted={}",
                    userId,
                    email,
                    timestamp,
                    performedByValue,
                    false
            );
            return DevUserResetResult.alreadyDeleted(userId, email);
        }

        ResolvedUserTarget target = targetOptional.get();
        deleteRelatedData(target);

        log.warn(
                "DEV_USER_DELETE executed userId={} email={} timestamp={} performedBy={} deleted={}",
                target.primaryUserId(),
                target.email(),
                timestamp,
                performedByValue,
                true
        );
        return DevUserResetResult.deleted(target.primaryUserId(), target.accountHolderId(), target.email());
    }

    private Optional<ResolvedUserTarget> resolveTarget(String email, UUID requestedUserId) {
        if (email != null && !email.isBlank()) {
            return userRepository.findByEmailIgnoreCase(email.trim())
                    .flatMap(user -> resolveTargetFromAnyIdentifier(user.getId(), user.getEmail()));
        }
        if (requestedUserId == null) {
            return Optional.empty();
        }
        return resolveTargetFromAnyIdentifier(requestedUserId, null);
    }

    private Optional<ResolvedUserTarget> resolveTargetFromAnyIdentifier(UUID identifier, String preferredEmail) {
        Set<UUID> userIds = new LinkedHashSet<>();
        Set<UUID> appUserIds = new LinkedHashSet<>();
        UUID accountHolderId = null;
        String email = normalize(preferredEmail);

        User linkedUser = userRepository.findById(identifier).orElse(null);
        if (linkedUser != null) {
            userIds.add(linkedUser.getId());
            email = normalize(linkedUser.getEmail()) != null ? normalize(linkedUser.getEmail()) : email;
        }

        AppUser directAppUser = appUserRepository.findById(identifier).orElse(null);
        if (directAppUser != null) {
            appUserIds.add(directAppUser.getId());
            accountHolderId = directAppUser.getAccountHolderId();
        }

        AccountHolder directAccountHolder = accountHolderRepository.findById(identifier).orElse(null);
        if (directAccountHolder != null) {
            accountHolderId = directAccountHolder.getId();
        }

        if (accountHolderId == null && linkedUser != null && accountHolderRepository.existsById(linkedUser.getId())) {
            accountHolderId = linkedUser.getId();
        }

        if (accountHolderId == null) {
            return Optional.empty();
        }

        List<AppUser> linkedAppUsers = appUserRepository.findByAccountHolderId(accountHolderId);
        linkedAppUsers.forEach(appUser -> {
            appUserIds.add(appUser.getId());
            userIds.add(appUser.getId());
        });

        if (userIds.isEmpty() && userRepository.findById(accountHolderId).isPresent()) {
            userIds.add(accountHolderId);
        }

        for (UUID userId : userIds) {
            if (email == null) {
                email = userRepository.findById(userId)
                        .map(User::getEmail)
                        .map(this::normalize)
                        .orElse(null);
            }
        }

        UUID primaryUserId = !userIds.isEmpty()
                ? userIds.iterator().next()
                : (!appUserIds.isEmpty() ? appUserIds.iterator().next() : accountHolderId);

        return Optional.of(new ResolvedUserTarget(primaryUserId, accountHolderId, email, userIds, appUserIds));
    }

    private void deleteRelatedData(ResolvedUserTarget target) {
        entityManager.flush();

        List<UUID> walletIds = selectUuidList(
                "SELECT id FROM wallets WHERE account_holder_id = ?",
                target.accountHolderId()
        );

        deleteWalletScopedData(target.accountHolderId(), walletIds);
        deleteOperationScopedData(target.accountHolderId(), target.appUserIds(), walletIds);
        deleteIdentityScopedData(target);
        deleteReferralScopedData(target.accountHolderId());
        deleteSupportingScopedData(target.accountHolderId(), target.appUserIds());

        deleteByUuid("wallets", "account_holder_id", target.accountHolderId());
        deleteByUuids("app_users", "id", target.appUserIds());
        deleteByUuids("users", "id", target.userIds());
        deleteByUuid("account_holders", "id", target.accountHolderId());

        entityManager.clear();
    }

    private void deleteWalletScopedData(UUID accountHolderId, Collection<UUID> walletIds) {
        deleteByUuid("wallet_otp_challenges", "account_holder_id", accountHolderId);
        deleteByUuid("wallet_aml_alerts", "account_holder_id", accountHolderId);
        deleteByUuids("wallet_otp_challenges", "wallet_id", walletIds);
        deleteByUuids("wallet_aml_alerts", "wallet_id", walletIds);
        deleteByUuids("wallet_reservations", "wallet_id", walletIds);
        deleteByUuids("wallet_auto_debit_rules", "wallet_id", walletIds);
        deleteByUuids("wallet_ledger", "wallet_id", walletIds);
    }

    private void deleteOperationScopedData(UUID accountHolderId, Collection<UUID> appUserIds, Collection<UUID> walletIds) {
        deleteByUuid("memberships", "account_holder_id", accountHolderId);
        deleteBySql("DELETE FROM notification_attempts WHERE notification_id IN (SELECT id FROM notifications WHERE account_holder_id = ?)", accountHolderId);
        deleteByUuid("notifications", "account_holder_id", accountHolderId);
        deleteByUuid("mobile_plan_selections", "account_holder_id", accountHolderId);
        deleteByUuid("mobile_onboarding_journeys", "account_holder_id", accountHolderId);
        deleteByUuid("phone_verifications", "requester_account_holder_id", accountHolderId);
        deleteByUuid("user_tasks", "account_holder_id", accountHolderId);
        deleteByUuid("user_progress", "account_holder_id", accountHolderId);
        deleteByUuid("media_files", "account_holder_id", accountHolderId);
        deleteByUuid("kyc_documents", "account_holder_id", accountHolderId);
        deleteByUuid("credit_eligibility", "account_holder_id", accountHolderId);
        deleteByUuid("loans", "account_holder_id", accountHolderId);
        deleteByUuid("credit_applications", "account_holder_id", accountHolderId);
        deleteByUuid("loan_applications", "account_holder_id", accountHolderId);
        deleteByUuid("imf_savings_channels", "account_holder_id", accountHolderId);
        deleteByUuid("member_savings_contributions", "account_holder_id", accountHolderId);
        deleteByUuid("operations", "user_id", accountHolderId);
        deleteByUuid("operations", "destination_account_holder_id", accountHolderId);
        deleteByUuids("operations", "destination_wallet_id", walletIds);
        deleteChatScopedData(accountHolderId);
        for (UUID appUserId : appUserIds) {
            deleteByUuid("operations", "actor_user_id", appUserId);
            deleteBySql("DELETE FROM notification_attempts WHERE notification_id IN (SELECT id FROM notifications WHERE actor_user_id = ?)", appUserId);
            deleteByUuid("notifications", "actor_user_id", appUserId);
        }
    }

    private void deleteIdentityScopedData(ResolvedUserTarget target) {
        deleteByUuids("user_sessions", "user_id", target.userIds());
        deleteByUuids("user_profiles", "user_id", target.userIds());
        deleteByUuids("user_devices", "user_id", target.userIds());
    }

    private void deleteReferralScopedData(UUID accountHolderId) {
        deleteByUuid("mobile_onboarding_journeys", "sponsor_account_holder_id", accountHolderId);
        deleteByUuid("mlm_commissions", "beneficiary_account_holder_id", accountHolderId);
        deleteByUuid("mlm_volume_ledger", "account_holder_id", accountHolderId);
        deleteByUuid("mlm_volume_ledger", "source_member_id", accountHolderId);
        deleteByUuid("mlm_member_metrics", "account_holder_id", accountHolderId);
        deleteByUuid("mlm_member_grades", "member_id", accountHolderId);
        deleteByUuid("mlm_leg_carryover", "member_id", accountHolderId);
        deleteByUuid("mlm_leg_carryover", "placement_child_id", accountHolderId);
        deleteByUuid("mlm_weekly_snapshots", "account_holder_id", accountHolderId);
        deleteByUuid("mlm_relationships", "ancestor_id", accountHolderId);
        deleteByUuid("mlm_relationships", "descendant_id", accountHolderId);
        deleteByUuid("mlm_member_links", "account_holder_id", accountHolderId);
        deleteByUuid("mlm_member_links", "sponsor_account_holder_id", accountHolderId);
        deleteByUuid("mlm_member_links", "placement_account_holder_id", accountHolderId);
    }

    private void deleteSupportingScopedData(UUID accountHolderId, Collection<UUID> appUserIds) {
        deleteByUuid("account_delegations", "account_holder_id", accountHolderId);
        deleteByUuid("delegated_actions", "account_holder_id", accountHolderId);
        deleteByUuid("assisted_member_verifications", "account_holder_id", accountHolderId);
        deleteBySql("DELETE FROM partner_roles WHERE partner_user_id IN (SELECT id FROM partner_users WHERE account_holder_id = ?)", accountHolderId);
        deleteByUuid("partner_users", "account_holder_id", accountHolderId);
        deleteByUuid("assisted_sessions", "account_holder_id", accountHolderId);
        deleteByUuid("offline_transactions", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_amens", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_supports", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_comments", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_daily_completions", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_engagement_daily_checkins", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_daily_mission_progress", "account_holder_id", accountHolderId);
        deleteByUuid("prayer_posts", "author_account_holder_id", accountHolderId);
        for (UUID appUserId : appUserIds) {
            deleteBySql("UPDATE account_holders SET created_by_user_id = NULL WHERE created_by_user_id = ?", appUserId);
            deleteByUuid("agent_profiles", "app_user_id", appUserId);
            deleteByUuid("assisted_sessions", "agent_user_id", appUserId);
            deleteByUuid("offline_transactions", "agent_user_id", appUserId);
            deleteByUuid("account_delegations", "delegate_user_id", appUserId);
            deleteByUuid("delegated_actions", "actor_user_id", appUserId);
            deleteByUuid("assisted_member_verifications", "verified_by", appUserId);
            deleteByUuid("prayer_posts", "actor_user_id", appUserId);
            deleteByUuid("prayer_amens", "actor_user_id", appUserId);
            deleteByUuid("prayer_supports", "actor_user_id", appUserId);
            deleteByUuid("prayer_comments", "actor_user_id", appUserId);
            deleteByUuid("prayer_daily_completions", "actor_user_id", appUserId);
            deleteByUuid("prayer_engagement_daily_checkins", "actor_user_id", appUserId);
        }
    }

    private void deleteChatScopedData(UUID accountHolderId) {
        deleteByUuid("chat_read_receipts", "reader_account_holder_id", accountHolderId);
        deleteByUuid("chat_message_delivery_status", "recipient_account_holder_id", accountHolderId);
        deleteBySql(
                "DELETE FROM chat_read_receipts WHERE message_id IN (" +
                        "SELECT id FROM chat_messages WHERE sender_account_holder_id = ? OR recipient_account_holder_id = ?)",
                accountHolderId,
                accountHolderId
        );
        deleteBySql(
                "DELETE FROM chat_message_delivery_status WHERE message_id IN (" +
                        "SELECT id FROM chat_messages WHERE sender_account_holder_id = ? OR recipient_account_holder_id = ?)",
                accountHolderId,
                accountHolderId
        );
        deleteBySql(
                "DELETE FROM chat_messages WHERE sender_account_holder_id = ? OR recipient_account_holder_id = ?",
                accountHolderId,
                accountHolderId
        );
        deleteBySql(
                "DELETE FROM chat_conversations WHERE participant_a_account_holder_id = ? OR participant_b_account_holder_id = ?",
                accountHolderId,
                accountHolderId
        );
    }

    private List<UUID> selectUuidList(String sql, UUID value) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getObject(1, UUID.class), value);
    }

    private void deleteByUuids(String table, String column, Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (UUID value : values) {
            deleteByUuid(table, column, value);
        }
    }

    private void deleteByUuid(String table, String column, UUID value) {
        if (value == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE " + column + " = ?", value);
    }

    private void deleteBySql(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ResolvedUserTarget(
            UUID primaryUserId,
            UUID accountHolderId,
            String email,
            Set<UUID> userIds,
            Set<UUID> appUserIds
    ) {
    }

    public record DevUserResetResult(
            boolean deleted,
            UUID userId,
            UUID accountHolderId,
            String email
    ) {
        public static DevUserResetResult deleted(UUID userId, UUID accountHolderId, String email) {
            return new DevUserResetResult(true, userId, accountHolderId, email);
        }

        public static DevUserResetResult alreadyDeleted(UUID userId, String email) {
            return new DevUserResetResult(false, userId, null, email);
        }
    }
}
