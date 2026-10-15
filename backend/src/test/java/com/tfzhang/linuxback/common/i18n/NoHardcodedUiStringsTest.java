package com.tfzhang.linuxback.common.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

class NoHardcodedUiStringsTest {

    private static final Path JAVA_ROOT = Path.of("src/main/java");
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Set<String> ALLOWLIST = Set.of(
            "ExperimentServiceImpl.java",
            "StudentDashboardServiceImpl.java",
            "SubQuestionScoreMapper.java",
            "LlmSubQuestionGrader.java",
            "FakeSubQuestionGrader.java",
            "GradeExcelRow.java");

    @Test
    void noCjkUiLiteralsOutsideAllowlist() throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !ALLOWLIST.contains(path.getFileName().toString()))
                    .flatMap(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            return lines.stream()
                                    .map(String::trim)
                                    .filter(line -> !line.startsWith("//")
                                            && !line.startsWith("*")
                                            && !line.startsWith("/*"))
                                    .filter(line -> HAN.matcher(line).find())
                                    .map(line -> path + ": " + line);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .toList();
        }
        if (!violations.isEmpty()) {
            fail("CJK UI literals must live in the catalog:\n" + String.join("\n", violations));
        }
    }
}
