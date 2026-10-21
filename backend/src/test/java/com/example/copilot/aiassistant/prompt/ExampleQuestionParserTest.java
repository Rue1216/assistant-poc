package com.example.copilot.aiassistant.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ExampleQuestionParserTest {

    private final ExampleQuestionParser parser = new ExampleQuestionParser();

    private String seed(String name) throws IOException {
        return new String(new ClassPathResource("seed/" + name).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void parsesExampleQuestions() throws IOException {
        assertThat(parser.parse(seed("instruction.contact-prep.md"))).containsExactly(
                "整理近期互動，幫我準備下次聯繫重點", "上次跟這位顧客聊了什麼？");
    }

    @Test
    void systemPromptHasNoQuestions() throws IOException {
        assertThat(parser.parse(seed("system.main.md"))).isEmpty();
    }

    @Test
    void stopsAtNextHeading() {
        String md = "## 範例問題\n\n- 一\n-  二  \n\n## 注意事項\n\n- 不是問題\n";
        assertThat(parser.parse(md)).containsExactly("一", "二");
    }
}
