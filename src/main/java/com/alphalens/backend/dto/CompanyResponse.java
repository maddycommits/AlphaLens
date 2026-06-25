package com.alphalens.backend.dto;

public record CompanyResponse(
        String ticker,
        String companyName,
        String sector
) {}