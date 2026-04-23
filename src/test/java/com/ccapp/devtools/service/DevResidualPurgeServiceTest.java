package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevChecklistEntry;
import com.ccapp.devtools.dto.DevChecklistGroup;
import com.ccapp.devtools.dto.DevResidualPurgeReport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevResidualPurgeServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DevUserVerificationService verificationService = mock(DevUserVerificationService.class);
    private final DevResidualPurgeService service = new DevResidualPurgeService(jdbcTemplate, verificationService);

    @Test
    void purge_returnsFinalStatusFromVerificationServiceAfterPurge() {
        UUID accountHolderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Set<UUID> userIds = Set.of(userId);

        List<DevChecklistGroup> before = List.of(new DevChecklistGroup("IDENTITY_CORE", List.of(
                DevChecklistEntry.residual("users", 1L),
                DevChecklistEntry.residual("account_holders", 1L)
        )));
        List<DevChecklistGroup> after = List.of(new DevChecklistGroup("IDENTITY_CORE", List.of(
                DevChecklistEntry.clean("users"),
                DevChecklistEntry.clean("account_holders")
        )));

        when(verificationService.buildChecklist(accountHolderId, userIds))
                .thenReturn(before)
                .thenReturn(after);
        when(verificationService.computeStatus(after)).thenReturn("CLEAN");

        DevResidualPurgeReport report = service.purge(accountHolderId, userIds);

        assertEquals("CLEAN", report.finalStatus());
        assertEquals(accountHolderId, report.accountHolderId());
        assertNotNull(report.purgedAt());
        assertEquals(before, report.before());
        assertEquals(after, report.after());
        assertTrue(report.warnings().isEmpty());
    }

    @Test
    void purge_returnsPartial_whenResidualsPersistAfterPurge() {
        UUID accountHolderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Set<UUID> userIds = Set.of(userId);

        List<DevChecklistGroup> before = List.of(new DevChecklistGroup("IDENTITY_CORE", List.of(
                DevChecklistEntry.residual("account_holders", 1L)
        )));
        List<DevChecklistGroup> after = List.of(new DevChecklistGroup("IDENTITY_CORE", List.of(
                DevChecklistEntry.residual("account_holders", 1L)
        )));

        when(verificationService.buildChecklist(accountHolderId, userIds))
                .thenReturn(before)
                .thenReturn(after);
        when(verificationService.computeStatus(after)).thenReturn("PARTIAL");

        DevResidualPurgeReport report = service.purge(accountHolderId, userIds);

        assertEquals("PARTIAL", report.finalStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void purge_discoversUserIdsFromAppUsers_whenNoneProvided() {
        UUID accountHolderId = UUID.randomUUID();
        UUID discoveredUserId = UUID.randomUUID();

        when(jdbcTemplate.query(
                eq("SELECT id FROM app_users WHERE account_holder_id = ?"),
                any(RowMapper.class),
                eq(accountHolderId)
        )).thenReturn(List.of(discoveredUserId));

        List<DevChecklistGroup> emptyChecklist = List.of();
        when(verificationService.buildChecklist(any(), any())).thenReturn(emptyChecklist);
        when(verificationService.computeStatus(emptyChecklist)).thenReturn("CLEAN");

        DevResidualPurgeReport report = service.purge(accountHolderId, Set.of());

        assertNotNull(report);
        // Verify the service attempted to discover userIds from app_users
        verify(jdbcTemplate, atLeastOnce()).query(
                eq("SELECT id FROM app_users WHERE account_holder_id = ?"),
                any(RowMapper.class),
                eq(accountHolderId)
        );
    }

    @Test
    void purge_collectedWarnings_whenDeleteFails() {
        UUID accountHolderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Set<UUID> userIds = Set.of(userId);

        // Simulate a FK constraint failure on account_holders delete
        doThrow(new RuntimeException("trigger management not supported")).when(jdbcTemplate).execute(any(String.class));
        when(jdbcTemplate.update(any(String.class), any(Object.class)))
                .thenAnswer(inv -> {
                    String sql = inv.getArgument(0);
                    if (sql.contains("account_holders")) {
                        throw new RuntimeException("FK constraint violation");
                    }
                    return 0;
                });

        List<DevChecklistGroup> checklist = List.of();
        when(verificationService.buildChecklist(any(), any())).thenReturn(checklist);
        when(verificationService.computeStatus(checklist)).thenReturn("PARTIAL");

        DevResidualPurgeReport report = service.purge(accountHolderId, userIds);

        // Report should still be returned with warnings — never throws
        assertNotNull(report);
        assertTrue(report.warnings().stream().anyMatch(w -> w.contains("account_holders") || w.contains("Failed")));
    }
}
