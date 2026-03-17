package com.ccapp.country.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CountryConfigFullResponse(
        JsonNode accounting,
        JsonNode mlm,
        JsonNode membership,
        JsonNode financing,
        JsonNode payment,
        JsonNode circle,
        JsonNode risk,
        JsonNode features
) {
}
