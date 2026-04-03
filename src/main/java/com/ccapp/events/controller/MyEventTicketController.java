package com.ccapp.events.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.events.dto.MyEventTicketResponse;
import com.ccapp.events.service.EventTicketQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/tickets")
@RequiredArgsConstructor
@Tag(name = "My Event Tickets", description = "Current user tickets")
@SecurityRequirement(name = "bearerAuth")
public class MyEventTicketController {

    private final EventTicketQueryService eventTicketQueryService;

    @GetMapping
    @Operation(summary = "List current user event tickets")
    public ResponseEntity<CommonResponse<List<MyEventTicketResponse>>> mine() {
        return ResponseEntity.ok(CommonResponse.success(
                "My event tickets",
                eventTicketQueryService.listCurrentUserTickets()
        ));
    }
}
