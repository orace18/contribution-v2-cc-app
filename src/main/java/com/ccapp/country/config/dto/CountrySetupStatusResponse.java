package com.ccapp.country.config.dto;

import java.util.Map;

public record CountrySetupStatusResponse(
        Map<String, StepStatus> steps,
        boolean canActivate
) {
    public record StepStatus(
            boolean complete,
            String reason
    ) {
        public static StepStatus ok() {
            return new StepStatus(true, null);
        }

        public static StepStatus blocked(String reason) {
            return new StepStatus(false, reason);
        }
    }
}
