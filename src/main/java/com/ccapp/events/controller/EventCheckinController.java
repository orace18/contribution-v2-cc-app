package com.ccapp.events.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.events.dto.EventBatchCheckinItemResponse;
import com.ccapp.events.dto.EventBatchCheckinRequest;
import com.ccapp.events.dto.EventCheckinRequest;
import com.ccapp.events.dto.EventCheckinResponse;
import com.ccapp.events.service.EventCheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Event Checkin", description = "QR validation and offline sync")
@SecurityRequirement(name = "bearerAuth")
public class EventCheckinController {

    private final EventCheckinService eventCheckinService;

    @PostMapping("/checkin")
    @Operation(summary = "Validate a single event ticket QR code")
    public ResponseEntity<CommonResponse<EventCheckinResponse>> checkin(@Valid @RequestBody EventCheckinRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Event checkin processed",
                eventCheckinService.checkin(request.qrCode())
        ));
    }

    @PostMapping("/checkin/batch")
    @Operation(summary = "Sync offline event ticket scans")
    public ResponseEntity<CommonResponse<List<EventBatchCheckinItemResponse>>> batchCheckin(
            @Valid @RequestBody EventBatchCheckinRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Event checkin batch processed",
                eventCheckinService.batchCheckin(request.items())
        ));
    }
}
