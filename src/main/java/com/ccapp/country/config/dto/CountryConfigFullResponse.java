package com.ccapp.country.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CountryConfigFullResponse(
        JsonNode general,
        JsonNode membership,
        JsonNode mlmLimits,
        JsonNode guarantee,
        JsonNode financing,
        JsonNode accounting,
        JsonNode payment
) {
}
