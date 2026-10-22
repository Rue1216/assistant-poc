package com.example.copilot;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    // Singleton container shared by all subclasses. Spring caches the context across test classes,
    // so the container must outlive any single class (Ryuk removes it when the JVM exits).
    // Testcontainers 1.x starts MongoDB as a single-node replica set, which transactions need.
    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8");

    static {
        MONGO.start();
    }

    @MockitoBean
    protected ChatModel chatModel;

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void llmProperties(DynamicPropertyRegistry registry) {
        registry.add("ai-assistant.llm.api-key", () -> "test-key");
        registry.add("ai-assistant.llm.model-name", () -> "test-model");
        registry.add("ai-assistant.config.scheduler-enabled", () -> "false");
    }
}
