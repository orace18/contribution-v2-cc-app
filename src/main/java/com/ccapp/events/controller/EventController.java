package com.ccapp.events.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.events.dto.CreateEventRequest;
import com.ccapp.events.dto.CreateEventTicketTypeRequest;
import com.ccapp.events.dto.EventResponse;
import com.ccapp.events.dto.EventStatsResponse;
import com.ccapp.events.dto.EventTicketTypeResponse;
import com.ccapp.events.dto.UpdateEventRequest;
import com.ccapp.events.service.EventService;
import com.ccapp.events.service.EventTicketTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Event and ticket management")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;
    private final EventTicketTypeService eventTicketTypeService;

    @PostMapping
    @Operation(summary = "Create an event")
    public ResponseEntity<CommonResponse<EventResponse>> create(@Valid @RequestBody CreateEventRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Event created", eventService.create(request)));
    }

    @GetMapping
    @Operation(summary = "List visible events")
    public ResponseEntity<CommonResponse<List<EventResponse>>> list() {
        return ResponseEntity.ok(CommonResponse.success("Events", eventService.listVisibleEvents()));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get event details")
    public ResponseEntity<CommonResponse<EventResponse>> get(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success("Event", eventService.get(eventId)));
    }

    @PatchMapping("/{eventId}")
    @Operation(summary = "Update an event")
    public ResponseEntity<CommonResponse<EventResponse>> update(@PathVariable UUID eventId,
                                                                @Valid @RequestBody UpdateEventRequest request) {
        return ResponseEntity.ok(CommonResponse.success("Event updated", eventService.update(eventId, request)));
    }

    @PostMapping("/{eventId}/publish")
    @Operation(summary = "Publish an event")
    public ResponseEntity<CommonResponse<EventResponse>> publish(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success("Event published", eventService.publish(eventId)));
    }

    @PostMapping("/{eventId}/complete")
    @Operation(summary = "Complete an event and trigger compensation on delivered ticket orders")
    public ResponseEntity<CommonResponse<EventResponse>> complete(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success("Event completed", eventService.complete(eventId)));
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "Cancel an event")
    public ResponseEntity<CommonResponse<EventResponse>> cancel(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success("Event cancelled", eventService.cancel(eventId)));
    }

    @PostMapping("/{eventId}/tickets")
    @Operation(summary = "Create a ticket type for an event")
    public ResponseEntity<CommonResponse<EventTicketTypeResponse>> createTicketType(@PathVariable UUID eventId,
                                                                                    @Valid @RequestBody CreateEventTicketTypeRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Event ticket type created",
                eventTicketTypeService.create(eventId, request)
        ));
    }

    @GetMapping("/{eventId}/tickets")
    @Operation(summary = "List ticket types for an event")
    public ResponseEntity<CommonResponse<List<EventTicketTypeResponse>>> listTicketTypes(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Event ticket types",
                eventTicketTypeService.list(eventId)
        ));
    }

    @GetMapping("/{eventId}/stats")
    @Operation(summary = "Get event sales and attendance statistics")
    public ResponseEntity<CommonResponse<EventStatsResponse>> stats(@PathVariable UUID eventId) {
        return ResponseEntity.ok(CommonResponse.success("Event stats", eventService.stats(eventId)));
    }
}
