package com.example.copilot.aiassistant.answer;

import com.example.copilot.aiassistant.common.AssistantException;
import com.example.copilot.aiassistant.common.ErrorCode;
import com.example.copilot.aiassistant.privacy.CustomerContext;
import com.example.copilot.aiassistant.privacy.CustomerDeidentifier;
import com.example.copilot.aiassistant.privacy.PiiRedactor;
import com.example.copilot.aiassistant.tool.ToolOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the model's final JSON ({@code {"blocks": [...]}}) into response blocks, flattened Markdown, citation
 * numbers and number-check results (answer-citations spec 2.3). Model text never goes into exceptions or logs.
 */
@Component
public class AnswerAssembler {

    public static final String MARKET_DISCLAIMER = "資訊僅供內部經營參考，不構成投資建議";

    private static final int MAX_BLOCKS = 40;
    private static final int MAX_CODE_POINTS = 8000;

    private static final String DECORATION = "[\\s\\u3000>*_\\-（）().。：:※\\u26A0\\uFE0F]*";
    private static final Pattern HORIZONTAL_RULE = Pattern.compile("\\s*([-*_])\\1{2,}\\s*");
    private static final Pattern DISCLAIMER_ONLY = Pattern.compile(
            DECORATION + Pattern.quote(MARKET_DISCLAIMER) + DECORATION);

    private static final Pattern MARKER = Pattern.compile("[\\[【(（](S\\d+)[\\]】)）]");
    private static final Pattern HEADING_PREFIX = Pattern.compile("^#+\\s*");
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[-*+•]\\s+");
    private static final Pattern NUMBERED_PREFIX = Pattern.compile("^\\d+[.)、](?!\\d)\\s*");
    private static final Pattern LINE_BREAK = Pattern.compile("\\s*\\n\\s*");

    private final PiiRedactor redactor;
    private final JsonMapper mapper;

    public AnswerAssembler(PiiRedactor redactor, JsonMapper mapper) {
        this.redactor = redactor;
        this.mapper = mapper;
    }

    /** A block whose sources are still S ids. */
    private record Draft(BlockType type, String text, List<String> sources) {
    }

    /**
     * @param successful this question's calls that were given a citation id, in call order
     * @param dropDisclaimer drop a last block that only repeats the investment disclaimer
     * @throws AssistantException AI_ANSWER_FORMAT_INVALID when the model output does not match the schema
     */
    public AssembledAnswer assemble(String modelText, List<ToolOutcome> successful, CustomerContext customer,
                                    boolean dropDisclaimer) {
        List<Draft> drafts = new ArrayList<>(parse(modelText));
        drafts.removeIf(d -> d.text().isEmpty());
        if (drafts.isEmpty()) {
            throw invalid();
        }

        // Redact before the length limit so a cut can never split a name and leave a fragment behind.
        for (int i = 0; i < drafts.size(); i++) {
            Draft d = drafts.get(i);
            String text = redactor.redact(d.text(), customer).replace(PiiRedactor.SELF, CustomerDeidentifier.SUBJECT);
            drafts.set(i, new Draft(d.type(), text, d.sources()));
        }

        boolean truncated = limit(drafts);
        drafts.replaceAll(d -> new Draft(d.type(), d.text().strip(), d.sources()));
        drafts.removeIf(d -> d.text().isEmpty());
        if (drafts.isEmpty()) {
            throw invalid();
        }

        Set<String> valid = new LinkedHashSet<>();
        for (ToolOutcome outcome : successful) {
            valid.add(outcome.citationId());
        }
        drafts.replaceAll(d -> new Draft(d.type(), d.text(), d.type() == BlockType.HEADING
                ? List.of() : d.sources().stream().filter(valid::contains).distinct().toList()));

        if (dropDisclaimer) {
            dropTrailingDisclaimer(drafts);
        }

        Map<String, Integer> numbers = new LinkedHashMap<>();
        for (Draft d : drafts) {
            for (String id : d.sources()) {
                numbers.putIfAbsent(id, numbers.size() + 1);
            }
        }
        Set<String> cited = new LinkedHashSet<>(numbers.keySet());
        for (String id : valid) {
            numbers.putIfAbsent(id, numbers.size() + 1);
        }

        List<AnswerBlock> blocks = new ArrayList<>();
        for (Draft d : drafts) {
            blocks.add(new AnswerBlock(d.type(), d.text(), d.sources().stream().map(numbers::get).toList()));
        }

        List<String> texts = blocks.stream().filter(b -> b.type() != BlockType.HEADING).map(AnswerBlock::text)
                .toList();
        List<String> unverified = NumberChecker.unverified(texts, toolData(successful));

        return new AssembledAnswer(blocks, flatten(blocks, false), flatten(blocks, true), numbers, cited,
                truncated, unverified);
    }

