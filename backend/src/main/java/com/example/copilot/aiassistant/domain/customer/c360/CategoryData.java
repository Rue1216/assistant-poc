package com.example.copilot.aiassistant.domain.customer.c360;

import java.util.List;

/** Both lists are ordered [customer, segment average, segment top 10]; ratios are percent-number strings. */
public record CategoryData(List<String> ratioList, List<Long> amountList) {}
