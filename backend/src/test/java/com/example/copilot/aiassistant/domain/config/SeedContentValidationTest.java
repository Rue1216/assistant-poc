package com.example.copilot.aiassistant.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.copilot.aiassistant.domain.privacy.PiiRedactor;
import com.example.copilot.aiassistant.domain.prompt.ExampleQuestionParser;
import com.example.copilot.aiassistant.domain.prompt.WorkflowParser;
import com.example.copilot.aiassistant.domain.prompt.WorkflowStep;
import com.example.copilot.aiassistant.domain.tool.AssistantTool;
import com.example.copilot.aiassistant.domain.tool.ToolRegistry;
import com.example.copilot.aiassistant.domain.tool.customer.GetCustomerTool;
import com.example.copilot.aiassistant.domain.tool.customer.GetRealTimeCustomerTool;
import com.example.copilot.aiassistant.domain.tool.market.GetCompanyAnnouncementsTool;
import com.example.copilot.aiassistant.domain.tool.market.GetMarketIndexTool;
import com.example.copilot.aiassistant.domain.tool.market.GetStockQuoteTool;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Every bundled seed must pass the same validation an admin edit would, against the real tool set. */
class SeedContentValidationTest {

    private static final String SEED_DIR = "seed/";

    private final PiiRedactor redactor = new PiiRedactor();
    // Tools are built for metadata only; the gateways are never called.
    private final ConfigValidator validator = new ConfigValidator(new ExampleQuestionParser(), List.of(),
            new ToolRegistry(List.<AssistantTool<?>>of(new GetCustomerTool(redactor),
                    new GetRealTimeCustomerTool(redactor), new GetMarketIndexTool(null),
                    new GetStockQuoteTool(null), new GetCompanyAnnouncementsTool(null))));

    private static String read(String file) throws IOException {
        try (InputStream in = SeedContentValidationTest.class.getClassLoader()
                .getResourceAsStream(SEED_DIR + file)) {
            assertThat(in).as(file).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** instruction-template.md is an authoring template, not a seed, and its name does not match the glob. */
    private static List<String> instructionSeeds() throws IOException, URISyntaxException {
        Path dir = Paths.get(SeedContentValidationTest.class.getClassLoader().getResource("seed").toURI());
        List<String> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "instruction.*.md")) {
            stream.forEach(p -> files.add(p.getFileName().toString()));
        }
        return files;
    }

    @Test
    void systemPromptSeedIsValid() throws IOException {
        assertThat(validator.validate(ConfigType.SYSTEM_PROMPT, read("system.main.md"))).isEmpty();
    }

    @Test
    void instructionSeedsAreValid() throws IOException, URISyntaxException {
        List<String> files = instructionSeeds();
        assertThat(files).contains("instruction.contact-prep.md");
        for (String file : files) {
            assertThat(validator.validate(ConfigType.SYSTEM_INSTRUCTION, read(file))).as(file).isEmpty();
        }
    }

    @Test
    void contactPrepRunsRealTimeCustomerBeforeCustomer() throws IOException {
        List<WorkflowStep> steps = WorkflowParser.steps(read("instruction.contact-prep.md"));
        assertThat(steps.stream().map(WorkflowStep::toolName)).containsExactly("getRealTimeCustomer", "getCustomer");
    }
}
