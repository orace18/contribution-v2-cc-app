package com.ccapp.directory.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.common.security.SecurityUtils;
import com.ccapp.directory.dto.DirectorySearchResponse;
import com.ccapp.directory.service.DirectorySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/directory")
@RequiredArgsConstructor
@Tag(name = "Directory", description = "Recherche canonique de cercles et de membres")
@SecurityRequirement(name = "bearerAuth")
public class DirectoryController {

    private final DirectorySearchService directorySearchService;

    @GetMapping("/search")
    @Operation(summary = "Rechercher dans l'annuaire canonique mobile")
    public ResponseEntity<CommonResponse<DirectorySearchResponse>> search(@RequestParam("q") String query,
                                                                          @RequestParam(value = "scopes", required = false) String scopes) {
        return ResponseEntity.ok(CommonResponse.success(
                "Directory search",
                directorySearchService.search(SecurityUtils.currentAccountHolderId(), query, scopes)
        ));
    }
}
