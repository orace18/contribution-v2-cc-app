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
import java.util.ArrayList;
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
        disableImmutableTriggers();

        UUID ah = target.accountHolderId();
        Collection<UUID> appUserIds = target.appUserIds();

        List<UUID> walletIds = selectUuidList("SELECT id FROM wallets WHERE account_holder_id = ?", ah);

        // 1. Wallet-scoped data (before wallets & operations)
        deleteWalletScopedData(ah, walletIds);

        // 2. Pre-operation FK dependents — must run before operations are deleted
        deleteOperationFkDependents(ah, appUserIds, walletIds);

        // 3. Operation-scoped data (includes operation rows themselves)
        deleteOperationScopedData(ah, appUserIds, walletIds);

        // 4. Shop-scoped data (shop_orders as buyer, shop_products as merchant)
        deleteShopScopedData(ah);

        // 5. Chat-scoped data
        deleteChatScopedData(ah);

        // 6. Identity-scoped data
        deleteIdentityScopedData(target);

        // 7. Event-scoped data
        deleteEventScopedData(ah);

        // 8. Commerce-scoped data
        deleteCommerceScopedData(ah);

        // 9. Referral / MLM-scoped data
        deleteReferralScopedData(ah);

        // 10. Supporting-scoped data
        deleteSupportingScopedData(ah, appUserIds);

        // 10.5 Media files (after chat, identity, prayer, kyc are cleared)
        deleteByUuid("media_files", "account_holder_id", ah);

        // 11. Core entities
        deleteByUuid("wallets", "account_holder_id", ah);
        deleteByUuids("app_users", "id", appUserIds);
        deleteByUuids("users", "id", target.userIds());
        deleteByUuid("account_holders", "id", ah);

        enableImmutableTriggers();
        entityManager.clear();
    }

    // ── Wallet scope ────────────────────────────────────────────────────────────

    private void deleteWalletScopedData(UUID accountHolderId, Collection<UUID> walletIds) {
        deleteByUuid("wallet_otp_challenges", "account_holder_id", accountHolderId);
        deleteByUuid("wallet_aml_alerts", "account_holder_id", accountHolderId);
        deleteByUuids("wallet_otp_challenges", "wallet_id", walletIds);
        deleteByUuids("wallet_aml_alerts", "wallet_id", walletIds);
        deleteByUuids("wallet_reservations", "wallet_id", walletIds);
        deleteByUuids("wallet_auto_debit_rules", "wallet_id", walletIds);
        deleteByUuids("wallet_ledger", "wallet_id", walletIds);
    }

    // ── Pre-operation FK dependents ─────────────────────────────────────────────
    // All tables with FK → operations must be cleared BEFORE operations are deleted.

    private void deleteOperationFkDependents(UUID accountHolderId, Collection<UUID> appUserIds, Collection<UUID> walletIds) {
        List<UUID> opIds = new ArrayList<>();
        opIds.addAll(selectUuidList("SELECT id FROM operations WHERE user_id = ?", accountHolderId));
        opIds.addAll(selectUuidList("SELECT id FROM operations WHERE destination_account_holder_id = ?", accountHolderId));
        for (UUID walletId : walletIds) {
            opIds.addAll(selectUuidList("SELECT id FROM operations WHERE destination_wallet_id = ?", walletId));
        }
        for (UUID appUserId : appUserIds) {
            opIds.addAll(selectUuidList("SELECT id FROM operations WHERE actor_user_id = ?", appUserId));
        }
        List<UUID> uniqueOpIds = new ArrayList<>(new LinkedHashSet<>(opIds));
        if (uniqueOpIds.isEmpty()) return;

        // ledger_entries → accounting_events → operations
        for (UUID opId : uniqueOpIds) {
            deleteBySql("DELETE FROM ledger_entries WHERE event_id IN (SELECT id FROM accounting_events WHERE operation_id = ?)", opId);
        }
        deleteByUuids("accounting_events", "operation_id", uniqueOpIds);

        // commerce_dsb_payouts (operation_id and reversal_operation_id → operations)
        deleteByUuids("commerce_dsb_payouts", "operation_id", uniqueOpIds);
        deleteByUuids("commerce_dsb_payouts", "reversal_operation_id", uniqueOpIds);

        // mlm_commissions.operation_id → operations
        // Break bidirectional cycle: commission_payout_items.commission_id ↔ mlm_commissions.payout_item_id
        for (UUID opId : uniqueOpIds) {
            deleteBySql(
                    "UPDATE commission_payout_items SET commission_id = NULL WHERE commission_id IN (SELECT id FROM mlm_commissions WHERE operation_id = ?)",
                    opId
            );
        }
        deleteByUuids("mlm_commissions", "operation_id", uniqueOpIds);

        // commission_payout_items.operation_id → operations
        // Break cycle: mlm_commissions.payout_item_id → commission_payout_items
        for (UUID opId : uniqueOpIds) {
            deleteBySql(
                    "UPDATE mlm_commissions SET payout_item_id = NULL WHERE payout_item_id IN (SELECT id FROM commission_payout_items WHERE operation_id = ?)",
                    opId
            );
        }
        deleteByUuids("commission_payout_items", "operation_id", uniqueOpIds);


    }

    // ── Operation scope ─────────────────────────────────────────────────────────

    private void deleteOperationScopedData(UUID accountHolderId, Collection<UUID> appUserIds, Collection<UUID> walletIds) {
        deleteByUuid("memberships", "account_holder_id", accountHolderId);
        deleteBySql("DELETE FROM notification_attempts WHERE notification_id IN (SELECT id FROM notifications WHERE account_holder_id = ?)", accountHolderId);
        deleteByUuid("notifications", "account_holder_id", accountHolderId);
        deleteByUuid("mobile_plan_selections", "account_holder_id", accountHolderId);
        deleteByUuid("mobile_onboarding_journeys", "account_holder_id", accountHolderId);
        deleteByUuid("phone_verifications", "requester_account_holder_id", accountHolderId);
        deleteBySql("DELETE FROM task_audit_logs WHERE user_task_id IN (SELECT id FROM user_tasks WHERE account_holder_id = ?)", accountHolderId);
        deleteByUuid("user_tasks", "account_holder_id", accountHolderId);
        deleteByUuid("user_progress", "account_holder_id", accountHolderId);
        deleteByUuid("kyc_documents", "account_holder_id", accountHolderId);
        deleteByUuid("credit_eligibility", "account_holder_id", accountHolderId);
        deleteByUuid("loans", "account_holder_id", accountHolderId);
        deleteByUuid("credit_applications", "account_holder_id", accountHolderId);
        deleteByUuid("loan_applications", "account_holder_id", accountHolderId);
        deleteByUuid("imf_savings_channels", "account_holder_id", accountHolderId);
        deleteByUuid("member_savings_contributions", "account_holder_id", accountHolderId);
        deleteByUuid("savings_cycles", "created_by_account_holder_id", accountHolderId);
        deleteByUuid("institution_ledger_entries", "account_holder_id", accountHolderId);
        deleteByUuid("operations", "user_id", accountHolderId);
        deleteByUuid("operations", "destination_account_holder_id", accountHolderId);
        deleteByUuids("operations", "destination_wallet_id", walletIds);
        for (UUID appUserId : appUserIds) {
            deleteByUuid("operations", "actor_user_id", appUserId);
            deleteBySql("DELETE FROM notification_attempts WHERE notification_id IN (SELECT id FROM notifications WHERE actor_user_id = ?)", appUserId);
            deleteByUuid("notifications", "actor_user_id", appUserId);
        }
    }

    // ── Shop scope ──────────────────────────────────────────────────────────────

    private void deleteShopScopedData(UUID accountHolderId) {
        // Before deleting shop_orders: clear non-cascade FK refs
        deleteBySql("DELETE FROM mlm_volume_ledger WHERE source_order_id IN (SELECT id FROM shop_orders WHERE buyer_id = ?)", accountHolderId);
        deleteBySql("DELETE FROM commission_adjustments WHERE reference_order_id IN (SELECT id FROM shop_orders WHERE buyer_id = ?)", accountHolderId);

        // Delete buyer's orders (CASCADE: shop_order_items, shop_commission_pool, commerce_orders → dsb_payouts, event_tickets)
        deleteByUuid("shop_orders", "buyer_id", accountHolderId);

        // For products this user owns as merchant: clear dependents before deleting products
        deleteBySql("DELETE FROM shop_order_items WHERE product_id IN (SELECT id FROM shop_products WHERE merchant_id = ?)", accountHolderId);
        // event_store_sync_jobs CASCADE from event_ticket_types, but event_ticket_types must be deleted first
        deleteBySql("DELETE FROM event_store_sync_jobs WHERE ticket_type_id IN (SELECT id FROM event_ticket_types WHERE shop_product_id IN (SELECT id FROM shop_products WHERE merchant_id = ?))", accountHolderId);
        deleteBySql("DELETE FROM event_ticket_types WHERE shop_product_id IN (SELECT id FROM shop_products WHERE merchant_id = ?)", accountHolderId);
        // commerce_products (commerce_product_listings) CASCADE from shop_products, but explicit is safer
        deleteBySql("DELETE FROM commerce_products WHERE product_id IN (SELECT id FROM shop_products WHERE merchant_id = ?)", accountHolderId);
        deleteByUuid("shop_products", "merchant_id", accountHolderId);
    }

    // ── Chat scope ──────────────────────────────────────────────────────────────

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

    // ── Identity scope ───────────────────────────────────────────────────────────

    private void deleteIdentityScopedData(ResolvedUserTarget target) {
        deleteByUuids("user_sessions", "user_id", target.userIds());
        deleteByUuids("user_profiles", "user_id", target.userIds());
        deleteByUuids("user_devices", "user_id", target.userIds());
    }

    // ── Event scope ──────────────────────────────────────────────────────────────

    private void deleteEventScopedData(UUID accountHolderId) {
        // event_checkin_attempts has NO CASCADE — must delete before events and event_tickets
        deleteBySql(
                "DELETE FROM event_checkin_attempts WHERE event_id IN (SELECT id FROM events WHERE organizer_account_holder_id = ?)",
                accountHolderId
        );
        deleteBySql(
                "DELETE FROM event_checkin_attempts WHERE ticket_id IN (SELECT id FROM event_tickets WHERE buyer_account_holder_id = ?)",
                accountHolderId
        );

        // Tickets and assignments as attendee (CASCADE from event_id would handle them when event deleted,
        // but buyer/account_holder FK requires explicit deletion first)
        deleteByUuid("event_tickets", "buyer_account_holder_id", accountHolderId);
        deleteByUuid("event_assignments", "account_holder_id", accountHolderId);

        // Delete organized events (CASCADE: event_speakers, event_ticket_types → event_store_sync_jobs, event_assignments, event_tickets)
        deleteByUuid("events", "organizer_account_holder_id", accountHolderId);
    }

    // ── Commerce scope ───────────────────────────────────────────────────────────

    private void deleteCommerceScopedData(UUID accountHolderId) {
        // commerce_dsb_payouts by beneficiary (operation_id-based ones already cleared in pre-operation step)
        deleteByUuid("commerce_dsb_payouts", "beneficiary_account_holder_id", accountHolderId);
        deleteBySql("DELETE FROM commerce_orders WHERE buyer_account_holder_id = ? OR merchant_account_holder_id = ?", accountHolderId, accountHolderId);
        deleteByUuid("commerce_promo_codes", "owner_account_holder_id", accountHolderId);
        deleteByUuid("commerce_merchants", "account_holder_id", accountHolderId);
    }

    // ── Referral / MLM scope ─────────────────────────────────────────────────────

    private void deleteReferralScopedData(UUID accountHolderId) {
        deleteByUuid("mobile_onboarding_journeys", "sponsor_account_holder_id", accountHolderId);

        // mlm_commissions by beneficiary — break bidirectional cycle first
        deleteBySql(
                "UPDATE commission_payout_items SET commission_id = NULL WHERE commission_id IN (SELECT id FROM mlm_commissions WHERE beneficiary_account_holder_id = ?)",
                accountHolderId
        );
        deleteBySql(
                "UPDATE mlm_commissions SET payout_item_id = NULL WHERE beneficiary_account_holder_id = ?",
                accountHolderId
        );
        deleteByUuid("mlm_commissions", "beneficiary_account_holder_id", accountHolderId);

        // commission_payout_items by account_holder (payout_item_id FK already NULLed above)
        deleteByUuid("commission_payout_items", "account_holder_id", accountHolderId);

        // commission_adjustments
        deleteByUuid("commission_adjustments", "account_holder_id", accountHolderId);

        // financial_audit_log
        deleteByUuid("financial_audit_log", "account_holder_id", accountHolderId);

        // mlm_placement_audit_logs (three FK columns)
        deleteByUuid("mlm_placement_audit_logs", "account_holder_id", accountHolderId);
        deleteByUuid("mlm_placement_audit_logs", "sponsor_account_holder_id", accountHolderId);
        deleteByUuid("mlm_placement_audit_logs", "placement_parent_id", accountHolderId);

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

    // ── Supporting scope ─────────────────────────────────────────────────────────

    private void deleteSupportingScopedData(UUID accountHolderId, Collection<UUID> appUserIds) {
        deleteByUuid("circle_invites", "created_by_account_holder_id", accountHolderId);
        deleteByUuid("circle_member_interactions", "member_id", accountHolderId);
        deleteByUuid("circle_member_interactions", "sender_id", accountHolderId);
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

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void disableImmutableTriggers() {
        jdbcTemplate.execute("ALTER TABLE ledger_entries DISABLE TRIGGER trg_no_mutation_ledger_entries");
        jdbcTemplate.execute("ALTER TABLE accounting_events DISABLE TRIGGER trg_no_mutation_accounting_events");
    }

    private void enableImmutableTriggers() {
        jdbcTemplate.execute("ALTER TABLE ledger_entries ENABLE TRIGGER trg_no_mutation_ledger_entries");
        jdbcTemplate.execute("ALTER TABLE accounting_events ENABLE TRIGGER trg_no_mutation_accounting_events");
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
