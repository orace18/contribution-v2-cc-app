package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevChecklistGroup;
import com.ccapp.devtools.dto.DevResidualPurgeReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Complementary purge for identity/KYC residuals after a delete-user call.
 * Strictly scoped to Dev Tools — never called from business logic.
 *
 * Does NOT touch wallet, operations, financial_audit_log, or any transactional domain.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DevResidualPurgeService {

    private final JdbcTemplate jdbcTemplate;
    private final DevUserVerificationService verificationService;

    @Transactional
    public DevResidualPurgeReport purge(UUID accountHolderId, Set<UUID> providedUserIds) {
        OffsetDateTime purgedAt = OffsetDateTime.now();
        List<String> warnings = new ArrayList<>();

        Set<UUID> userIds = resolveUserIds(accountHolderId, providedUserIds);

        List<DevChecklistGroup> before = verificationService.buildChecklist(accountHolderId, userIds);

        purgeKycResiduals(accountHolderId, warnings);
        purgeOnboardingResiduals(accountHolderId, warnings);
        purgeIdentityExtrasResiduals(userIds, warnings);
        purgeIdentityCoreResiduals(accountHolderId, userIds, warnings);

        List<DevChecklistGroup> after = verificationService.buildChecklist(accountHolderId, userIds);
        String finalStatus = verificationService.computeStatus(after);

        log.warn(
                "DEV_RESIDUAL_PURGE executed accountHolderId={} userIds={} finalStatus={} purgedAt={}",
                accountHolderId, userIds, finalStatus, purgedAt
        );

        return new DevResidualPurgeReport(
                accountHolderId,
                finalStatus,
                purgedAt,
                before,
                after,
                List.of(),
                warnings
        );
    }

    /**
     * If caller provides userIds (from the delete-user report), use them.
     * Otherwise, discover remaining linked users from residual app_users rows.
     */
    private Set<UUID> resolveUserIds(UUID accountHolderId, Set<UUID> provided) {
        if (provided != null && !provided.isEmpty()) {
            return provided;
        }
        List<UUID> fromAppUsers = jdbcTemplate.query(
                "SELECT id FROM app_users WHERE account_holder_id = ?",
                (rs, n) -> rs.getObject(1, UUID.class),
                accountHolderId
        );
        Set<UUID> resolved = new LinkedHashSet<>(fromAppUsers);
        // accountHolderId may equal userId (shared PK pattern used in this project)
        resolved.add(accountHolderId);
        return resolved;
    }

    private void purgeKycResiduals(UUID accountHolderId, List<String> warnings) {
        safeDelete("DELETE FROM kyc_media_backups WHERE account_holder_id = ?", accountHolderId, warnings);
        safeDelete(
                "DELETE FROM verification_analysis WHERE verification_id IN " +
                "(SELECT id FROM identity_verifications WHERE account_holder_id = ?)",
                accountHolderId, warnings);
        safeDelete(
                "DELETE FROM verification_documents WHERE verification_id IN " +
                "(SELECT id FROM identity_verifications WHERE account_holder_id = ?)",
                accountHolderId, warnings);
        safeDelete("DELETE FROM identity_verifications WHERE account_holder_id = ?", accountHolderId, warnings);
        safeDelete("DELETE FROM kyc_documents WHERE account_holder_id = ?", accountHolderId, warnings);
        safeDelete("DELETE FROM media_files WHERE account_holder_id = ?", accountHolderId, warnings);
    }

    private void purgeOnboardingResiduals(UUID accountHolderId, List<String> warnings) {
        safeDelete("DELETE FROM phone_verifications WHERE requester_account_holder_id = ?", accountHolderId, warnings);
        safeDelete("DELETE FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId, warnings);
        safeDelete("DELETE FROM memberships WHERE account_holder_id = ?", accountHolderId, warnings);
    }

    private void purgeIdentityExtrasResiduals(Set<UUID> userIds, List<String> warnings) {
        for (UUID userId : userIds) {
            safeDelete("DELETE FROM user_sessions WHERE user_id = ?", userId, warnings);
            safeDelete("DELETE FROM user_profiles WHERE user_id = ?", userId, warnings);
            safeDelete("DELETE FROM user_devices WHERE user_id = ?", userId, warnings);
        }
    }

    private void purgeIdentityCoreResiduals(UUID accountHolderId, Set<UUID> userIds, List<String> warnings) {
        for (UUID userId : userIds) {
            safeDelete("DELETE FROM app_users WHERE id = ?", userId, warnings);
            safeDelete("DELETE FROM users WHERE id = ?", userId, warnings);
        }
        // account_holders may have a FK trigger preventing deletion if circles reference coach_id
        try {
            jdbcTemplate.execute("ALTER TABLE account_holders DISABLE TRIGGER ALL");
            safeDelete("DELETE FROM account_holders WHERE id = ?", accountHolderId, warnings);
            jdbcTemplate.execute("ALTER TABLE account_holders ENABLE TRIGGER ALL");
        } catch (Exception e) {
            // Not PostgreSQL or trigger management not available — attempt direct delete
            safeDelete("DELETE FROM account_holders WHERE id = ?", accountHolderId, warnings);
        }
    }

    private void safeDelete(String sql, Object param, List<String> warnings) {
        try {
            jdbcTemplate.update(sql, param);
        } catch (Exception e) {
            String snippet = sql.length() > 80 ? sql.substring(0, 80) + "..." : sql;
            warnings.add("Failed: " + snippet + " — " + e.getMessage());
        }
    }
}
