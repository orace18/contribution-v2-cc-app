package com.ccapp.mobile.sponsor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SponsorLookupRequest(

        @NotBlank
        @Size(min = 7, max = 20)
        String phoneNumber,

        @NotBlank
        @Size(min = 2, max = 3)
        String country
) {}
