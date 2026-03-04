package com.ccapp.psp.controller;

import com.ccapp.common.api.CommonResponse;
import com.ccapp.psp.dto.JawupayCallbackRequest;
import com.ccapp.psp.service.JawupayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/psp")
@RequiredArgsConstructor
public class PspController {

    private final JawupayService jawupayService;

    @PostMapping("/jawupay/callback")
    public ResponseEntity<CommonResponse<Void>> jawupayCallback(
            @RequestHeader(name = "X-JAWUPAY-SIGNATURE", required = false) String signature,
            @Valid @RequestBody JawupayCallbackRequest request,
            HttpServletRequest httpRequest) {

        jawupayService.processCallback(request, signature, extractRemoteIp(httpRequest));
        return ResponseEntity.ok(CommonResponse.success("Callback processed"));
    }

    private String extractRemoteIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
