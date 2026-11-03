package com.example.copilot.aiassistant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Pins the layer dependency rules by scanning import lines of the main sources. */
class ArchitectureRulesTest {

    private static final Path ROOT = Path.of("src/main/java/com/example/copilot/aiassistant");

    private static final String BASE = "com.example.copilot.aiassistant.";

    private static final Pattern PACKAGE_DECL =
            Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPLEMENTS_CLAUSE = Pattern.compile("\\bimplements\\s+([^{]+)\\{");

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

    private static final List<String> APPLICATION_FORBIDDEN = List.of(
            "org.springframework.data.",
            "org.springframework.transaction.",
            "com.mongodb.",
            "org.bson.",
            "jakarta.servlet.",
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
        assertThat(violations("domain", DOMAIN_FORBIDDEN, Set.of())).isEmpty();
    }

    @Test
    void applicationHasNoSpringDataOrMongoImports() {
        assertThat(violations("application", APPLICATION_FORBIDDEN, Set.of())).isEmpty();
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
    void implementationsEndWithImpl() {
        List<PersistenceImpl> found = persistenceImplementations();
        assertThat(found).as("persistence implementations found").isNotEmpty();
        assertThat(found.stream()
                        .filter(impl -> !impl.path().endsWith("Impl.java"))
                        .map(impl -> impl.path() + " implements " + impl.interfaceName())
                        .toList())
                .isEmpty();
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

    private record PersistenceImpl(String path, String interfaceName) {}

    /**
     * Infrastructure classes implementing a domain.repository interface or an application interface
     * named *QueryRepository. Interface names are resolved via the file's imports, else same package.
     */
    private static List<PersistenceImpl> persistenceImplementations() {
        Path dir = ROOT.resolve("infrastructure");
        assertThat(dir).isDirectory();
        List<PersistenceImpl> result = new ArrayList<>();
        for (Path file : javaFiles(dir)) {
            String source = read(file);
            Matcher implementsClause = IMPLEMENTS_CLAUSE.matcher(source);
            if (!implementsClause.find()) {
                continue;
            }
            Matcher pkg = PACKAGE_DECL.matcher(source);
            String pkgName = pkg.find() ? pkg.group(1) : "";
            List<String> imports = importLines(file).stream()
                    .map(ArchitectureRulesTest::importedName)
                    .map(name -> name.endsWith(";") ? name.substring(0, name.length() - 1).trim() : name)
                    .toList();
            for (String simple : simpleInterfaceNames(implementsClause.group(1))) {
                String fqcn = imports.stream()
                        .filter(name -> name.endsWith("." + simple))
                        .findFirst()
                        .orElse(pkgName + "." + simple);
                boolean repository = fqcn.startsWith(BASE + "domain.repository.");
                boolean queryRepository = fqcn.startsWith(BASE + "application.") && simple.endsWith("QueryRepository");
                if (repository || queryRepository) {
                    result.add(new PersistenceImpl(relative(ROOT, file), fqcn));
                }
            }
        }
        return result;
    }

    /** Splits an implements clause on top-level commas and strips generic arguments. */
    private static List<String> simpleInterfaceNames(String clause) {
        List<String> names = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : clause.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                names.add(current.toString().trim());
                current.setLength(0);
            } else if (depth == 0) {
                current.append(c);
            }
        }
        names.add(current.toString().trim());
        return names.stream()
                .filter(name -> !name.isEmpty())
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
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

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
