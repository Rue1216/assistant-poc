package com.example.copilot.aiassistant.domain.customer.c360;

public record AumItems(
        CategoryData demandDeposit,
        CategoryData timeDeposit,
        CategoryData fund,
        CategoryData financialProduct,
        CategoryData insurance,
        CategoryData financialAsset) {}
