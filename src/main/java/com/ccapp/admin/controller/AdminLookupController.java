package com.ccapp.admin.controller;

import com.ccapp.admin.dto.LookupItemResponse;
import com.ccapp.admin.service.AdminLookupService;
import com.ccapp.common.api.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/admin/lookups", "/api/admin/lookups"})
@RequiredArgsConstructor
public class AdminLookupController {

    private final AdminLookupService adminLookupService;

    @GetMapping("/countries")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listCountries() {
        return ResponseEntity.ok(CommonResponse.success("Countries", adminLookupService.listCountries()));
    }

    @GetMapping("/world-countries")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listWorldCountries() {
        return ResponseEntity.ok(CommonResponse.success("World countries", adminLookupService.listWorldCountries()));
    }

    @GetMapping("/partners")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listPartners(
            @RequestParam(required = false) String countryCode) {
        return ResponseEntity.ok(CommonResponse.success("Partners", adminLookupService.listPartners(countryCode)));
    }

    @GetMapping("/offers")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listOffers(
            @RequestParam String countryCode) {
        return ResponseEntity.ok(CommonResponse.success("Offers", adminLookupService.listOffers(countryCode)));
    }

    @GetMapping("/accounts")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listAccounts(
            @RequestParam String countryCode) {
        return ResponseEntity.ok(CommonResponse.success("Accounts", adminLookupService.listAccounts(countryCode)));
    }

    @GetMapping("/psps")
    public ResponseEntity<CommonResponse<List<LookupItemResponse>>> listPsps(
            @RequestParam String countryCode) {
        return ResponseEntity.ok(CommonResponse.success("PSPs", adminLookupService.listPsps(countryCode)));
    }
}
