package com.example.copilot.aiassistant.domain.prompt;

import static com.example.copilot.aiassistant.domain.prompt.WorkflowParser.markers;
import static com.example.copilot.aiassistant.domain.prompt.WorkflowParser.render;
import static com.example.copilot.aiassistant.domain.prompt.WorkflowParser.steps;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowParserTest {

    @Test
    void parsesToolsWithAndWithoutOptions() {
        assertThat(steps("先看 {{getRealTimeCustomer}} 再看 {{getCustomer: management, behavior}}"))
                .containsExactly(new WorkflowStep("getRealTimeCustomer", List.of()),
                        new WorkflowStep("getCustomer", List.of("management", "behavior")));
    }

    @Test
    void acceptsFullWidthColonAndComma() {
        assertThat(steps("{{getCustomer：management，behavior}}"))
                .containsExactly(new WorkflowStep("getCustomer", List.of("management", "behavior")));
    }

    @Test
    void trimsWhitespace() {
        assertThat(steps("{{ getMarketIndex }}"))
                .containsExactly(new WorkflowStep("getMarketIndex", List.of()));
    }

    @Test
    void mergesDuplicatesAtTheFirstPosition() {
        assertThat(steps("{{A}} {{B}} {{A}}")).extracting(WorkflowStep::toolName).containsExactly("A", "B");
    }

    @Test
    void mergesOptionsAsUnionInFirstSeenOrder() {
        assertThat(steps("{{getCustomer: management}} {{getCustomer: behavior, management}}"))
                .containsExactly(new WorkflowStep("getCustomer", List.of("management", "behavior")));
    }

    @Test
    void noMarkersMeansNoSteps() {
        assertThat(steps("沒有標記")).isEmpty();
        assertThat(markers("沒有標記")).isEmpty();
    }

    @Test
    void markerLineIsOneBased() {
        assertThat(markers("第一行\n{{A}}").get(0).line()).isEqualTo(2);
    }

    @Test
    void wellFormedness() {
        assertThat(markers("{{姓名}}").get(0).wellFormed()).isFalse();
        assertThat(markers("{{A:}}").get(0).wellFormed()).isFalse();
        assertThat(markers("{{A: b,,c}}").get(0).wellFormed()).isFalse();
        assertThat(markers("{{A: b, c}}").get(0).wellFormed()).isTrue();
        assertThat(markers("{{A}}").get(0).wellFormed()).isTrue();
    }

    @Test
    void stepsKeepMalformedMarkersForTheGuardToReject() {
        assertThat(steps("{{姓名}}")).extracting(WorkflowStep::toolName).containsExactly("姓名");
    }

    @Test
    void renderReplacesMarkersWithToolName() {
        assertThat(render("看 {{getCustomer: management}}。", true)).isEqualTo("看 getCustomer（系統已查詢）。");
        assertThat(render("看 {{getCustomer: management}}。", false)).isEqualTo("看 getCustomer。");
    }
}
