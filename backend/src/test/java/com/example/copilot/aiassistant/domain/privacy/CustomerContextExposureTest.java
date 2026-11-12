package com.example.copilot.aiassistant.domain.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards that CustomerContext never leaks into serialized shapes, without relying on Jackson annotations. */
class CustomerContextExposureTest {

    private static final Path ROOT = Path.of("src/main/java/com/example/copilot/aiassistant");

    private static List<Path> javaFiles(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void dtosAndMongoDocumentsNeverMentionCustomerContext() throws IOException {
        List<Path> shapes = Stream.concat(
                javaFiles(ROOT.resolve("interfaces/rest")).stream()
                        .filter(p -> p.getParent().getFileName().toString().equals("dto")),
                javaFiles(ROOT.resolve("infrastructure/mongo")).stream()
                        .filter(p -> p.getFileName().toString().endsWith("Document.java"))).toList();
        assertThat(shapes).isNotEmpty();
        for (Path p : shapes) {
            assertThat(Files.readString(p)).as(p.toString()).doesNotContain("CustomerContext");
        }
    }

    @Test
    void domainDoesNotDependOnJacksonIgnore() throws IOException {
        for (Path p : javaFiles(ROOT.resolve("domain"))) {
            assertThat(Files.readString(p)).as(p.toString())
                    .doesNotContain("com.fasterxml.jackson.annotation.JsonIgnore");
        }
    }
}
