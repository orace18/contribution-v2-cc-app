package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevDomainResetResponse;
import com.ccapp.devtools.dto.TargetedResetRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(DevTargetedUserResetService.class)
class DevTargetedUserResetServiceIntegrationTest {

    @Autowired
    private DevTargetedUserResetService resetService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void resetKyc_shouldClearKycArtifactsAndLeavePhoneUntouched() {
        UUID accountHolderId = seedAccount("kyc-reset");
        UUID mediaId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbcTemplate.update(
                "UPDATE account_holders SET kyc_status = 'VERIFIED', kyc_level = 'LEVEL_1_BASIC', " +
                        "kyc_verified_at = ?, birth_date = ?, document_number = ?, nationality = ?, " +
                        "declared_document_type = ?, phone_verified_at = ?, phone_verification_source = ?, " +
                        "cpass_case_id = ? " +
                        "WHERE id = ?",
                now,
                java.sql.Date.valueOf("1990-01-01"),
                "DOC-123",
                "BJ",
                "CNI",
                now,
                "INTERNAL_OTP",
                "case-kyc-reset",
                accountHolderId
        );
        jdbcTemplate.update("UPDATE users SET kyc_status = 'VERIFIED' WHERE id = ?", accountHolderId);
        jdbcTemplate.update(
                "INSERT INTO media_files (id, account_holder_id, type, bucket, object_key, content_type, size, status, created_at) " +
                        "VALUES (?, ?, 'KYC_IDENTITY_FRONT', 'dev-kyc', ?, 'image/jpeg', 12, 'READY', ?)",
                mediaId,
                accountHolderId,
                "kyc/" + mediaId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO kyc_documents (id, account_holder_id, media_file_id, document_type, status, created_at) " +
                        "VALUES (?, ?, ?, 'IDENTITY_FRONT', 'VERIFIED', ?)",
                UUID.randomUUID(),
                accountHolderId,
                mediaId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO kyc_media_backups (media_file_id, account_holder_id, content_type, payload, created_at) " +
                        "VALUES (?, ?, 'image/jpeg', ?, ?)",
                mediaId,
                accountHolderId,
                new byte[]{1, 2, 3},
                now
        );
        UUID verificationId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO identity_verifications (id, account_holder_id, reference_id, status, current_step, " +
                        "document_type, created_at, updated_at) " +
                        "VALUES (?, ?, 'case-kyc-reset', 'APPROVED', 'COMPLETED', 'NATIONAL_ID', ?, ?)",
                verificationId,
                accountHolderId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO verification_documents (id, verification_id, document_front_key, selfie_key, bucket_name, uploaded_at) " +
                        "VALUES (?, ?, 'front-key', 'selfie-key', 'dev-kyc', ?)",
                UUID.randomUUID(),
                verificationId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO verification_analysis (id, verification_id, document_readable, analyzed_at) " +
                        "VALUES (?, ?, TRUE, ?)",
                UUID.randomUUID(),
                verificationId,
                now
        );
        seedTask(accountHolderId, taskId, "COMPLETE_KYC");

        DevDomainResetResponse result = resetService.resetKyc(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.applied());
        assertEquals("KYC", result.domain());
        assertEquals("NOT_STARTED", stringValue("SELECT kyc_status FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals("LEVEL_0_UNVERIFIED", stringValue("SELECT kyc_level FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals("NOT_STARTED", stringValue("SELECT kyc_status FROM users WHERE id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM kyc_documents WHERE account_holder_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM kyc_media_backups WHERE account_holder_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM media_files WHERE id = ?", mediaId));
        assertEquals(0L, count("SELECT COUNT(*) FROM identity_verifications WHERE account_holder_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM verification_documents WHERE verification_id = ?", verificationId));
        assertEquals(0L, count("SELECT COUNT(*) FROM verification_analysis WHERE verification_id = ?", verificationId));
        assertNull(value("SELECT cpass_case_id FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM user_tasks WHERE id = ?", taskId));
        assertNotNull(value("SELECT phone_verified_at FROM account_holders WHERE id = ?", accountHolderId));

        DevDomainResetResponse second = resetService.resetKyc(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );
        assertTrue(second.applied());
    }

    @Test
    void resetPhoneVerification_shouldClearPhoneStateAndLeaveKycUntouched() {
        UUID accountHolderId = seedAccount("phone-reset");
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "UPDATE account_holders SET kyc_status = 'VERIFIED', kyc_level = 'LEVEL_1_BASIC', " +
                        "phone_verified_at = ?, phone_verification_source = ?, phone_verification_ip = ?, " +
                        "phone_verification_device = ? WHERE id = ?",
                now,
                "FIREBASE",
                "127.0.0.1",
                "qa-device",
                accountHolderId
        );
        jdbcTemplate.update(
                "INSERT INTO phone_verifications (id, requester_account_holder_id, phone_number, channel, status, " +
                        "code_hash, attempt_count, max_attempts, send_count, max_send_count, last_sent_at, " +
                        "resend_available_at, expires_at, verified_at, created_at, updated_at) " +
                        "VALUES (?, ?, '22991000000', 'SMS', 'VERIFIED', 'hash', 1, 3, 1, 5, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                accountHolderId,
                now,
                now,
                now.plusMinutes(5),
                now,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO mobile_onboarding_journeys (id, account_holder_id, status, country_code, sponsor_set, " +
                        "network_linked, onboarding_completed, created_at, updated_at) " +
                        "VALUES (?, ?, 'PHONE_VERIFIED', 'BJ', FALSE, FALSE, TRUE, ?, ?)",
                UUID.randomUUID(),
                accountHolderId,
                now,
                now
        );

        DevDomainResetResponse result = resetService.resetPhoneVerification(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.applied());
        assertEquals("PHONE_VERIFICATION", result.domain());
        assertEquals(0L, count("SELECT COUNT(*) FROM phone_verifications WHERE requester_account_holder_id = ?", accountHolderId));
        assertEquals(null, value("SELECT phone_verified_at FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals(null, value("SELECT phone_verification_source FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals("VERIFIED", stringValue("SELECT kyc_status FROM account_holders WHERE id = ?", accountHolderId));
        assertEquals("ACTIVE_MEMBER", stringValue("SELECT status FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId));

        DevDomainResetResponse second = resetService.resetPhoneVerification(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );
        assertTrue(second.applied());
    }

    @Test
    void resetMlm_shouldClearOwnNetworkStateWhenNoOtherUsersDependOnTarget() {
        UUID sponsorId = seedAccount("mlm-sponsor");
        UUID accountHolderId = seedAccount("mlm-reset");
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "UPDATE account_holders SET phone_verified_at = ?, phone_verification_source = ? WHERE id = ?",
                now,
                "INTERNAL_OTP",
                accountHolderId
        );
        jdbcTemplate.update(
                "INSERT INTO mlm_member_links (account_holder_id, sponsor_account_holder_id, placement_account_holder_id, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                accountHolderId,
                sponsorId,
                sponsorId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO mlm_relationships (ancestor_id, descendant_id, level, created_at) VALUES (?, ?, 0, ?)",
                accountHolderId,
                accountHolderId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO mlm_member_metrics (account_holder_id, team_size, active_team_members, total_volume, total_commissions, last_updated) " +
                        "VALUES (?, 1, 1, 100.00, 10.00, ?)",
                accountHolderId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO mobile_onboarding_journeys (id, account_holder_id, status, country_code, sponsor_set, " +
                        "sponsor_account_holder_id, network_linked, onboarding_completed, created_at, updated_at) " +
                        "VALUES (?, ?, 'NETWORK_LINKED', 'BJ', TRUE, ?, TRUE, TRUE, ?, ?)",
                UUID.randomUUID(),
                accountHolderId,
                sponsorId,
                now,
                now
        );

        DevDomainResetResponse result = resetService.resetMlm(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.applied());
        assertEquals("MLM", result.domain());
        assertEquals(0L, count("SELECT COUNT(*) FROM mlm_member_links WHERE account_holder_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM mlm_relationships WHERE descendant_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM mlm_member_metrics WHERE account_holder_id = ?", accountHolderId));
        assertFalse(booleanValue("SELECT sponsor_set FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId));
        assertFalse(booleanValue("SELECT network_linked FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId));
        assertEquals(null, value("SELECT sponsor_account_holder_id FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId));
        assertEquals("ACTIVE_MEMBER", stringValue("SELECT status FROM mobile_onboarding_journeys WHERE account_holder_id = ?", accountHolderId));
        assertNotNull(value("SELECT phone_verified_at FROM account_holders WHERE id = ?", accountHolderId));

        DevDomainResetResponse second = resetService.resetMlm(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );
        assertTrue(second.applied());
    }

    @Test
    void resetMlm_shouldRefuseWhenTargetSponsorsAnotherUser() {
        UUID accountHolderId = seedAccount("mlm-guard-target");
        UUID childId = seedAccount("mlm-guard-child");
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO mlm_member_links (account_holder_id, sponsor_account_holder_id, placement_account_holder_id, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                childId,
                accountHolderId,
                accountHolderId,
                now,
                now
        );

        DevDomainResetResponse result = resetService.resetMlm(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertFalse(result.applied());
        assertEquals(1L, count("SELECT COUNT(*) FROM mlm_member_links WHERE sponsor_account_holder_id = ?", accountHolderId));
    }

    @Test
    void resetCircle_shouldRemoveMembershipAndCloseEmptyCircleWithoutTouchingKycOrPhone() {
        UUID accountHolderId = seedAccount("circle-reset");
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "UPDATE account_holders SET kyc_status = 'VERIFIED', kyc_level = 'LEVEL_1_BASIC', " +
                        "phone_verified_at = ?, phone_verification_source = ? WHERE id = ?",
                now,
                "INTERNAL_OTP",
                accountHolderId
        );
        UUID circleId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO circles (id, name, coach_id, country_code, status, created_at, updated_at) " +
                        "VALUES (?, 'QA Circle', ?, 'BJ', 'ACTIVE', ?, ?)",
                circleId,
                accountHolderId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_members (id, circle_id, account_holder_id, role, is_financial_member, joined_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MEMBER', FALSE, ?, ?, ?)",
                memberId,
                circleId,
                accountHolderId,
                now,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_chat_messages (id, circle_id, sender_id, message_type, content, sent_at) " +
                        "VALUES (?, ?, ?, 'TEXT', 'hello', ?)",
                messageId,
                circleId,
                accountHolderId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_chat_read_receipts (id, message_id, member_id, read_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                messageId,
                accountHolderId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_progress (circle_id, members_count, members_completed_training, " +
                        "members_submitted_project, members_paid_contribution, progress_percentage, updated_at) " +
                        "VALUES (?, 1, 0, 0, 0, 0.00, ?)",
                circleId,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_scores (circle_id, activity_score, training_score, repayment_score, global_score, updated_at) " +
                        "VALUES (?, 1.00, 1.00, 1.00, 1.00, ?)",
                circleId,
                now
        );

        DevDomainResetResponse result = resetService.resetCircle(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertTrue(result.applied());
        assertEquals("CIRCLE", result.domain());
        assertEquals(0L, count("SELECT COUNT(*) FROM circle_members WHERE account_holder_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM circle_chat_messages WHERE sender_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM circle_chat_read_receipts WHERE member_id = ?", accountHolderId));
        assertEquals(0L, count("SELECT COUNT(*) FROM circle_progress WHERE circle_id = ?", circleId));
        assertEquals(0L, count("SELECT COUNT(*) FROM circle_scores WHERE circle_id = ?", circleId));
        assertEquals("CLOSED", stringValue("SELECT status FROM circles WHERE id = ?", circleId));
        assertEquals("VERIFIED", stringValue("SELECT kyc_status FROM account_holders WHERE id = ?", accountHolderId));
        assertNotNull(value("SELECT phone_verified_at FROM account_holders WHERE id = ?", accountHolderId));

        DevDomainResetResponse second = resetService.resetCircle(
                new TargetedResetRequest(accountHolderId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );
        assertTrue(second.applied());
    }

    @Test
    void resetCircle_shouldRefuseManagedActiveCircleWithOtherMembers() {
        UUID coachId = seedAccount("circle-guard-coach");
        UUID memberAccountHolderId = seedAccount("circle-guard-member");
        OffsetDateTime now = OffsetDateTime.now();
        UUID circleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO circles (id, name, coach_id, country_code, status, created_at, updated_at) " +
                        "VALUES (?, 'Managed Circle', ?, 'BJ', 'ACTIVE', ?, ?)",
                circleId,
                coachId,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_members (id, circle_id, account_holder_id, role, is_financial_member, joined_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'COACH', FALSE, ?, ?, ?)",
                UUID.randomUUID(),
                circleId,
                coachId,
                now,
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO circle_members (id, circle_id, account_holder_id, role, is_financial_member, joined_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'MEMBER', FALSE, ?, ?, ?)",
                UUID.randomUUID(),
                circleId,
                memberAccountHolderId,
                now,
                now,
                now
        );

        DevDomainResetResponse result = resetService.resetCircle(
                new TargetedResetRequest(coachId, null),
                UUID.randomUUID(),
                "qa-admin@clubchretien.dev"
        );

        assertFalse(result.applied());
        assertEquals(2L, count("SELECT COUNT(*) FROM circle_members WHERE circle_id = ?", circleId));
        assertEquals("ACTIVE", stringValue("SELECT status FROM circles WHERE id = ?", circleId));
    }

    private UUID seedAccount(String suffix) {
        UUID accountHolderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO users (id, firebase_uid, email, role, account_status, kyc_status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'USER', 'ACTIVE', 'NOT_STARTED', ?, ?)",
                accountHolderId,
                "firebase-" + suffix + "-" + accountHolderId,
                suffix + "@clubchretien.dev",
                now,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO account_holders (id, first_name, last_name, phone_number, country_code, service_country_code, " +
                        "account_type, created_via, kyc_status, kyc_level, created_at) " +
                        "VALUES (?, 'QA', ?, '22991000000', 'BJ', 'BJ', 'STANDARD', 'SELF', 'NOT_STARTED', " +
                        "'LEVEL_0_UNVERIFIED', ?)",
                accountHolderId,
                suffix,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO app_users (id, firebase_uid, account_holder_id, role, created_at) VALUES (?, ?, ?, 'USER', ?)",
                accountHolderId,
                "firebase-" + suffix + "-" + accountHolderId,
                accountHolderId,
                now
        );
        return accountHolderId;
    }

    private void seedTask(UUID accountHolderId, UUID taskId, String taskType) {
        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO user_tasks (id, account_holder_id, task_type, title, status, priority_score, active_key, created_at) " +
                        "VALUES (?, ?, ?, 'QA task', 'COMPLETED', 100, NULL, ?)",
                taskId,
                accountHolderId,
                taskType,
                now
        );
        jdbcTemplate.update(
                "INSERT INTO task_audit_logs (id, user_task_id, account_holder_id, task_type, action, details_json, created_at) " +
                        "VALUES (?, ?, ?, ?, 'COMPLETED', '{}', ?)",
                UUID.randomUUID(),
                taskId,
                accountHolderId,
                taskType,
                now
        );
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0L : count;
    }

    private String stringValue(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private Object value(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Object.class, args);
    }

    private boolean booleanValue(String sql, Object... args) {
        Boolean value = jdbcTemplate.queryForObject(sql, Boolean.class, args);
        return Boolean.TRUE.equals(value);
    }
}
