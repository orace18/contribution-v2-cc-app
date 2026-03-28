package com.ccapp.country.config.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record CountrySetupActivationPayloadsRequest(
        @NotNull(message = "general payload is required")
        JsonNode general,
        @NotNull(message = "accounting payload is required")
        JsonNode accounting,
        @NotNull(message = "membership payload is required")
        JsonNode membership,
        @JsonProperty("mlm_limits")
        @NotNull(message = "mlm_limits payload is required")
        JsonNode mlmLimits,
        JsonNode financing,
        @NotNull(message = "guarantee payload is required")
        JsonNode guarantee
) {
}