    private List<Draft> parse(String modelText) {
        JsonNode root;
        try {
            root = modelText == null ? null : mapper.readTree(modelText);
        } catch (RuntimeException e) {
            throw invalid();
        }
        if (root == null || !root.isObject() || !root.path("blocks").isArray()) {
            throw invalid();
        }
        List<Draft> drafts = new ArrayList<>();
        for (JsonNode node : root.get("blocks")) {
            if (!node.isObject() || !node.path("type").isString() || !node.path("text").isString()
                    || !node.path("sources").isArray()) {
                throw invalid();
            }
            BlockType type = BlockType.fromJson(node.get("type").asString()).orElseThrow(this::invalid);
            List<String> sources = new ArrayList<>();
            for (JsonNode source : node.get("sources")) {
                if (!source.isString()) {
                    throw invalid();
                }
                sources.add(source.asString());
            }
            drafts.add(clean(type, node.get("text").asString(), sources));
        }
        return drafts;
    }

    /** Moves inline [S1] markers into sources and strips Markdown markers the model wrote into the text. */
    private static Draft clean(BlockType type, String raw, List<String> sources) {
        List<String> all = new ArrayList<>(sources);
        Matcher m = MARKER.matcher(raw);
        while (m.find()) {
            all.add(m.group(1));
        }
        String text = MARKER.matcher(raw).replaceAll("").strip();
        text = switch (type) {
            case HEADING -> HEADING_PREFIX.matcher(text).replaceFirst("");
            case BULLET -> BULLET_PREFIX.matcher(text).replaceFirst("");
            case NUMBERED -> NUMBERED_PREFIX.matcher(text).replaceFirst("");
            case PARAGRAPH -> text;
        };
        if (type != BlockType.PARAGRAPH) {
            text = LINE_BREAK.matcher(text).replaceAll(" ");
        }
        return new Draft(type, text.strip(), all);
    }

    /** At most 40 blocks and 8000 code points in total; returns whether anything was cut. */
    private static boolean limit(List<Draft> drafts) {
        boolean truncated = false;
        if (drafts.size() > MAX_BLOCKS) {
            drafts.subList(MAX_BLOCKS, drafts.size()).clear();
            truncated = true;
        }
        int remaining = MAX_CODE_POINTS;
        for (int i = 0; i < drafts.size(); i++) {
            Draft d = drafts.get(i);
            int length = d.text().codePointCount(0, d.text().length());
            if (length <= remaining) {
                remaining -= length;
                continue;
            }
            truncated = true;
            if (remaining == 0) {
                drafts.subList(i, drafts.size()).clear();
            } else {
                String cut = d.text().substring(0, d.text().offsetByCodePoints(0, remaining));
                drafts.set(i, new Draft(d.type(), cut, d.sources()));
                drafts.subList(i + 1, drafts.size()).clear();
            }
            break;
        }
        return truncated;
    }

    /** The UI shows the disclaimer itself; a last block that only repeats it is dropped (never the only block). */
    private static void dropTrailingDisclaimer(List<Draft> drafts) {
        if (drafts.size() <= 1
                || !DISCLAIMER_ONLY.matcher(drafts.get(drafts.size() - 1).text()).matches()) {
            return;
        }
        int end = drafts.size() - 1;
        if (end > 1 && HORIZONTAL_RULE.matcher(drafts.get(end - 1).text()).matches()) {
            end--;
        }
        // Nothing but a rule would be left: keep the disclaimer rather than return an empty answer.
        if (end == 1 && HORIZONTAL_RULE.matcher(drafts.get(0).text()).matches()) {
            return;
        }
        drafts.subList(end, drafts.size()).clear();
    }

    private List<JsonNode> toolData(List<ToolOutcome> successful) {
        List<JsonNode> data = new ArrayList<>();
        for (ToolOutcome outcome : successful) {
            try {
                JsonNode node = mapper.readTree(outcome.payloadForModel()).get("data");
                if (node != null) {
                    data.add(node);
                }
            } catch (RuntimeException e) {
                // A payload we cannot read contributes no known numbers.
            }
        }
        return data;
    }

    private static String flatten(List<AnswerBlock> blocks, boolean annotate) {
        StringBuilder sb = new StringBuilder();
        BlockType previous = null;
        int number = 0;
        for (AnswerBlock block : blocks) {
            boolean list = block.type() == BlockType.BULLET || block.type() == BlockType.NUMBERED;
            if (previous != null) {
                sb.append(list && previous == block.type() ? "\n" : "\n\n");
            }
            number = block.type() == BlockType.NUMBERED ? (previous == BlockType.NUMBERED ? number + 1 : 1) : 0;
            switch (block.type()) {
                case HEADING -> sb.append("### ");
                case BULLET -> sb.append("- ");
                case NUMBERED -> sb.append(number).append(". ");
                case PARAGRAPH -> { }
            }
            sb.append(block.text());
            if (annotate && !block.citations().isEmpty()) {
                sb.append(' ');
                block.citations().forEach(n -> sb.append('[').append(n).append(']'));
            }
            previous = block.type();
        }
        return sb.toString();
    }

    private AssistantException invalid() {
        return new AssistantException(ErrorCode.AI_ANSWER_FORMAT_INVALID);
    }
}
