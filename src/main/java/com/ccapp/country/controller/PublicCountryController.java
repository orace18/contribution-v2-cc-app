package com.ccapp.country.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.country.dto.CountryResponse;
import com.ccapp.country.service.CountryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/countries")
@RequiredArgsConstructor
@Tag(name = "Public Countries", description = "Public country catalogue")
public class PublicCountryController {

    private final CountryService countryService;

    @GetMapping
    @Operation(summary = "List configured countries")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse<List<CountryResponse>>> list() {
        return ResponseEntity.ok(CommonResponse.success("Configured countries", countryService.listConfiguredCountries()));
    }
}
