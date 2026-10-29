package com.example.copilot.aiassistant.tool.customer;

import com.example.copilot.aiassistant.port.c360.RadarPoint;
import com.example.copilot.aiassistant.port.c360.RadarView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns a radar view (labels plus customer and segment indices) into facts the model can quote. */
public final class RadarFacts {

    private static final Logger log = LoggerFactory.getLogger(RadarFacts.class);

    private RadarFacts() {}

    /** Per label: item, index, optional value, segmentIndex, vsSegment (customer minus segment, signed). */
    public static List<Map<String, Object>> items(RadarView view, boolean withValue, boolean withHighlight) {
        int size = size(view);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            RadarPoint customer = view.customerData().get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("item", view.labels().get(i));
            item.put("index", customer.rank());
            if (withValue && customer.info() != null) {
                item.put("value", customer.info());
            }
            item.put("segmentIndex", view.segmentData().get(i).rank());
            item.put("vsSegment", signed(gap(view, i)));
            if (withHighlight && customer.highlight() != null) {
                item.put("highlight", customer.highlight());
            }
            items.add(item);
        }
        return items;
    }

    /** The {@code rank}-th highest customer index (1 is the highest) as item and index; null when there are fewer. */
    public static Map<String, Object> top(RadarView view, int rank) {
        // sorted() is stable, so equal indices keep the label order.
        List<Integer> order = IntStream.range(0, size(view)).boxed()
                .sorted(Comparator.comparingInt((Integer i) -> view.customerData().get(i).rank()).reversed())
                .toList();
        if (rank < 1 || rank > order.size()) {
            return null;
        }
        int i = order.get(rank - 1);
        Map<String, Object> top = new LinkedHashMap<>();
        top.put("item", view.labels().get(i));
        top.put("index", view.customerData().get(i).rank());
        return top;
    }

    /** Labels where the customer index is above the segment, the biggest gap first. */
    public static List<String> aboveSegment(RadarView view) {
        return IntStream.range(0, size(view)).boxed().filter(i -> gap(view, i) > 0)
                .sorted(Comparator.comparingInt((Integer i) -> gap(view, i)).reversed())
                .map(i -> view.labels().get(i)).toList();
    }

    /** Labels where the customer index is below the segment, the biggest gap first. */
    public static List<String> belowSegment(RadarView view) {
        return IntStream.range(0, size(view)).boxed().filter(i -> gap(view, i) < 0)
                .sorted(Comparator.comparingInt((Integer i) -> gap(view, i)))
                .map(i -> view.labels().get(i)).toList();
    }

    private static int gap(RadarView view, int i) {
        return view.customerData().get(i).rank() - view.segmentData().get(i).rank();
    }

    /** The shortest of the three lists; a mismatch is logged (counts only). */
    private static int size(RadarView view) {
        if (view == null || view.labels() == null || view.customerData() == null || view.segmentData() == null) {
            return 0;
        }
        int labels = view.labels().size();
        int customer = view.customerData().size();
        int segment = view.segmentData().size();
        if (labels != customer || labels != segment) {
            log.warn("radar length mismatch: labels={} customer={} segment={}", labels, customer, segment);
        }
        return Math.min(labels, Math.min(customer, segment));
    }

    private static String signed(int diff) {
        return diff > 0 ? "+" + diff : String.valueOf(diff);
    }
}
