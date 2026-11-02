package com.example.copilot.aiassistant.infrastructure.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.domain.answer.AnswerSchema;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real OpenAI calls that check strict JSON Schema output (with a dynamic enum) works together with tool
 * definitions. Costs money, so it only runs with {@code -Dlive-llm=true}. Reads the key from backend/.env
 * (never printed) and the model name from application.yml.
 */
@EnabledIfSystemProperty(named = "live-llm", matches = "true")
class OpenAiResponseFormatLiveTest {

    private static final String QUESTION = "台積電(2330)最近收盤價？";
    private static final String TOOL_RESULT = "{\"untrustedData\":true,\"status\":\"SUCCESS\",\"citationId\":\"S1\","
            + "\"data\":{\"stockCode\":\"2330\",\"name\":\"台積電\",\"dataDate\":\"2026-10-16\",\"close\":2420}}";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static ChatModel model;

    @BeforeAll
    static void createModel() throws IOException {
        Properties env = new Properties();
        try (Reader in = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
            env.load(in);
        }
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        String modelName = yaml.getObject().getProperty("ai-assistant.llm.model-name");
        AiAssistantProperties.Llm llm = new AiAssistantProperties.Llm("openai", env.getProperty("OPENAI_API_KEY"),
                null, modelName, null, "none", null, null, Duration.ofSeconds(30), 1500);
        model = new LlmConfiguration().chatModel(new AiAssistantProperties(llm, null, null, null, null, null));
    }

    @Test
    void toolCallStillHappensWithSchema() {
        ChatResponse response = chat(List.of(UserMessage.from(QUESTION)), List.of());
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
    }

    @Test
    void answersWithEnumRestrictedSources() throws Exception {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(UserMessage.from(QUESTION));
        AiMessage first = chat(messages, List.of()).aiMessage();
        assertThat(first.hasToolExecutionRequests()).isTrue();
        messages.add(first);
        for (ToolExecutionRequest request : first.toolExecutionRequests()) {
            messages.add(ToolExecutionResultMessage.from(request, TOOL_RESULT));
        }

        JsonNode root = MAPPER.readTree(chat(messages, List.of("S1")).aiMessage().text());
        JsonNode blocks = root.get("blocks");
        assertThat(blocks).isNotNull();
        assertThat(blocks.isArray()).isTrue();
        boolean citesS1 = false;
        for (JsonNode block : blocks) {
            for (JsonNode source : block.get("sources")) {
                assertThat(source.asString()).isEqualTo("S1");
                citesS1 = true;
            }
        }
        assertThat(citesS1).as("at least one block cites S1").isTrue();
    }

    @Test
    void schemaWithoutEnumIsAccepted() throws Exception {
        ChatResponse response = chat(List.of(UserMessage.from("什麼是殖利率？")), List.of());
        JsonNode root = MAPPER.readTree(response.aiMessage().text());
        assertThat(root.get("blocks").isArray()).isTrue();
    }

    private static ChatResponse chat(List<ChatMessage> messages, List<String> citationIds) {
        return model.chat(ChatRequest.builder()
                .messages(messages)
                .toolSpecifications(new GetStockQuoteTool(null).specification())
                .responseFormat(AnswerSchema.responseFormat(citationIds))
                .build());
    }
}
