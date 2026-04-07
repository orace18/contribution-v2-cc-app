package com.ccapp.prayer.controller.admin;

import com.ccapp.admin.audit.AdminAuditService;
import com.ccapp.admin.security.AdminSecurityUtils;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.api.PageResponse;
import com.ccapp.prayer.domain.enums.PrayerDailyPrayerStatus;
import com.ccapp.prayer.dto.AdminCreateDailyPrayerRequest;
import com.ccapp.prayer.dto.AdminDailyPrayerResponse;
import com.ccapp.prayer.dto.AdminUpdateDailyPrayerRequest;
import com.ccapp.prayer.service.PrayerAdminDailyPrayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/prayers/daily")
@Tag(name = "Prayer Admin Daily", description = "Admin management for Business Prayer daily prayers")
@SecurityRequirement(name = "bearerAuth")
public class AdminDailyPrayerController {

    private final PrayerAdminDailyPrayerService prayerAdminDailyPrayerService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    @Operation(summary = "List daily prayer records")
    public ResponseEntity<CommonResponse<PageResponse<AdminDailyPrayerResponse>>> listDailyPrayers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) PrayerDailyPrayerStatus status) {
        PageResponse<AdminDailyPrayerResponse> response = prayerAdminDailyPrayerService.listDailyPrayers(
                page,
                size,
                fromDate,
                toDate,
                status
        );
        return ResponseEntity.ok(CommonResponse.success("Daily prayer records", response));
    }

    @PostMapping
    @Operation(summary = "Create a daily prayer draft")
    public ResponseEntity<CommonResponse<AdminDailyPrayerResponse>> createDailyPrayer(
            @Valid @RequestBody AdminCreateDailyPrayerRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminDailyPrayerResponse response = prayerAdminDailyPrayerService.createDailyPrayer(adminUserId, request);
        adminAuditService.logAction(adminUserId, "PRAYER_DAILY_CREATED", "PRAYER_DAILY", response.id().toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Daily prayer created", response));
    }

    @GetMapping("/{dailyPrayerId}")
    @Operation(summary = "Get a daily prayer record")
    public ResponseEntity<CommonResponse<AdminDailyPrayerResponse>> getDailyPrayer(@PathVariable UUID dailyPrayerId) {
        AdminDailyPrayerResponse response = prayerAdminDailyPrayerService.getDailyPrayer(dailyPrayerId);
        return ResponseEntity.ok(CommonResponse.success("Daily prayer record", response));
    }

    @PatchMapping("/{dailyPrayerId}")
    @Operation(summary = "Update a daily prayer draft")
    public ResponseEntity<CommonResponse<AdminDailyPrayerResponse>> updateDailyPrayer(
            @PathVariable UUID dailyPrayerId,
            @Valid @RequestBody AdminUpdateDailyPrayerRequest request) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminDailyPrayerResponse response = prayerAdminDailyPrayerService.updateDailyPrayer(adminUserId, dailyPrayerId, request);
        adminAuditService.logAction(adminUserId, "PRAYER_DAILY_UPDATED", "PRAYER_DAILY", dailyPrayerId.toString(), request);
        return ResponseEntity.ok(CommonResponse.success("Daily prayer updated", response));
    }

    @PostMapping("/{dailyPrayerId}/publish")
    @Operation(summary = "Publish a daily prayer record")
    public ResponseEntity<CommonResponse<AdminDailyPrayerResponse>> publishDailyPrayer(@PathVariable UUID dailyPrayerId) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminDailyPrayerResponse response = prayerAdminDailyPrayerService.publishDailyPrayer(adminUserId, dailyPrayerId);
        adminAuditService.logAction(adminUserId, "PRAYER_DAILY_PUBLISHED", "PRAYER_DAILY", dailyPrayerId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Daily prayer published", response));
    }

    @PostMapping("/{dailyPrayerId}/archive")
    @Operation(summary = "Archive a daily prayer record")
    public ResponseEntity<CommonResponse<AdminDailyPrayerResponse>> archiveDailyPrayer(@PathVariable UUID dailyPrayerId) {
        UUID adminUserId = AdminSecurityUtils.currentAdminUserId();
        AdminDailyPrayerResponse response = prayerAdminDailyPrayerService.archiveDailyPrayer(adminUserId, dailyPrayerId);
        adminAuditService.logAction(adminUserId, "PRAYER_DAILY_ARCHIVED", "PRAYER_DAILY", dailyPrayerId.toString(), null);
        return ResponseEntity.ok(CommonResponse.success("Daily prayer archived", response));
    }
}
