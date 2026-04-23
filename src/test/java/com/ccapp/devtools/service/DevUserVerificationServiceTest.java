package com.ccapp.devtools.service;

import com.ccapp.devtools.dto.DevChecklistEntry;
import com.ccapp.devtools.dto.DevChecklistGroup;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevUserVerificationServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DevUserVerificationService service = new DevUserVerificationService(jdbcTemplate);

    @Test
    void computeStatus_returnsClean_whenAllEntriesAreClean() {
        List<DevChecklistGroup> checklist = List.of(
                new DevChecklistGroup("IDENTITY_CORE", List.of(
                        DevChecklistEntry.clean("users"),
                        DevChecklistEntry.clean("app_users"),
                        DevChecklistEntry.clean("account_holders")
                ))
        );

        assertEquals("CLEAN", service.computeStatus(checklist));
        assertFalse(service.hasResiduals(checklist));
    }

    @Test
    void computeStatus_returnsPartial_whenAnyEntryHasResiduals() {
        List<DevChecklistGroup> checklist = List.of(
                new DevChecklistGroup("IDENTITY_CORE", List.of(
                        DevChecklistEntry.clean("users"),
                        DevChecklistEntry.residual("app_users", 1L),
                        DevChecklistEntry.clean("account_holders")
                ))
        );

        assertEquals("PARTIAL", service.computeStatus(checklist));
        assertTrue(service.hasResiduals(checklist));
    }

    @Test
    void checkByUuid_returnsClean_whenCountIsZero() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                contains("users"),
                eq(Long.class),
                eq(id)
        )).thenReturn(0L);

        DevChecklistEntry entry = service.checkByUuid("users", "id", id);

        assertTrue(entry.clean());
        assertEquals(0L, entry.residualCount());
        assertEquals("users", entry.table());
    }

    @Test
    void checkByUuid_returnsResidual_whenCountIsPositive() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(
                contains("account_holders"),
                eq(Long.class),
                eq(id)
        )).thenReturn(1L);

        DevChecklistEntry entry = service.checkByUuid("account_holders", "id", id);

        assertFalse(entry.clean());
        assertEquals(1L, entry.residualCount());
    }

    @Test
    void checkByUuid_returnsClean_whenValueIsNull() {
        DevChecklistEntry entry = service.checkByUuid("users", "id", null);
        assertTrue(entry.clean());
        assertEquals(0L, entry.residualCount());
    }

    @Test
    void checkByUuids_returnsClean_whenSetIsEmpty() {
        DevChecklistEntry entry = service.checkByUuids("users", "id", Set.of());
        assertTrue(entry.clean());
    }

    @Test
    void checkByUuids_accumulatesCountsAcrossAllIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(id1))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), eq(id2))).thenReturn(2L);

        DevChecklistEntry entry = service.checkByUuids("user_sessions", "user_id", Set.of(id1, id2));

        assertFalse(entry.clean());
        assertEquals(3L, entry.residualCount());
    }
}
