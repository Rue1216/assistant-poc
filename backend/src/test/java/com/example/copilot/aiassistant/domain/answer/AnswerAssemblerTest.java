package com.example.copilot.aiassistant.domain.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.copilot.TestCustomers;
import com.example.copilot.aiassistant.domain.exception.AssistantException;
import com.example.copilot.aiassistant.domain.exception.ErrorCode;
import com.example.copilot.aiassistant.domain.customer.AccessLevel;
import com.example.copilot.aiassistant.domain.privacy.CustomerContext;
import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.tool.ToolOutcome;
import com.example.copilot.aiassistant.domain.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AnswerAssemblerTest {

    private static final String DISCLAIMER_BLOCK = "※ 資訊僅供內部經營參考，不構成投資建議。";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AnswerAssembler assembler = new AnswerAssembler(new PiiRedactor(), mapper);
    private final CustomerContext customer =
            new CustomerContext(TestCustomers.RETIREE, AccessLevel.FULL, Set.of("王小明"), Set.of());

    private static ToolOutcome outcome(String id, String payload) {
        return new ToolOutcome("tool", "purpose", ToolResult.success(null, List.of()), payload, 1L, id);
    }

    private static ToolOutcome s(String id) {
        return outcome(id, "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"" + id
                + "\",\"data\":{\"close\":2420}}");
    }

    private String block(String type, String text, String... sources) {
        List<String> quoted = new ArrayList<>();
        for (String src : sources) {
            quoted.add("\"" + src + "\"");
        }
        return "{\"type\":\"" + type + "\",\"text\":" + mapper.writeValueAsString(text)
                + ",\"sources\":[" + String.join(",", quoted) + "]}";
    }

    private String json(String... blocks) {
        return "{\"blocks\":[" + String.join(",", blocks) + "]}";
    }

    private AssembledAnswer assemble(String modelText, ToolOutcome... successful) {
        return assembler.assemble(modelText, List.of(successful), customer, false);
    }

    @Test
    void invalidOutputIsFormatInvalid() {
        for (String bad : List.of("", "不是 JSON", "{}", "{\"blocks\":\"x\"}",
                "{\"blocks\":[{\"type\":\"quote\",\"text\":\"a\",\"sources\":[]}]}",
                "{\"blocks\":[{\"type\":\"paragraph\",\"sources\":[]}]}",
                "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"a\",\"sources\":[1]}]}",
                "{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"   \",\"sources\":[]}]}")) {
            assertThatThrownBy(() -> assemble(bad)).isInstanceOfSatisfying(AssistantException.class,
                    e -> assertThat(e.code()).isEqualTo(ErrorCode.AI_ANSWER_FORMAT_INVALID));
        }
    }

    @Test
    void blankBlocksAreDropped() {
        AssembledAnswer r = assemble(json(block("paragraph", "  "), block("paragraph", "內容")));
        assertThat(r.blocks()).hasSize(1);
        assertThat(r.answer()).isEqualTo("內容");
    }

    @Test
    void eachBlockIsRedactedAndSelfTokenReplaced() {
        AssembledAnswer r = assemble(json(block("paragraph", "王小明近期無新活動")));
        assertThat(r.blocks().get(0).text()).isEqualTo("本顧客近期無新活動");
        assertThat(r.answer()).doesNotContain("王小明").doesNotContain("【本顧客】");
        assertThat(r.annotatedAnswer()).doesNotContain("王小明").doesNotContain("【本顧客】");
    }

    @Test
    void unknownAndDuplicateSourcesAreCleaned() {
        AssembledAnswer r = assemble(json(block("paragraph", "內容", "S1", "S9", "S1")), s("S1"));
        assertThat(r.blocks().get(0).citations()).containsExactly(1);
    }

    @Test
    void headingSourcesAreCleared() {
        AssembledAnswer r = assemble(json(block("heading", "標題", "S1"), block("paragraph", "內容")), s("S1"));
        assertThat(r.blocks().get(0).citations()).isEmpty();
        assertThat(r.cited()).isEmpty();
    }

    @Test
    void inlineMarkersMoveIntoSources() {
        AssembledAnswer r = assemble(json(block("paragraph", "收盤 2420 元 [S1]【S2】（S3）")), s("S1"), s("S2"));
        assertThat(r.blocks().get(0).text()).isEqualTo("收盤 2420 元");
        assertThat(r.blocks().get(0).citations()).containsExactly(1, 2);
    }

    @Test
    void groupedAndLabelledMarkersMoveIntoSources() {
        AssembledAnswer grouped = assemble(json(block("paragraph", "收盤 2420 元 [S1, S2]，上漲")), s("S1"), s("S2"));
        assertThat(grouped.blocks().get(0).text()).isEqualTo("收盤 2420 元，上漲");
        assertThat(grouped.blocks().get(0).citations()).containsExactly(1, 2);

        for (String marker : List.of("[S1、S2]", "【S1，S2】", "（S1、S2）", "(S1,S2)")) {
            AssembledAnswer r = assemble(json(block("paragraph", "收盤 2420 元 " + marker + "。")), s("S1"), s("S2"));
            assertThat(r.blocks().get(0).text()).as(marker).isEqualTo("收盤 2420 元。");
            assertThat(r.blocks().get(0).citations()).as(marker).containsExactly(1, 2);
        }
        for (String marker : List.of("（來源：S1）", "(來源: S1)")) {
            AssembledAnswer r = assemble(json(block("paragraph", "收盤 2420 元 " + marker + "，上漲")), s("S1"));
            assertThat(r.blocks().get(0).text()).as(marker).isEqualTo("收盤 2420 元，上漲");
            assertThat(r.blocks().get(0).citations()).as(marker).containsExactly(1);
        }
        AssembledAnswer middle = assemble(json(block("paragraph", "收盤 [S1] 2420 元")), s("S1"));
        assertThat(middle.blocks().get(0).text()).isEqualTo("收盤 2420 元");
    }

    @Test
    void numbersFollowFirstUseThenUncitedInCallOrder() {
        AssembledAnswer r = assemble(json(block("paragraph", "a", "S2"), block("paragraph", "b", "S1")),
                s("S1"), s("S2"), s("S3"));
        assertThat(r.citationNumbers()).containsExactly(
                Map.entry("S2", 1), Map.entry("S1", 2), Map.entry("S3", 3));
        assertThat(r.cited()).containsExactlyInAnyOrder("S2", "S1");
    }

    @Test
    void trailingDisclaimerBlockIsDroppedWhenAsked() {
        String two = json(block("paragraph", "內容"), block("paragraph", DISCLAIMER_BLOCK));
        assertThat(assembler.assemble(two, List.of(), customer, true).blocks()).hasSize(1);
        assertThat(assembler.assemble(two, List.of(), customer, false).blocks()).hasSize(2);

        String only = json(block("paragraph", DISCLAIMER_BLOCK));
        assertThat(assembler.assemble(only, List.of(), customer, true).blocks()).hasSize(1);

        String withRule = json(block("paragraph", "內容"), block("paragraph", "---"),
                block("paragraph", DISCLAIMER_BLOCK));
        AssembledAnswer r = assembler.assemble(withRule, List.of(), customer, true);
        assertThat(r.blocks()).hasSize(1);
        assertThat(r.answer()).isEqualTo("內容");
    }

    @Test
    void moreThanFortyBlocksAreTruncated() {
        String[] blocks = new String[41];
        for (int i = 0; i < 41; i++) {
            blocks[i] = block("paragraph", "區塊" + i);
        }
        AssembledAnswer r = assemble(json(blocks));
        assertThat(r.blocks()).hasSize(40);
        assertThat(r.truncated()).isTrue();
        assertThat(assemble(json(block("paragraph", "a"))).truncated()).isFalse();
    }

    @Test
    void textOverEightThousandCharsIsTruncated() {
        String t = "字".repeat(3000);
        AssembledAnswer r = assemble(json(block("paragraph", t), block("paragraph", t), block("paragraph", t)));
        assertThat(r.blocks()).hasSize(3);
        assertThat(r.blocks().get(0).text()).hasSize(3000);
        assertThat(r.blocks().get(1).text()).hasSize(3000);
        assertThat(r.blocks().get(2).text()).hasSize(2000);
        assertThat(r.truncated()).isTrue();
    }

    @Test
    void disclaimerPrecededOnlyByARuleIsKept() {
        AssembledAnswer r = assembler.assemble(json(block("paragraph", "---"), block("paragraph", DISCLAIMER_BLOCK)),
                List.of(), customer, true);
        assertThat(r.blocks()).hasSize(2);
        assertThat(r.blocks().get(1).text()).contains("資訊僅供內部經營參考");

        AssembledAnswer headed = assembler.assemble(json(block("heading", "標題"), block("paragraph", "---"),
                block("paragraph", DISCLAIMER_BLOCK)), List.of(), customer, true);
        assertThat(headed.blocks()).hasSize(1);
        assertThat(headed.answer()).isEqualTo("### 標題");
    }

    @Test
    void customerNameCutByTheLimitLeavesNoFragment() {
        AssembledAnswer r = assemble(json(block("paragraph", "字".repeat(7998) + "王小明後續")));
        assertThat(r.truncated()).isTrue();
        assertThat(r.answer()).doesNotContain("王小").doesNotContain("王");
    }

    @Test
    void truncatedTextIsStrippedAgain() {
        AssembledAnswer r = assemble(json(block("paragraph", "字".repeat(7999) + " 後")));
        assertThat(r.truncated()).isTrue();
        assertThat(r.blocks().get(0).text()).hasSize(7999);
    }

    @Test
    void numberedPrefixKeepsALeadingDecimal() {
        AssembledAnswer r = assemble(json(block("numbered", "2.5% 成長"), block("numbered", "3) 項目")));
        assertThat(r.blocks()).extracting(AnswerBlock::text).containsExactly("2.5% 成長", "項目");
    }

    @Test
    void leadingMarkdownMarkersAreStripped() {
        AssembledAnswer r = assemble(json(block("bullet", "- 台積電"), block("numbered", "2. 鴻海"),
                block("heading", "### 小節")));
        assertThat(r.blocks()).extracting(AnswerBlock::text).containsExactly("台積電", "鴻海", "小節");
    }

    @Test
    void lineBreaksCollapseOutsideParagraphs() {
        AssembledAnswer r = assemble(json(block("bullet", "a\n b"), block("paragraph", "c\nd")));
        assertThat(r.blocks().get(0).text()).isEqualTo("a b");
        assertThat(r.blocks().get(1).text()).isEqualTo("c\nd");
    }

    @Test
    void flattensToMarkdown() {
        AssembledAnswer r = assemble(json(block("heading", "持有標的"), block("bullet", "A", "S1"),
                block("bullet", "B"), block("numbered", "C"), block("numbered", "D"),
                block("paragraph", "E [S1]")), s("S1"));
        assertThat(r.answer()).isEqualTo("### 持有標的\n\n- A\n- B\n\n1. C\n2. D\n\nE");
        assertThat(r.annotatedAnswer()).isEqualTo("### 持有標的\n\n- A [1]\n- B\n\n1. C\n2. D\n\nE [1]");
    }

    @Test
    void multipleCitationsAreAnnotatedSideBySide() {
        AssembledAnswer r = assemble(json(block("paragraph", "x", "S1", "S2")), s("S1"), s("S2"));
        assertThat(r.annotatedAnswer()).isEqualTo("x [1][2]");
    }

    private static final String NOTICE = "注意事項：本顧客有客訴註記，到期日 2026/12/31，請依規定程序處理。";

    private AssembledAnswer withNotices(String modelText, List<String> notices, ToolOutcome... successful) {
        return assembler.assemble(modelText, List.of(successful), customer, false, notices);
    }

    @Test
    void noticesComeFirstAsParagraphsWithoutCitations() {
        List<String> notices = List.of("第一則說明", "第二則說明");
        AssembledAnswer r = withNotices(json(block("paragraph", "內容", "S1")), notices, s("S1"));

        assertThat(r.blocks()).hasSize(3);
        assertThat(r.blocks().subList(0, 2)).allSatisfy(b -> {
            assertThat(b.type()).isEqualTo(BlockType.PARAGRAPH);
            assertThat(b.citations()).isEmpty();
        });
        assertThat(r.blocks()).extracting(AnswerBlock::text).containsExactly("第一則說明", "第二則說明", "內容");
        assertThat(r.blocks().get(2).citations()).containsExactly(1);
        assertThat(r.answer()).startsWith("第一則說明\n\n第二則說明\n\n");
        assertThat(r.annotatedAnswer()).startsWith("第一則說明\n\n第二則說明\n\n").endsWith("內容 [1]");
    }

    @Test
    void noticesAreNotTruncated() {
        AssembledAnswer r = withNotices(json(block("paragraph", "字".repeat(8001))), List.of(NOTICE));

        assertThat(r.truncated()).isTrue();
        assertThat(r.blocks().get(0).text()).isEqualTo(NOTICE);
        assertThat(r.blocks().get(1).text()).hasSize(8000);
    }

    @Test
    void noticesSkipTheNumberCheck() {
        AssembledAnswer r = withNotices(json(block("paragraph", "內容")), List.of(NOTICE));
        assertThat(r.unverifiedNumbers()).isEmpty();
    }

    @Test
    void specNoticeKeepsItsFullWidthParentheses() {
        String spec = "注意事項：本顧客有客訴註記（到期日 2026/12/31），請依規定程序處理。";
        AssembledAnswer r = withNotices(json(block("paragraph", "內容")), List.of(spec));

        assertThat(r.blocks().get(0).text()).isEqualTo(spec);
        assertThat(r.answer()).startsWith(spec + "\n\n");
        assertThat(r.annotatedAnswer()).startsWith(spec + "\n\n");
    }

    @Test
    void modelNumbersAreStillChecked() {
        AssembledAnswer r = withNotices(json(block("paragraph", "目標 3000")), List.of(NOTICE));
        assertThat(r.unverifiedNumbers()).containsExactly("3000");
    }

    @Test
    void programAnswerHasOnlyTheTexts() {
        AssembledAnswer r = assembler.programAnswer(List.of("甲", "乙"));

        assertThat(r.blocks()).extracting(AnswerBlock::text).containsExactly("甲", "乙");
        assertThat(r.blocks()).allSatisfy(b -> {
            assertThat(b.type()).isEqualTo(BlockType.PARAGRAPH);
            assertThat(b.citations()).isEmpty();
        });
        assertThat(r.answer()).isEqualTo("甲\n\n乙");
        assertThat(r.annotatedAnswer()).isEqualTo("甲\n\n乙");
        assertThat(r.citationNumbers()).isEmpty();
        assertThat(r.cited()).isEmpty();
        assertThat(r.unverifiedNumbers()).isEmpty();
        assertThat(r.truncated()).isFalse();
    }

    @Test
    void unverifiedNumbersComeFromNonHeadingBlocks() {
        AssembledAnswer r = assemble(json(block("heading", "2025 年回顧"),
                block("paragraph", "收盤 2420 元，目標 3000", "S1")), s("S1"));
        assertThat(r.unverifiedNumbers()).containsExactly("3000");
    }
}
