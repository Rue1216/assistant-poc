package com.example.copilot.aiassistant.tool.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.port.c360.RadarPoint;
import com.example.copilot.aiassistant.port.c360.RadarView;
import com.example.copilot.testsupport.LogCapture;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;

class RadarFactsTest {

    private static List<RadarPoint> points(int... ranks) {
        return Arrays.stream(ranks).mapToObj(r -> new RadarPoint(r, null, null)).toList();
    }

    private static RadarView view(List<String> labels, int[] customer, int[] segment) {
        return new RadarView(labels, points(customer), points(segment), null);
    }

    private final RadarView view = view(List.of("A", "B", "C", "D"), new int[] {80, 50, 80, 20},
            new int[] {50, 60, 80, 30});

    @Test
    void itemsCarryIndexSegmentIndexAndSignedDifference() {
        List<Map<String, Object>> items = RadarFacts.items(view, false, false);

        assertThat(items).hasSize(4);
        assertThat(items.get(0)).containsEntry("item", "A").containsEntry("index", 80)
                .containsEntry("segmentIndex", 50).containsEntry("vsSegment", "+30")
                .doesNotContainKeys("value", "highlight");
        assertThat(items.get(1)).containsEntry("vsSegment", "-10");
        assertThat(items.get(2)).containsEntry("vsSegment", "0");
    }

    @Test
    void valueAndHighlightAreOptional() {
        RadarView v = new RadarView(List.of("A", "B"),
                List.of(new RadarPoint(70, "180 萬", null), new RadarPoint(40, null, true)),
                points(50, 50), null);

        assertThat(RadarFacts.items(v, true, true).get(0)).containsEntry("value", "180 萬")
                .doesNotContainKey("highlight");
        assertThat(RadarFacts.items(v, true, true).get(1)).containsEntry("highlight", true)
                .doesNotContainKey("value");
        assertThat(RadarFacts.items(v, false, false).get(0)).doesNotContainKey("value");
    }

    @Test
    void topPicksByRankAndBreaksTiesByLabelOrder() {
        assertThat(RadarFacts.top(view, 1)).isEqualTo(Map.of("item", "A", "index", 80));
        assertThat(RadarFacts.top(view, 2)).isEqualTo(Map.of("item", "C", "index", 80));
        assertThat(RadarFacts.top(view, 3)).isEqualTo(Map.of("item", "B", "index", 50));
        assertThat(RadarFacts.top(view, 5)).isNull();
    }

    @Test
    void aboveAndBelowSegmentAreOrderedByDifference() {
        RadarView v = view(List.of("A", "B", "C", "D"), new int[] {60, 90, 10, 55}, new int[] {50, 60, 30, 50});

        assertThat(RadarFacts.aboveSegment(v)).containsExactly("B", "A", "D");
        assertThat(RadarFacts.belowSegment(v)).containsExactly("C");
        assertThat(RadarFacts.aboveSegment(view)).containsExactly("A");
    }

    @Test
    void radarLengthMismatchUsesShorter() {
        RadarView v = view(List.of("A", "B", "C", "D", "E", "F"), new int[] {1, 2, 3, 4, 5},
                new int[] {1, 2, 3, 4, 5});

        try (LogCapture capture = LogCapture.of(RadarFacts.class, Level.WARN)) {
            assertThat(RadarFacts.items(v, false, false)).hasSize(5);

            assertThat(capture.events()).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getMessage().getFormattedMessage()).contains("radar length mismatch");
            });
        }
    }
}
