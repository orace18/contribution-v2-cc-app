package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevDomainResetResponse;
import com.ccapp.devtools.dto.TargetedResetRequest;
import com.ccapp.identity.account_holder.repository.AccountHolderRepository;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.domain.entity.User;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.mlm.graph.MlmGraphNeo4jGateway;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DevTargetedUserResetService {

    private static final String KYC = "KYC";
    private static final String MLM = "MLM";
    private static final String PHONE_VERIFICATION = "PHONE_VERIFICATION";
    private static final String CIRCLE = "CIRCLE";

    private static final List<String> KYC_TASK_TYPES = List.of("COMPLETE_KYC", "KYC_COMPLETED");
    private static final List<String> PHONE_TASK_TYPES = List.of("VERIFY_PHONE", "PHONE_VERIFIED");
    private static final List<String> CIRCLE_TASK_TYPES = List.of(
            "JOIN_CIRCLE",
            "CIRCLE_JOINED",
            "MAKE_FIRST_CONTRIBUTION",
            "INVITE_FRIENDS",
            "INVITE_MEMBER"
    );

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;
    private final AccountHolderRepository accountHolderRepository;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;
    private final ObjectProvider<MlmGraphNeo4jGateway> graphGatewayProvider;

    @Transactional
    public DevDomainResetResponse resetKyc(TargetedResetRequest request, UUID performedBy, String performedByEmail) {
        request.validate();
        OffsetDateTime resetAt = OffsetDateTime.now();
        Optional<ResolvedResetTarget> targetOptional = resolveTarget(request);
        if (targetOptional.isEmpty()) {
            return notApplicable(KYC, request, resetAt, "Reset not applicable: target account holder was not found");
        }

        entityManager.flush();
        ResolvedResetTarget target = targetOptional.get();
        UUID accountHolderId = target.accountHolderId();

        Set<UUID> kycMediaIds = new LinkedHashSet<>();
        kycMediaIds.addAll(selectUuidList(
                "SELECT media_file_id FROM kyc_documents WHERE account_holder_id = ?",
                accountHolderId
        ));
        kycMediaIds.addAll(selectUuidList(
                "SELECT id FROM media_files WHERE account_holder_id = ? " +
                        "AND type IN ('KYC_IDENTITY_FRONT', 'KYC_IDENTITY_BACK', 'KYC_SELFIE')",
                accountHolderId
        ));
        List<UUID> identityVerificationIds = selectUuidList(
                "SELECT id FROM identity_verifications WHERE account_holder_id = ?",
                accountHolderId
        );

        int verificationAnalysisDeleted = deleteByUuids("verification_analysis", "verification_id", identityVerificationIds);
        int verificationDocumentsDeleted = deleteByUuids("verification_documents", "verification_id", identityVerificationIds);
        int identityVerificationsDeleted = deleteByUuid("identity_verifications", "account_holder_id", accountHolderId);
        int mediaBackupsDeleted = deleteByUuids("kyc_media_backups", "media_file_id", kycMediaIds);
        int documentsDeleted = deleteByUuid("kyc_documents", "account_holder_id", accountHolderId);
        int mediaFilesDeleted = deleteByUuids("media_files", "id", kycMediaIds);
        int accountHolderRowsUpdated = updateBySql(
                "UPDATE account_holders SET " +
                        "kyc_status = 'NOT_STARTED', " +
                        "kyc_level = 'LEVEL_0_UNVERIFIED', " +
                        "kyc_verified_at = NULL, " +
                        "birth_date = NULL, " +
                        "document_number = NULL, " +
                        "nationality = NULL, " +
                        "declared_document_type = NULL, " +
                        "cpass_case_id = NULL " +
                        "WHERE id = ?",
                accountHolderId
        );
        int legacyUserRowsUpdated = target.userId() == null
                ? 0
                : updateBySql("UPDATE users SET kyc_status = 'NOT_STARTED' WHERE id = ?", target.userId());
        int financialAuditRowsDeleted = deleteBySql(
                "DELETE FROM financial_audit_log WHERE account_holder_id = ? " +
                        "AND event_type IN ('KYC_LEVEL_1_AUTO_APPROVED', 'KYC_VERIFIED', 'KYC_REJECTED')",
                accountHolderId
        );
        TaskResetCounts taskCounts = deleteTasks(accountHolderId, KYC_TASK_TYPES);

        Map<String, Object> summary = summary();
        summary.put("accountHolderRowsUpdated", accountHolderRowsUpdated);
        summary.put("legacyUserRowsUpdated", legacyUserRowsUpdated);
        summary.put("kycDocumentsDeleted", documentsDeleted);
        summary.put("kycMediaBackupsDeleted", mediaBackupsDeleted);
        summary.put("kycMediaFilesDeleted", mediaFilesDeleted);
        summary.put("identityVerificationsDeleted", identityVerificationsDeleted);
        summary.put("verificationDocumentsDeleted", verificationDocumentsDeleted);
        summary.put("verificationAnalysisDeleted", verificationAnalysisDeleted);
        summary.put("financialAuditRowsDeleted", financialAuditRowsDeleted);
        summary.put("userTasksDeleted", taskCounts.userTasksDeleted());
        summary.put("taskAuditLogsDeleted", taskCounts.taskAuditLogsDeleted());
        summary.put("preservedDomains", List.of("account", "phone", "mlm", "circle", "wallet"));

        entityManager.clear();
        logReset(KYC, target, resetAt, performedBy, performedByEmail, true, summary);
        return applied(KYC, target, resetAt, "KYC reset completed", summary, List.of());
    }

    @Transactional
    public DevDomainResetResponse resetMlm(TargetedResetRequest request, UUID performedBy, String performedByEmail) {
        request.validate();
        OffsetDateTime resetAt = OffsetDateTime.now();
        Optional<ResolvedResetTarget> targetOptional = resolveTarget(request);
        if (targetOptional.isEmpty()) {
            return notApplicable(MLM, request, resetAt, "Reset not applicable: target account holder was not found");
        }

        entityManager.flush();
        ResolvedResetTarget target = targetOptional.get();
        UUID accountHolderId = target.accountHolderId();
        Map<String, Object> guardSummary = mlmDependencySummary(accountHolderId);
        boolean hasDependentNetwork = guardSummary.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .anyMatch(value -> value.longValue() > 0);
        if (hasDependentNetwork) {
            guardSummary.put("reason", "Target still sponsors, places, or is referenced by other users");
            logReset(MLM, target, resetAt, performedBy, performedByEmail, false, guardSummary);
            return notApplied(MLM, target, resetAt,
                    "Reset not applicable: target is still referenced by other network users",
                    guardSummary,
                    List.of("Remove or reset dependent network users first"));
        }

        int payoutItemsDetached = updateBySql(
                "UPDATE commission_payout_items SET commission_id = NULL " +
                        "WHERE commission_id IN (" +
                        "SELECT id FROM mlm_commissions WHERE beneficiary_account_holder_id = ?)",
                accountHolderId
        );
        int commissionsDetached = updateBySql(
                "UPDATE mlm_commissions SET payout_item_id = NULL WHERE beneficiary_account_holder_id = ?",
                accountHolderId
        );
        int commissionsDeleted = deleteByUuid("mlm_commissions", "beneficiary_account_holder_id", accountHolderId);
        int payoutItemsDeleted = deleteByUuid("commission_payout_items", "account_holder_id", accountHolderId);
        int commissionAdjustmentsDeleted = deleteByUuid("commission_adjustments", "account_holder_id", accountHolderId);
        int financialAuditRowsDeleted = deleteBySql(
                "DELETE FROM financial_audit_log WHERE account_holder_id = ? " +
                        "AND (event_type LIKE 'MLM_%' OR event_type LIKE 'COMMISSION_%')",
                accountHolderId
        );
        int placementAuditRowsDeleted = 0;
        placementAuditRowsDeleted += deleteByUuid("mlm_placement_audit_logs", "account_holder_id", accountHolderId);
        placementAuditRowsDeleted += deleteByUuid("mlm_placement_audit_logs", "sponsor_account_holder_id", accountHolderId);
        placementAuditRowsDeleted += deleteByUuid("mlm_placement_audit_logs", "placement_parent_id", accountHolderId);
        int volumeRowsDeleted = 0;
        volumeRowsDeleted += deleteByUuid("mlm_volume_ledger", "account_holder_id", accountHolderId);
        volumeRowsDeleted += deleteByUuid("mlm_volume_ledger", "source_member_id", accountHolderId);
        int metricsDeleted = deleteByUuid("mlm_member_metrics", "account_holder_id", accountHolderId);
        int gradesDeleted = deleteByUuid("mlm_member_grades", "member_id", accountHolderId);
        int carryoverDeleted = 0;
        carryoverDeleted += deleteByUuid("mlm_leg_carryover", "member_id", accountHolderId);
        carryoverDeleted += deleteByUuid("mlm_leg_carryover", "placement_child_id", accountHolderId);
        int snapshotsDeleted = deleteByUuid("mlm_weekly_snapshots", "account_holder_id", accountHolderId);
        int relationshipsDeleted = 0;
        relationshipsDeleted += deleteByUuid("mlm_relationships", "ancestor_id", accountHolderId);
        relationshipsDeleted += deleteByUuid("mlm_relationships", "descendant_id", accountHolderId);
        int memberLinksDeleted = deleteByUuid("mlm_member_links", "account_holder_id", accountHolderId);
        int journeysUpdated = updateBySql(
                "UPDATE mobile_onboarding_journeys SET " +
                        "sponsor_set = FALSE, " +
                        "sponsor_account_holder_id = NULL, " +
                        "network_linked = FALSE, " +
                        "onboarding_completed = FALSE, " +
                        "status = CASE " +
                        "WHEN status = 'SPONSOR_SET' THEN 'COUNTRY_SELECTED' " +
                        "WHEN status IN ('NETWORK_LINKED', 'ONBOARDING_COMPLETED') THEN 'ACTIVE_MEMBER' " +
                        "ELSE status END " +
                        "WHERE account_holder_id = ?",
                accountHolderId
        );

        List<String> warnings = new ArrayList<>();
        int graphReset = resetMlmGraph(accountHolderId, warnings);

        Map<String, Object> summary = summary();
        summary.put("commissionPayoutItemsDetached", payoutItemsDetached);
        summary.put("commissionsDetached", commissionsDetached);
        summary.put("commissionsDeleted", commissionsDeleted);
        summary.put("commissionPayoutItemsDeleted", payoutItemsDeleted);
        summary.put("commissionAdjustmentsDeleted", commissionAdjustmentsDeleted);
        summary.put("financialAuditRowsDeleted", financialAuditRowsDeleted);
        summary.put("placementAuditRowsDeleted", placementAuditRowsDeleted);
        summary.put("volumeLedgerRowsDeleted", volumeRowsDeleted);
        summary.put("memberMetricsDeleted", metricsDeleted);
        summary.put("memberGradesDeleted", gradesDeleted);
        summary.put("legCarryoverRowsDeleted", carryoverDeleted);
        summary.put("weeklySnapshotsDeleted", snapshotsDeleted);
        summary.put("relationshipsDeleted", relationshipsDeleted);
        summary.put("memberLinksDeleted", memberLinksDeleted);
        summary.put("mobileOnboardingJourneysUpdated", journeysUpdated);
        summary.put("neo4jGraphReset", graphReset > 0);
        summary.put("preservedDomains", List.of("account", "kyc", "phone", "circle", "wallet"));

        entityManager.clear();
        logReset(MLM, target, resetAt, performedBy, performedByEmail, true, summary);
        return applied(MLM, target, resetAt, "MLM reset completed", summary, warnings);
    }

    @Transactional
    public DevDomainResetResponse resetPhoneVerification(TargetedResetRequest request, UUID performedBy, String performedByEmail) {
        request.validate();
        OffsetDateTime resetAt = OffsetDateTime.now();
        Optional<ResolvedResetTarget> targetOptional = resolveTarget(request);
        if (targetOptional.isEmpty()) {
            return notApplicable(PHONE_VERIFICATION, request, resetAt, "Reset not applicable: target account holder was not found");
        }

        entityManager.flush();
        ResolvedResetTarget target = targetOptional.get();
        UUID accountHolderId = target.accountHolderId();
        int accountHolderRowsUpdated = updateBySql(
                "UPDATE account_holders SET " +
                        "phone_verified_at = NULL, " +
                        "phone_verification_source = NULL, " +
                        "phone_verification_ip = NULL, " +
                        "phone_verification_device = NULL " +
                        "WHERE id = ?",
                accountHolderId
        );
        int phoneVerificationsDeleted = deleteByUuid("phone_verifications", "requester_account_holder_id", accountHolderId);
        int onboardingRowsDemoted = updateBySql(
                "UPDATE mobile_onboarding_journeys SET " +
                        "status = 'ACTIVE_MEMBER', " +
                        "onboarding_completed = FALSE " +
                        "WHERE account_holder_id = ? " +
                        "AND status IN ('PHONE_VERIFIED', 'WALLET_ACTIVATED', 'ONBOARDING_COMPLETED')",
                accountHolderId
        );
        TaskResetCounts taskCounts = deleteTasks(accountHolderId, PHONE_TASK_TYPES);

        Map<String, Object> summary = summary();
        summary.put("accountHolderRowsUpdated", accountHolderRowsUpdated);
        summary.put("phoneVerificationsDeleted", phoneVerificationsDeleted);
        summary.put("mobileOnboardingJourneysDemoted", onboardingRowsDemoted);
        summary.put("userTasksDeleted", taskCounts.userTasksDeleted());
        summary.put("taskAuditLogsDeleted", taskCounts.taskAuditLogsDeleted());
        summary.put("preservedDomains", List.of("account", "kyc", "mlm", "circle", "wallet", "userDevices"));

        entityManager.clear();
        logReset(PHONE_VERIFICATION, target, resetAt, performedBy, performedByEmail, true, summary);
        return applied(PHONE_VERIFICATION, target, resetAt, "Phone verification reset completed", summary, List.of());
    }

    @Transactional
    public DevDomainResetResponse resetCircle(TargetedResetRequest request, UUID performedBy, String performedByEmail) {
        request.validate();
        OffsetDateTime resetAt = OffsetDateTime.now();
        Optional<ResolvedResetTarget> targetOptional = resolveTarget(request);
        if (targetOptional.isEmpty()) {
            return notApplicable(CIRCLE, request, resetAt, "Reset not applicable: target account holder was not found");
        }

        entityManager.flush();
        ResolvedResetTarget target = targetOptional.get();
        UUID accountHolderId = target.accountHolderId();
        Map<String, Object> guardSummary = circleDependencySummary(accountHolderId);
        boolean hasManagedActiveCircles = guardSummary.values().stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .anyMatch(value -> value.longValue() > 0);
        if (hasManagedActiveCircles) {
            guardSummary.put("reason", "Target manages active circles with other members");
            logReset(CIRCLE, target, resetAt, performedBy, performedByEmail, false, guardSummary);
            return notApplied(CIRCLE, target, resetAt,
                    "Reset not applicable: target manages active circle(s) with other members",
                    guardSummary,
                    List.of("Transfer or reset the other circle members before resetting this coach/owner"));
        }

        List<UUID> affectedCircleIds = selectUuidList(
                "SELECT DISTINCT circle_id FROM circle_members WHERE account_holder_id = ?",
                accountHolderId
        );
        List<UUID> memberIds = selectUuidList(
                "SELECT id FROM circle_members WHERE account_holder_id = ?",
                accountHolderId
        );

        int chatReadReceiptsDeleted = 0;
        chatReadReceiptsDeleted += deleteBySql(
                "DELETE FROM circle_chat_read_receipts WHERE message_id IN (" +
                        "SELECT id FROM circle_chat_messages WHERE sender_id = ?)",
                accountHolderId
        );
        chatReadReceiptsDeleted += deleteByUuid("circle_chat_read_receipts", "member_id", accountHolderId);
        int chatMessagesDeleted = deleteByUuid("circle_chat_messages", "sender_id", accountHolderId);
        int attendanceDeleted = deleteByUuid("circle_meeting_attendance", "account_holder_id", accountHolderId);
        int interactionsDeleted = deleteBySql(
                "DELETE FROM circle_member_interactions WHERE member_id = ? OR sender_id = ?",
                accountHolderId,
                accountHolderId
        );
        int activityDeleted = deleteByUuid("circle_activity", "actor_user_id", accountHolderId);
        int inviteCodesDeleted = deleteBySql(
                "DELETE FROM invite_codes WHERE invite_id IN (" +
                        "SELECT id FROM circle_invites WHERE created_by_account_holder_id = ?)",
                accountHolderId
        );
        int invitesDeleted = deleteByUuid("circle_invites", "created_by_account_holder_id", accountHolderId);
        int coachAssignmentsDeleted = deleteByUuid("circle_coach_assignments", "coach_account_holder_id", accountHolderId);
        int coachAssignmentsUpdated = updateBySql(
                "UPDATE circle_coach_assignments SET assigned_by_account_holder_id = NULL " +
                        "WHERE assigned_by_account_holder_id = ?",
                accountHolderId
        );
        int repaymentsDeleted = deleteByUuidsWithSubquery(
                "DELETE FROM circle_repayments WHERE loan_id IN (" +
                        "SELECT id FROM circle_loans WHERE borrower_id = ?)",
                memberIds
        );
        int loansDeleted = deleteByUuids("circle_loans", "borrower_id", memberIds);
        int contributionsDeleted = deleteByUuids("circle_contributions", "member_id", memberIds);
        int memberSavingsContributionsDeleted = deleteByUuid(
                "member_savings_contributions",
                "account_holder_id",
                accountHolderId
        );
        int membersDeleted = deleteByUuid("circle_members", "account_holder_id", accountHolderId);
        int circleProgressDeleted = deleteByUuids("circle_progress", "circle_id", affectedCircleIds);
        int circleScoresDeleted = deleteByUuids("circle_scores", "circle_id", affectedCircleIds);
        int circleTrustScoresDeleted = deleteByUuids("circle_trust_scores", "circle_id", affectedCircleIds);
        int seedCapitalEligibilityDeleted = deleteByUuids("seed_capital_eligibility", "circle_id", affectedCircleIds);
        int emptyCirclesClosed = closeEmptyCircles(affectedCircleIds);
        TaskResetCounts taskCounts = deleteTasks(accountHolderId, CIRCLE_TASK_TYPES);

        Map<String, Object> summary = summary();
        summary.put("affectedCircles", affectedCircleIds.size());
        summary.put("circleMemberRowsDeleted", membersDeleted);
        summary.put("circleContributionRowsDeleted", contributionsDeleted);
        summary.put("circleLoanRowsDeleted", loansDeleted);
        summary.put("circleRepaymentRowsDeleted", repaymentsDeleted);
        summary.put("circleMeetingAttendanceRowsDeleted", attendanceDeleted);
        summary.put("circleMemberInteractionRowsDeleted", interactionsDeleted);
        summary.put("circleActivityRowsDeleted", activityDeleted);
        summary.put("circleInviteRowsDeleted", invitesDeleted);
        summary.put("inviteCodeRowsDeleted", inviteCodesDeleted);
        summary.put("circleChatMessageRowsDeleted", chatMessagesDeleted);
        summary.put("circleChatReadReceiptRowsDeleted", chatReadReceiptsDeleted);
        summary.put("circleCoachAssignmentsDeleted", coachAssignmentsDeleted);
        summary.put("circleCoachAssignmentsUpdated", coachAssignmentsUpdated);
        summary.put("memberSavingsContributionsDeleted", memberSavingsContributionsDeleted);
        summary.put("circleProgressRowsDeleted", circleProgressDeleted);
        summary.put("circleScoreRowsDeleted", circleScoresDeleted);
        summary.put("circleTrustScoreRowsDeleted", circleTrustScoresDeleted);
        summary.put("seedCapitalEligibilityRowsDeleted", seedCapitalEligibilityDeleted);
        summary.put("emptyCirclesClosed", emptyCirclesClosed);
        summary.put("userTasksDeleted", taskCounts.userTasksDeleted());
        summary.put("taskAuditLogsDeleted", taskCounts.taskAuditLogsDeleted());
        summary.put("preservedDomains", List.of("account", "kyc", "phone", "mlm", "wallet"));

        entityManager.clear();
        logReset(CIRCLE, target, resetAt, performedBy, performedByEmail, true, summary);
        return applied(CIRCLE, target, resetAt, "Circle reset completed", summary, List.of());
    }

    private Optional<ResolvedResetTarget> resolveTarget(TargetedResetRequest request) {
        if (request.accountHolderId() != null) {
            UUID accountHolderId = request.accountHolderId();
            if (!accountHolderRepository.existsById(accountHolderId)) {
                return Optional.empty();
            }
            UUID userId = appUserRepository.findFirstByAccountHolderIdOrderByCreatedAtAsc(accountHolderId)
                    .map(AppUser::getId)
                    .or(() -> userRepository.findById(accountHolderId).map(User::getId))
                    .orElse(null);
            return Optional.of(new ResolvedResetTarget(accountHolderId, userId));
        }

        UUID userId = request.userId();
        AppUser appUser = appUserRepository.findById(userId).orElse(null);
        if (appUser != null && accountHolderRepository.existsById(appUser.getAccountHolderId())) {
            return Optional.of(new ResolvedResetTarget(appUser.getAccountHolderId(), appUser.getId()));
        }

        if (accountHolderRepository.existsById(userId)) {
            UUID primaryUserId = userRepository.findById(userId).map(User::getId).orElse(userId);
            return Optional.of(new ResolvedResetTarget(userId, primaryUserId));
        }

        return Optional.empty();
    }

    private Map<String, Object> mlmDependencySummary(UUID accountHolderId) {
        Map<String, Object> summary = summary();
        summary.put("sponsoredChildren", countBySql(
                "SELECT COUNT(*) FROM mlm_member_links WHERE sponsor_account_holder_id = ?",
                accountHolderId
        ));
        summary.put("placementChildren", countBySql(
                "SELECT COUNT(*) FROM mlm_member_links " +
                        "WHERE placement_account_holder_id = ? AND account_holder_id <> ?",
                accountHolderId,
                accountHolderId
        ));
        summary.put("relationshipDownline", countBySql(
                "SELECT COUNT(*) FROM mlm_relationships " +
                        "WHERE ancestor_id = ? AND descendant_id <> ? AND level > 0",
                accountHolderId,
                accountHolderId
        ));
        summary.put("referencingOnboardingJourneys", countBySql(
                "SELECT COUNT(*) FROM mobile_onboarding_journeys " +
                        "WHERE sponsor_account_holder_id = ? AND account_holder_id <> ?",
                accountHolderId,
                accountHolderId
        ));
        return summary;
    }

    private Map<String, Object> circleDependencySummary(UUID accountHolderId) {
        Map<String, Object> summary = summary();
        summary.put("activeLegacyManagedCirclesWithOtherMembers", countBySql(
                "SELECT COUNT(*) FROM circles c " +
                        "WHERE c.coach_id = ? AND c.status = 'ACTIVE' " +
                        "AND EXISTS (" +
                        "SELECT 1 FROM circle_members cm " +
                        "WHERE cm.circle_id = c.id AND cm.account_holder_id <> ?)",
                accountHolderId,
                accountHolderId
        ));
        summary.put("activeAssignedCoachCirclesWithOtherMembers", countBySql(
                "SELECT COUNT(*) FROM circle_coach_assignments cca " +
                        "JOIN circles c ON c.id = cca.circle_id " +
                        "WHERE cca.coach_account_holder_id = ? AND c.status = 'ACTIVE' " +
                        "AND EXISTS (" +
                        "SELECT 1 FROM circle_members cm " +
                        "WHERE cm.circle_id = c.id AND cm.account_holder_id <> ?)",
                accountHolderId,
                accountHolderId
        ));
        summary.put("activeCoachMembershipsWithOtherMembers", countBySql(
                "SELECT COUNT(*) FROM circle_members cm " +
                        "JOIN circles c ON c.id = cm.circle_id " +
                        "WHERE cm.account_holder_id = ? AND cm.role = 'COACH' AND c.status = 'ACTIVE' " +
                        "AND EXISTS (" +
                        "SELECT 1 FROM circle_members other_cm " +
                        "WHERE other_cm.circle_id = c.id AND other_cm.account_holder_id <> ?)",
                accountHolderId,
                accountHolderId
        ));
        return summary;
    }

    private int resetMlmGraph(UUID accountHolderId, List<String> warnings) {
        MlmGraphNeo4jGateway graphGateway = graphGatewayProvider.getIfAvailable();
        if (graphGateway == null || !graphGateway.isAvailable()) {
            return 0;
        }
        try {
            graphGateway.write(
                    """
                            MATCH (u:User {id: $userId})
                            OPTIONAL MATCH (u)-[oldSponsor:SPONSORED_BY]->()
                            DELETE oldSponsor
                            WITH u
                            OPTIONAL MATCH (u)-[oldPlacement:PLACED_UNDER]->()
                            DELETE oldPlacement
                            RETURN u.id AS userId
                            """,
                    Map.of("userId", accountHolderId.toString())
            );
            return 1;
        } catch (RuntimeException ex) {
            warnings.add("Neo4j graph reset failed: " + ex.getClass().getSimpleName());
            log.warn("DEV_RESET_MLM graph cleanup failed accountHolderId={}", accountHolderId, ex);
            return 0;
        }
    }

    private int closeEmptyCircles(Collection<UUID> circleIds) {
        int updated = 0;
        for (UUID circleId : circleIds) {
            updated += updateBySql(
                    "UPDATE circles SET status = 'CLOSED' " +
                            "WHERE id = ? AND NOT EXISTS (" +
                            "SELECT 1 FROM circle_members WHERE circle_id = ?)",
                    circleId,
                    circleId
            );
        }
        return updated;
    }

    private TaskResetCounts deleteTasks(UUID accountHolderId, Collection<String> taskTypes) {
        int taskAuditLogsDeleted = 0;
        int userTasksDeleted = 0;
        for (String taskType : taskTypes) {
            List<UUID> taskIds = selectUuidList(
                    "SELECT id FROM user_tasks WHERE account_holder_id = ? AND task_type = ?",
                    accountHolderId,
                    taskType
            );
            taskAuditLogsDeleted += deleteByUuids("task_audit_logs", "user_task_id", taskIds);
            taskAuditLogsDeleted += deleteBySql(
                    "DELETE FROM task_audit_logs WHERE account_holder_id = ? AND task_type = ?",
                    accountHolderId,
                    taskType
            );
            userTasksDeleted += deleteBySql(
                    "DELETE FROM user_tasks WHERE account_holder_id = ? AND task_type = ?",
                    accountHolderId,
                    taskType
            );
        }
        return new TaskResetCounts(userTasksDeleted, taskAuditLogsDeleted);
    }

    private List<UUID> selectUuidList(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getObject(1, UUID.class), args);
    }

    private long countBySql(String sql, Object... args) {
        Number number = jdbcTemplate.queryForObject(sql, Number.class, args);
        return number == null ? 0L : number.longValue();
    }

    private int deleteByUuidsWithSubquery(String sql, Collection<UUID> values) {
        int rows = 0;
        for (UUID value : values) {
            rows += deleteBySql(sql, value);
        }
        return rows;
    }

    private int deleteByUuids(String table, String column, Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int rows = 0;
        for (UUID value : values) {
            rows += deleteByUuid(table, column, value);
        }
        return rows;
    }

    private int deleteByUuid(String table, String column, UUID value) {
        if (value == null) {
            return 0;
        }
        return deleteBySql("DELETE FROM " + table + " WHERE " + column + " = ?", value);
    }

    private int deleteBySql(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    private int updateBySql(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    private DevDomainResetResponse applied(String domain,
                                           ResolvedResetTarget target,
                                           OffsetDateTime resetAt,
                                           String message,
                                           Map<String, Object> summary,
                                           List<String> warnings) {
        return new DevDomainResetResponse(
                true,
                domain,
                target.accountHolderId(),
                target.userId(),
                resetAt,
                message,
                summary,
                warnings == null ? List.of() : List.copyOf(warnings)
        );
    }

    private DevDomainResetResponse notApplied(String domain,
                                              ResolvedResetTarget target,
                                              OffsetDateTime resetAt,
                                              String message,
                                              Map<String, Object> summary,
                                              List<String> warnings) {
        return new DevDomainResetResponse(
                false,
                domain,
                target.accountHolderId(),
                target.userId(),
                resetAt,
                message,
                summary,
                warnings == null ? List.of() : List.copyOf(warnings)
        );
    }

    private DevDomainResetResponse notApplicable(String domain,
                                                 TargetedResetRequest request,
                                                 OffsetDateTime resetAt,
                                                 String message) {
        return new DevDomainResetResponse(
                false,
                domain,
                request.accountHolderId(),
                request.userId(),
                resetAt,
                message,
                summary(),
                List.of()
        );
    }

    private Map<String, Object> summary() {
        return new LinkedHashMap<>();
    }

    private void logReset(String domain,
                          ResolvedResetTarget target,
                          OffsetDateTime resetAt,
                          UUID performedBy,
                          String performedByEmail,
                          boolean applied,
                          Map<String, Object> summary) {
        String performedByValue = performedByEmail != null && !performedByEmail.isBlank()
                ? performedByEmail
                : String.valueOf(performedBy);
        log.warn(
                "DEV_RESET_{} executed accountHolderId={} userId={} timestamp={} performedBy={} applied={} summary={}",
                domain,
                target.accountHolderId(),
                target.userId(),
                resetAt,
                performedByValue,
                applied,
                summary
        );
    }

    private record ResolvedResetTarget(UUID accountHolderId, UUID userId) {
    }

    private record TaskResetCounts(int userTasksDeleted, int taskAuditLogsDeleted) {
    }
}
