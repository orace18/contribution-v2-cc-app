package com.ccapp.identity.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.identity.dto.BlockAccountRequest;
import com.ccapp.identity.service.IdentityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/identity")
@RequiredArgsConstructor
public class InternalIdentityController {

    private final IdentityService identityService;

    @PostMapping("/users/{userId}/block")
    public ResponseEntity<CommonResponse<Void>> blockAccount(@PathVariable UUID userId,
                                                             @Valid @RequestBody BlockAccountRequest request) {
        identityService.blockAccount(userId, request.reason());
        return ResponseEntity.ok(CommonResponse.success("Account blocked"));
    }
}
