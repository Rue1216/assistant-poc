package com.example.copilot.aiassistant.chat;

import static com.example.copilot.aiassistant.chat.ScriptedChatModel.block;
import static com.example.copilot.aiassistant.chat.ScriptedChatModel.blocks;
import static com.example.copilot.aiassistant.chat.ScriptedChatModel.call;
import static com.example.copilot.aiassistant.chat.ScriptedChatModel.text;
import static com.example.copilot.aiassistant.chat.ScriptedChatModel.toolCalls;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.copilot.IntegrationTestBase;
import com.example.copilot.TestCustomers;
import com.example.copilot.TestUsers;
import com.jayway.jsonpath.JsonPath;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.nio.charset.StandardCharsets;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;

/** The chat API end to end: tool call, JSON answer with a citation, response body and audit document. */
class ChatCitationFlowTest extends IntegrationTestBase {

    private static final String URL = "/api/ai-assistant/customers/{id}/chat";
    private static final String AUDIT = "COLL_ICRM_AI_ASSISTANT_AUDIT";

    @Autowired
    MongoTemplate mongo;

    @Test
    void answerCitesToolResultInResponseAndAudit() throws Exception {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(
                text("{\"category\":\"none\"}", 10), // the routing round comes first
                toolCalls(100, call("c1", "getCustomerProfile", "{}")),
                text(blocks(block("paragraph", "本顧客屬退休客群", "S1")), 200));

        String body = mockMvc.perform(post(URL, TestCustomers.RETIREE).with(TestUsers.as("fc.officer")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"顧客概況？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()").value(containsInAnyOrder("requestId", "blocks", "answer",
                        "citations", "queriedAt", "disclaimer", "toolCalls")))
                .andExpect(jsonPath("$.blocks[0].type").value("paragraph"))
                .andExpect(jsonPath("$.blocks[0].text").value("本顧客屬退休客群"))
                .andExpect(jsonPath("$.blocks[0].citations[0]").value(1))
                .andExpect(jsonPath("$.answer").value("本顧客屬退休客群"))
                .andExpect(jsonPath("$.citations.length()").value(1))
                .andExpect(jsonPath("$.citations[0].id").value(1))
                .andExpect(jsonPath("$.citations[0].toolName").value("getCustomerProfile"))
                .andExpect(jsonPath("$.citations[0].title").value("個人360・顧客名片"))
                .andExpect(jsonPath("$.citations[0].target").value(nullValue()))
                .andExpect(jsonPath("$.citations[0].description").value(endsWith(" 個欄位")))
                .andExpect(jsonPath("$.citations[0].queriedAt").value(endsWith("+08:00")))
                .andExpect(jsonPath("$.citations[0].cited").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String requestId = JsonPath.read(body, "$.requestId");
        Document audit = mongo.getCollection(AUDIT).find(new Document("requestId", requestId)).first();
        assertThat(audit).isNotNull();
        assertThat(audit.getString("answer")).contains(" [1]");
        Document call = audit.getList("toolCalls", Document.class).get(0);
        assertThat(call.getInteger("citationId")).isEqualTo(1);
        assertThat(call.getString("resultHash")).hasSize(64);
        assertThat(call.containsKey("result")).isFalse();
        assertThat(audit.containsKey("unverifiedNumbers")).isTrue();
    }
}
