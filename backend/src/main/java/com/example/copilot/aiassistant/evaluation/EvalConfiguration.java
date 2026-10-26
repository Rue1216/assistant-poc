package com.example.copilot.aiassistant.evaluation;

import com.example.copilot.aiassistant.AiAssistantProperties;
import com.example.copilot.aiassistant.tool.ToolRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
public class EvalConfiguration {

    /** Loaded and validated at startup; an invalid dataset fails the application start. */
    @Bean
    public EvalDataset evalDataset(AiAssistantProperties props, ResourceLoader loader, ToolRegistry registry) {
        AiAssistantProperties.Eval eval = props.eval();
        Resource resource = loader.getResource(eval.dataset());
        byte[] bytes;
        try (InputStream in = resource.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read eval dataset " + eval.dataset(), e);
        }
        return EvalDatasetLoader.parse(bytes, registry.names(), eval.maxCases());
    }

    /** One run at a time: a single platform daemon thread. */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService evalExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("eval-runner").daemon(true).factory());
    }
}
