package com.example.copilot.aiassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pins the layer dependency rules by scanning import lines of the main sources. */
class ArchitectureRulesTest {

    private static final Path ROOT = Path.of("src/main/java/com/example/copilot/aiassistant");

    /** Temporary Phase 2 exemptions (relative to the domain layer); Task 7 deletes this. */
    private static final Set<String> PHASE2_EXEMPT = Set.of(
            "config/AssistantConfig.java",
            "repository/AssistantConfigRepository.java",
            "repository/EvalRunRepository.java",
            "audit/AuditRecord.java");

    private static final List<String> DOMAIN_FORBIDDEN = List.of(
            "org.springframework.",
            "com.mongodb.",
            "org.bson.",
            "jakarta.servlet.",
            "jakarta.annotation.",
            "com.example.copilot.aiassistant.AiAssistantProperties",
            "com.example.copilot.aiassistant.application.",
            "com.example.copilot.aiassistant.interfaces.",
            "com.example.copilot.aiassistant.infrastructure.",
            "com.example.copilot.mockplatform.");

    private static final List<String> INTERFACES_FORBIDDEN = List.of(
            "com.example.copilot.aiassistant.domain.repository.",
            "com.example.copilot.aiassistant.infrastructure.",
            "org.springframework.data.",
            "com.mongodb.",
            "org.bson.",
            "com.example.copilot.mockplatform.");

    private static final List<String> INFRASTRUCTURE_FORBIDDEN = List.of(
            "com.example.copilot.aiassistant.interfaces.",
            "com.example.copilot.mockplatform.");

    @Test
    void domainHasNoFrameworkImports() {
        assertThat(violations("domain", DOMAIN_FORBIDDEN, PHASE2_EXEMPT)).isEmpty();
    }

    @Test
    void interfacesDoNotTouchRepositoriesOrInfrastructure() {
        assertThat(violations("interfaces", INTERFACES_FORBIDDEN, Set.of())).isEmpty();
    }

    @Test
    void infrastructureDoesNotDependOnInterfaces() {
        assertThat(violations("infrastructure", INFRASTRUCTURE_FORBIDDEN, Set.of())).isEmpty();
    }

    @Test
    void onlyFourLayersUnderAiassistant() throws IOException {
        try (Stream<Path> children = Files.list(ROOT)) {
            Set<String> names = children
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
            assertThat(names).containsExactlyInAnyOrder(
                    "domain", "application", "interfaces", "infrastructure", "AiAssistantProperties.java");
        }
    }

    @Test
    void noAppPackage() {
        assertThat(javaFiles(ROOT).stream()
                        .map(p -> p.toString().replace(File.separatorChar, '/'))
                        .filter(p -> p.contains("/app/"))
                        .toList())
                .isEmpty();
    }

    /**
     * Returns "relative/path.java: import line" for every import in the layer that starts with a
     * forbidden prefix. Exempt paths are relative to the layer directory.
     */
    private static List<String> violations(String layer, List<String> forbiddenPrefixes, Set<String> exempt) {
        Path layerDir = ROOT.resolve(layer);
        assertThat(layerDir).isDirectory();
        return javaFiles(layerDir).stream()
                .filter(file -> !exempt.contains(relative(layerDir, file)))
                .flatMap(file -> importLines(file).stream()
                        .filter(line -> forbiddenPrefixes.stream().anyMatch(importedName(line)::startsWith))
                        .map(line -> relative(ROOT, file) + ": " + line))
                .toList();
    }

    private static String importedName(String importLine) {
        String name = importLine.substring("import ".length()).trim();
        if (name.startsWith("static ")) {
            name = name.substring("static ".length()).trim();
        }
        return name;
    }

    private static String relative(Path base, Path file) {
        return base.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static List<String> importLines(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.map(String::trim).filter(l -> l.startsWith("import ")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> javaFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
