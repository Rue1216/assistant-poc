package com.example.copilot.aiassistant.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.NoArgs;
import com.example.copilot.aiassistant.domain.tool.ToolContext;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import com.example.copilot.aiassistant.domain.tool.customer.GetCustomerTool;
import com.example.copilot.aiassistant.domain.tool.customer.GetRealTimeCustomerTool;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import dev.langchain4j.agent.tool.ToolSpecification;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowValidatorTest {

    private static final String PREFIX = "第 1 個工具標記";

    /** A fifth tool, so a fourth distinct tool can appear in a marker. */
    private static final class FakeTool implements AssistantTool<NoArgs> {
        @Override
        public String name() {
            return "fakeTool";
        }

        @Override
        public String purpose() {
            return "fake";
        }

        @Override
        public ToolSpecification specification() {
            return ToolSpecification.builder().name(name()).build();
        }

        @Override
        public Class<NoArgs> argsType() {
            return NoArgs.class;
        }

        @Override
        public AccessLevel requiredAccessLevel() {
            return AccessLevel.LIMITED;
        }

        @Override
        public Duration timeout() {
            return Duration.ofSeconds(1);
        }

        @Override
        public ToolResult execute(NoArgs args, ToolContext context) {
            return null;
        }
    }

    private final WorkflowValidator validator = new WorkflowValidator(new ToolRegistry(List.of(
            new GetCustomerTool(new PiiRedactor()), new GetRealTimeCustomerTool(new PiiRedactor()),
            new GetMarketIndexTool(null), new GetStockQuoteTool(null), new FakeTool())));

    private void assertSingle(ConfigType type, String content, String message) {
        List<ValidationError> errors = validator.validate(type, content);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).rule()).isEqualTo("WORKFLOW_INVALID");
        assertThat(errors.get(0).message()).isEqualTo(message);
    }

    private void assertInstruction(String content, String message) {
        assertSingle(ConfigType.SYSTEM_INSTRUCTION, content, message);
    }

    @Test
    void systemPromptRejectsMarkers() {
        assertSingle(ConfigType.SYSTEM_PROMPT, "{{getMarketIndex}}", "System Prompt 不可放工具標記，流程要設定在問題分類");
    }

    @Test
    void scopeSectionRejectsMarkers() {
        assertInstruction("## 適用問題\n{{getMarketIndex}}",
                "「## 適用問題」不可放工具標記（這段會交給路由或顯示成快捷按鈕）");
    }

    @Test
    void exampleSectionRejectsMarkers() {
        assertInstruction("## 範例問題\n- {{getMarketIndex}}",
                "「## 範例問題」不可放工具標記（這段會交給路由或顯示成快捷按鈕）");
    }

    @Test
    void unclosedMarkerNamesTheLine() {
        assertInstruction("## 回答套版\n第二行\n看 {{getMarketIndex", "第 3 行的工具標記沒有結尾");
    }

    @Test
    void malformedMarkerIsReported() {
        assertInstruction("{{姓名}}", "第 1 個工具標記格式不正確");
    }

    @Test
    void emptyMarkerDoesNotThrow() {
        assertInstruction("{{}}", "第 1 個工具標記格式不正確");
    }

    @Test
    void tooManyDistinctTools() {
        assertInstruction("{{getMarketIndex}}{{getRealTimeCustomer}}{{getStockQuote}}{{fakeTool}}",
                "工具標記最多 3 個不同的工具，目前有 4 個");
    }

    @Test
    void unknownTool() {
        assertInstruction("{{noSuchTool}}", PREFIX + "：找不到工具 noSuchTool");
    }

    @Test
    void toolWithOptionsNeedsOne() {
        assertInstruction("{{getCustomer}}", PREFIX + "：getCustomer 至少要選一個選項");
    }

    @Test
    void unknownOption() {
        assertInstruction("{{getCustomer: nope}}", PREFIX + "：getCustomer 沒有選項 nope");
    }

    @Test
    void toolWithoutOptionsRejectsOne() {
        assertInstruction("{{getMarketIndex: x}}", PREFIX + "：getMarketIndex 不接受選項");
    }

    @Test
    void validMarkersPass() {
        assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION,
                "{{getRealTimeCustomer}}\n{{getCustomer: management, behavior}}\n{{getStockQuote}}")).isEmpty();
    }

    @Test
    void repeatedToolIsAllowed() {
        assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION,
                "{{getMarketIndex}}\n{{getMarketIndex}}")).isEmpty();
        assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION,
                "{{getCustomer: management}}\n{{getCustomer: behavior}}")).isEmpty();
    }

    @Test
    void repeatsDoNotCountTowardTheLimit() {
        assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION,
                "{{getMarketIndex}}{{getRealTimeCustomer}}{{getStockQuote}}{{getMarketIndex}}")).isEmpty();
    }

    @Test
    void noMarkersPass() {
        assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION, "## 回答套版\n一般文字")).isEmpty();
    }

    @Test
    void userTextInMessagesIsClipped() {
        String longName = "a".repeat(40);
        assertInstruction("{{" + longName + "}}", PREFIX + "：找不到工具 " + "a".repeat(32));
        assertInstruction("{{getCustomer: " + longName + "}}", PREFIX + "：getCustomer 沒有選項 " + "a".repeat(32));
    }
}
