package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevChecklistEntry;
import com.ccapp.devtools.dto.DevChecklistGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a structured post-delete verification checklist against all identity/KYC-related tables.
 * Used after delete-user to confirm the account graph is clean, or before/after residual purge.
 */
@Service
@RequiredArgsConstructor
public class DevUserVerificationService {

    private final JdbcTemplate jdbcTemplate;

    public List<DevChecklistGroup> buildChecklist(UUID accountHolderId, Set<UUID> userIds) {
        List<DevChecklistGroup> groups = new ArrayList<>();
        groups.add(buildIdentityCoreGroup(accountHolderId, userIds));
        groups.add(buildIdentityExtrasGroup(userIds));
        groups.add(buildSessionSecurityGroup(userIds));
        groups.add(buildOnboardingGroup(accountHolderId));
        groups.add(buildKycGroup(accountHolderId));
        return groups;
    }

    /** CLEAN if all entries are clean (0 residuals). PARTIAL otherwise. */
    public String computeStatus(List<DevChecklistGroup> checklist) {
        boolean hasResiduals = checklist.stream()
                .flatMap(g -> g.entries().stream())
                .anyMatch(e -> !e.clean());
        return hasResiduals ? "PARTIAL" : "CLEAN";
    }

    public boolean hasResiduals(List<DevChecklistGroup> checklist) {
        return checklist.stream()
                .flatMap(g -> g.entries().stream())
                .anyMatch(e -> !e.clean());
    }

    private DevChecklistGroup buildIdentityCoreGroup(UUID accountHolderId, Set<UUID> userIds) {
        List<DevChecklistEntry> entries = new ArrayList<>();
        entries.add(checkByUuids("users", "id", userIds));
        entries.add(checkByUuids("app_users", "id", userIds));
        entries.add(checkByUuid("account_holders", "id", accountHolderId));
        return new DevChecklistGroup("IDENTITY_CORE", entries);
    }

    private DevChecklistGroup buildIdentityExtrasGroup(Set<UUID> userIds) {
        List<DevChecklistEntry> entries = new ArrayList<>();
        entries.add(checkByUuids("user_profiles", "user_id", userIds));
        entries.add(checkByUuids("user_devices", "user_id", userIds));
        return new DevChecklistGroup("IDENTITY_EXTRAS", entries);
    }

    private DevChecklistGroup buildSessionSecurityGroup(Set<UUID> userIds) {
        List<DevChecklistEntry> entries = new ArrayList<>();
        entries.add(checkByUuids("user_sessions", "user_id", userIds));
        // These tables may not exist in all environments — handled gracefully
        entries.add(checkTableIfExists("auth_token_usage", "user_id", userIds));
        entries.add(checkTableIfExists("security_events", "user_id", userIds));
        entries.add(checkTableIfExists("login_attempts", "user_id", userIds));
        return new DevChecklistGroup("SESSION_SECURITY", entries);
    }

    private DevChecklistGroup buildOnboardingGroup(UUID accountHolderId) {
        List<DevChecklistEntry> entries = new ArrayList<>();
        entries.add(checkByUuid("mobile_onboarding_journeys", "account_holder_id", accountHolderId));
        entries.add(checkByUuid("phone_verifications", "requester_account_holder_id", accountHolderId));
        entries.add(checkByUuid("memberships", "account_holder_id", accountHolderId));
        return new DevChecklistGroup("ONBOARDING", entries);
    }

    private DevChecklistGroup buildKycGroup(UUID accountHolderId) {
        List<DevChecklistEntry> entries = new ArrayList<>();
        entries.add(checkByUuid("identity_verifications", "account_holder_id", accountHolderId));
        entries.add(checkViaSubquery(
                "verification_documents", "verification_id",
                "SELECT id FROM identity_verifications WHERE account_holder_id = ?", accountHolderId));
        entries.add(checkViaSubquery(
                "verification_analysis", "verification_id",
                "SELECT id FROM identity_verifications WHERE account_holder_id = ?", accountHolderId));
        entries.add(checkByUuid("kyc_documents", "account_holder_id", accountHolderId));
        entries.add(checkByUuid("kyc_media_backups", "account_holder_id", accountHolderId));
        entries.add(checkByUuid("media_files", "account_holder_id", accountHolderId));
        return new DevChecklistGroup("KYC", entries);
    }

    DevChecklistEntry checkByUuid(String table, String column, UUID value) {
        if (value == null) {
            return DevChecklistEntry.clean(table);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Long.class, value);
        long c = count == null ? 0L : count;
        return c == 0 ? DevChecklistEntry.clean(table) : DevChecklistEntry.residual(table, c);
    }

    DevChecklistEntry checkByUuids(String table, String column, Set<UUID> values) {
        if (values == null || values.isEmpty()) {
            return DevChecklistEntry.clean(table);
        }
        long total = values.stream()
                .mapToLong(v -> checkByUuid(table, column, v).residualCount())
                .sum();
        return total == 0 ? DevChecklistEntry.clean(table) : DevChecklistEntry.residual(table, total);
    }

    private DevChecklistEntry checkViaSubquery(String table, String column, String subquery, UUID param) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IN (" + subquery + ")",
                Long.class, param);
        long c = count == null ? 0L : count;
        return c == 0 ? DevChecklistEntry.clean(table) : DevChecklistEntry.residual(table, c);
    }

    private DevChecklistEntry checkTableIfExists(String table, String column, Set<UUID> values) {
        try {
            return checkByUuids(table, column, values);
        } catch (Exception e) {
            return DevChecklistEntry.withNote(table, 0L, "table not found in schema");
        }
    }
}
