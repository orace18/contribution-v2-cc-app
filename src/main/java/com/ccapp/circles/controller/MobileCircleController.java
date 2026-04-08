package com.ccapp.circles.controller;

import com.ccapp.circles.dto.CircleMemberResponse;
import com.ccapp.circles.dto.CreateAssistedMemberRequest;
import com.ccapp.circles.service.CircleService;
import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/mobile/circles")
@RequiredArgsConstructor
@Tag(name = "Mobile Circles", description = "Circle operations for mobile clients")
@SecurityRequirement(name = "bearerAuth")
public class MobileCircleController {

    private final CircleService circleService;

    @Deprecated(forRemoval = false)
    @PostMapping("/{circleId}/assisted-members")
    @Operation(summary = "Deprecated assisted-member circle flow; managed accounts now own this creation domain")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Assisted member created\",\"data\":{\"id\":\"4cb74a2c-3651-4a9e-bbd5-f52f16108d68\",\"accountHolderId\":\"8df991be-c9f2-45a5-9a2d-36dd6204aff3\",\"role\":\"MEMBER\",\"name\":\"Jean Kouassi\",\"activity\":\"Rice trading\",\"memberType\":\"ASSISTED\",\"managerName\":\"Esther Doe\",\"businessActivity\":\"Rice trading\",\"loanStatus\":\"NOT_STARTED\",\"repaymentProgress\":null,\"joinedAt\":\"2026-03-10T10:55:44Z\"},\"timestamp\":\"2026-03-10T10:55:44Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Validation or business rule error", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "409", description = "Phone number conflict", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<CircleMemberResponse>> createAssistedMember(@PathVariable UUID circleId,
                                                                                      @Valid @RequestBody CreateAssistedMemberRequest request) {
        UUID creatorAccountHolderId = SecurityUtils.currentAccountHolderId();
        AuthenticatedPrincipal principal = SecurityUtils.currentPrincipal();
        UUID actorUserId = principal.userId();
        CircleMemberResponse response = circleService.createAssistedMember(creatorAccountHolderId, actorUserId, circleId, request);
        return ResponseEntity.ok(CommonResponse.success("Assisted member created", response));
    }

    @GetMapping("/{circleId}/members")
    @Operation(summary = "List circle members with assisted metadata")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Success",
                    content = @Content(
                            schema = @Schema(implementation = CommonResponse.class),
                            examples = @ExampleObject(value = "{\"success\":true,\"message\":\"Circle members\",\"data\":[{\"id\":\"4cb74a2c-3651-4a9e-bbd5-f52f16108d68\",\"accountHolderId\":\"8df991be-c9f2-45a5-9a2d-36dd6204aff3\",\"role\":\"MEMBER\",\"name\":\"Jean K\",\"activity\":\"Rice trading\",\"memberType\":\"ASSISTED\",\"managerName\":\"Esther\",\"businessActivity\":\"Rice trading\",\"loanStatus\":\"NOT_STARTED\",\"repaymentProgress\":null,\"joinedAt\":\"2026-03-10T10:55:44Z\"}],\"timestamp\":\"2026-03-10T10:55:44Z\"}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(responseCode = "404", description = "Circle not found", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<CircleMemberResponse>>> listMembers(@PathVariable UUID circleId) {
        return ResponseEntity.ok(CommonResponse.success("Circle members", circleService.listMembers(circleId)));
    }
}
