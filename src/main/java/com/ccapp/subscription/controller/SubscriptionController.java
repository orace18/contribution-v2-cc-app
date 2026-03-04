package com.ccapp.subscription.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.subscription.dto.InitiateSubscriptionRequest;
import com.ccapp.subscription.dto.InitiateSubscriptionResponse;
import com.ccapp.subscription.dto.SubscriptionPlanResponse;
import com.ccapp.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/plans")
    public ResponseEntity<CommonResponse<List<SubscriptionPlanResponse>>> plans(@RequestParam String country) {
        return ResponseEntity.ok(CommonResponse.success("Subscription plans", subscriptionService.getPlans(country)));
    }

    @PostMapping("/initiate")
    public ResponseEntity<CommonResponse<InitiateSubscriptionResponse>> initiate(
            @Valid @RequestBody InitiateSubscriptionRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                "Subscription initiated",
                subscriptionService.initiate(SecurityUtils.currentUserId(), request)));
    }
}
