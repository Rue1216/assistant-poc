package com.example.copilot.aiassistant.domain.customer.c360;

public record LumItems(
        CategoryData mtgPurchase,
        CategoryData mtgRevolve,
        CategoryData collateral,
        CategoryData creditLoan,
        CategoryData finAssetLoan,
        CategoryData ccBalance) {}
