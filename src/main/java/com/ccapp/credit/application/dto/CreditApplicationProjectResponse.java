package com.ccapp.credit.application.dto;

public record CreditApplicationProjectResponse(
        String name,
        String sector,
        String description,
        String useOfFunds
) {
}
