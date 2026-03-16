package com.ccapp.financing.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.financing.dto.OfferRequirementResponse;
import com.ccapp.financing.service.OfferRequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/offers")
@RequiredArgsConstructor
public class OfferRequirementController {

    private final OfferRequirementService offerRequirementService;

    @GetMapping("/{offerId}/requirements")
    public ResponseEntity<CommonResponse<List<OfferRequirementResponse>>> listRequirements(@PathVariable UUID offerId) {
        return ResponseEntity.ok(CommonResponse.success(
                "Offer requirements",
                offerRequirementService.listRequirements(offerId)
        ));
    }
}
