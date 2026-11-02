package com.example.copilot.aiassistant.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.infrastructure.config.EvalConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/** eval-run-validator.json repeats the code's storage limits; a change to one side must reach the other. */
class EvalRunValidatorLimitsTest {

    private static Document properties() throws IOException {
        try (InputStream in = EvalRunValidatorLimitsTest.class.getResourceAsStream("/mongo/eval-run-validator.json")) {
            assertThat(in).isNotNull();
            Document validator = Document.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return validator.get("$jsonSchema", Document.class).get("properties", Document.class);
        }
    }

    private static Document at(Document doc, String... path) {
        Document current = doc;
        for (String key : path) {
            current = current.get(key, Document.class);
        }
        return current;
    }

    @Test
    void caseCountMatchesStoredCasesMax() throws IOException {
        Document props = properties();
        int max = EvalConfiguration.STORED_CASES_MAX;

        assertThat(at(props, "cases").getInteger("maxItems")).isEqualTo(max);
        assertThat(at(props, "totalCases").getInteger("maximum")).isEqualTo(max);
        assertThat(at(props, "completedCases").getInteger("maximum")).isEqualTo(max);
    }

    @Test
    void caseResultLimitsMatchCaseResult() throws IOException {
        Document caseProps = at(properties(), "cases", "items", "properties");
        for (String side : List.of("baseline", "candidate")) {
            Document s = at(caseProps, side, "properties");
            assertThat(at(s, "answer").getInteger("maxLength")).as(side).isEqualTo(CaseResult.MAX_ANSWER);
            assertThat(at(s, "toolCalls").getInteger("maxItems")).as(side).isEqualTo(CaseResult.MAX_TOOL_CALLS);
            assertThat(at(s, "toolCalls", "items", "properties", "toolName").getInteger("maxLength")).as(side)
                    .isEqualTo(CaseResult.MAX_TOOL_NAME);
            assertThat(at(s, "unverifiedNumbers").getInteger("maxItems")).as(side)
                    .isEqualTo(CaseResult.MAX_UNVERIFIED);
        }
    }
}
