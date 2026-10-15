package com.tfzhang.linuxback.common.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogTest {

    private static final Path ZH = Path.of("src/main/resources/i18n/messages.properties");
    private static final Path EN = Path.of("src/main/resources/i18n/messages_en_US.properties");
    private static final Path JAVA_ROOT = Path.of("src/main/java");

    private static final Pattern MSG_T = Pattern.compile("Msg\\.t\\(\\s*\"([^\"]+)\"");
    private static final Pattern VALIDATION_KEY = Pattern.compile("\\{(validation\\.[a-zA-Z0-9.]+)\\}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");

    private static final List<String> CONCATENATED_KEYS = List.of(
            "export.grade.status.pending",
            "export.grade.status.grading_in_progress",
            "export.grade.status.completed",
            "export.grade.status.grading_failed",
            "export.grade.status.notSubmitted");

    @Test
    void catalogsHaveIdenticalKeysPlaceholdersAndEnglishHasNoHan() throws IOException {
        Properties zh = load(ZH);
        Properties en = load(EN);

        assertEquals(zh.stringPropertyNames(), en.stringPropertyNames());

        for (String key : zh.stringPropertyNames()) {
            String zhValue = zh.getProperty(key);
            String enValue = en.getProperty(key);
            assertEquals(placeholders(zhValue), placeholders(enValue), key);
            assertFalse(HAN.matcher(enValue).find(), () -> "en-US value for " + key + " contains CJK");
        }
    }

    @Test
    void everyReferencedKeyExists() throws IOException {
        Properties zh = load(ZH);
        Set<String> referenced = new LinkedHashSet<>(CONCATENATED_KEYS);
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String source;
                try {
                    source = Files.readString(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Matcher msg = MSG_T.matcher(source);
                while (msg.find()) {
                    referenced.add(msg.group(1));
                }
                Matcher validation = VALIDATION_KEY.matcher(source);
                while (validation.find()) {
                    referenced.add(validation.group(1));
                }
            });
        }
        for (String key : referenced) {
            if (key.endsWith(".")) {
                continue;
            }
            assertTrue(zh.containsKey(key), () -> "missing catalog key: " + key);
        }
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (var in = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            properties.load(in);
        }
        return properties;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
