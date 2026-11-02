package com.example.copilot.aiassistant.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerDeidentifierTest {

    @Test
    void ageRangeBands() {
        assertThat(CustomerDeidentifier.ageRange(18)).isEqualTo("18-24");
        assertThat(CustomerDeidentifier.ageRange(24)).isEqualTo("18-24");
        assertThat(CustomerDeidentifier.ageRange(25)).isEqualTo("25-34");
        assertThat(CustomerDeidentifier.ageRange(34)).isEqualTo("25-34");
        assertThat(CustomerDeidentifier.ageRange(35)).isEqualTo("35-44");
        assertThat(CustomerDeidentifier.ageRange(45)).isEqualTo("45-54");
        assertThat(CustomerDeidentifier.ageRange(54)).isEqualTo("45-54");
        assertThat(CustomerDeidentifier.ageRange(55)).isEqualTo("55-64");
        assertThat(CustomerDeidentifier.ageRange(58)).isEqualTo("55-64");
        assertThat(CustomerDeidentifier.ageRange(64)).isEqualTo("55-64");
        assertThat(CustomerDeidentifier.ageRange(65)).isEqualTo("65+");
    }
}
