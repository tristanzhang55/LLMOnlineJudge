package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.JsonNode;
import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.entity.Attachment;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.grading.ocr.QwenVlOcrClient;
import com.tfzhang.linuxback.grading.sandbox.SandboxRunner;
import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.mapper.AttachmentMapper;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@ConditionalOnProperty(name = "app.grading.scorer", havingValue = "llm")
@RequiredArgsConstructor
public class LlmSubQuestionGrader implements SubQuestionGrader {

    // i18n: persisted content, do not localize
    static final String SYSTEM_PROMPT = """
            You are a strict teaching assistant grading one sub-question.
            Return JSON only, no markdown: {"score": <integer>, "evidence": "<short reason citing the rubric>"}.
            score must be an integer in [0, maxScore]. Do not exceed maxScore.
            Do not execute student code yourself.
            """;
    // i18n: persisted content, do not localize
    static final String EMPTY_EVIDENCE = "nothing submitted";
    // i18n: persisted content, do not localize
    static final String EVIDENCE_FALLBACK = "（模型未给出依据）";

    private final ChatModel gradingChatModel;
    private final QwenVlOcrClient qwenVlOcrClient;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentStorageService attachmentStorageService;
    private final SandboxRunner sandboxRunner;
    private final GradingProperties gradingProperties;

    @Override
    public GradeResult grade(SubQuestion subQuestion, JsonNode answerForSubQuestion) {
        int max = subQuestion.getScore() == null ? 0 : Math.max(0, subQuestion.getScore());
        String format = subQuestion.getAnswerFormat() == null
                ? ""
                : subQuestion.getAnswerFormat().toLowerCase();
        return switch (format) {
            case "image" -> gradeImage(subQuestion, answerForSubQuestion, max);
            case "code" -> gradeCode(subQuestion, answerForSubQuestion, max);
            default -> gradeText(subQuestion, answerForSubQuestion, max);
        };
    }

    private GradeResult gradeText(SubQuestion subQuestion, JsonNode answer, int max) {
        String student = extractTextContent(answer);
        if (student.isBlank()) {
            return emptyResult();
        }
        return scoreWithLlm(subQuestion, student, max, "text answer");
    }

    private GradeResult gradeImage(SubQuestion subQuestion, JsonNode answer, int max) {
        StringBuilder ocrAll = new StringBuilder();
        StringBuilder log = new StringBuilder();
        JsonNode blocks = answer == null ? null : answer.get("blocks");
        if (blocks == null || !blocks.isArray() || blocks.isEmpty()) {
            return emptyResult();
        }
        int imageCount = 0;
        for (JsonNode block : blocks) {
            if (block == null || !block.has("attachmentId") || !block.get("attachmentId").canConvertToLong()) {
                continue;
            }
            imageCount++;
            long attachmentId = block.get("attachmentId").asLong();
            Attachment row = attachmentMapper.selectById(attachmentId);
            if (row == null || row.getStorageKey() == null || row.getStorageKey().isBlank()) {
                log.append("attachment ").append(attachmentId).append(" missing; ");
                continue;
            }
            byte[] bytes;
            try {
                bytes = attachmentStorageService.read(row.getStorageKey());
            } catch (AttachmentException e) {
                log.append("attachment ").append(attachmentId).append(" unreadable; ");
                continue;
            }
            String ocr = qwenVlOcrClient.recognize(bytes, row.getMimeType());
            if (ocr == null || ocr.isBlank()) {
                log.append("OCR empty for attachment ").append(attachmentId).append("; ");
            } else {
                if (!ocrAll.isEmpty()) {
                    ocrAll.append("\n");
                }
                ocrAll.append(ocr.trim());
                log.append("OCR attachment ").append(attachmentId)
                        .append(" chars=").append(ocr.trim().length()).append("; ");
            }
        }
        if (imageCount == 0) {
            return emptyResult();
        }
        String ocrText = ocrAll.toString().trim();
        if (ocrText.isBlank()) {
            String evidence = "unreadable or empty OCR";
            return new GradeResult(0, evidence, 0, log.isEmpty() ? evidence : log.toString().trim());
        }
        GradeResult scored = scoreWithLlm(subQuestion, ocrText, max, "OCR text from student image");
        String combinedLog = (log + " " + scored.gradingLog()).trim();
        return new GradeResult(scored.aiScore(), scored.aiEvidence(), 0, combinedLog);
    }

    private GradeResult gradeCode(SubQuestion subQuestion, JsonNode answer, int max) {
        String source = extractCodeContent(answer);
        if (source.isBlank()) {
            return emptyResult();
        }
        String language = subQuestion.getLanguage();
        StringBuilder log = new StringBuilder();
        int usedSandbox = 0;
        boolean allow = subQuestion.getAllowSandbox() != null && subQuestion.getAllowSandbox() == 1;
        SandboxRunner.Result sandbox = null;
        if (!allow) {
            log.append("sandbox skipped because allowSandbox=0");
        } else {
            sandbox = sandboxRunner.run(source, language);
            if (sandbox.ran()) {
                usedSandbox = 1;
            }
            log.append(sandbox.log());
        }
        String payload;
        String payloadKind;
        if (sandbox != null && sandbox.ran() && sandbox.outcome() != null) {
            payload = codePayloadWithSandbox(language, source, sandbox.outcome());
            payloadKind = "student source code with sandbox stdout/stderr/compileError";
        } else {
            payload = codePayloadWithoutSandbox(language, source, log.toString());
            payloadKind = "student source code (sandbox not used as execution truth this pass)";
        }
        GradeResult scored = scoreWithLlm(subQuestion, payload, max, payloadKind);
        return new GradeResult(scored.aiScore(), scored.aiEvidence(), usedSandbox,
                (log + "; " + scored.gradingLog()).trim());
    }

    static String codePayloadWithSandbox(String language, String source, SandboxRunner.SandboxOutcome outcome) {
        StringBuilder payload = new StringBuilder();
        if (language != null && !language.isBlank()) {
            payload.append("language: ").append(language).append("\n");
        }
        payload.append(source);
        payload.append("\n\nstdout:\n").append(outcome.stdout() == null ? "" : outcome.stdout());
        payload.append("\nstderr:\n").append(outcome.stderr() == null ? "" : outcome.stderr());
        if (outcome.compileError() != null) {
            payload.append("\ncompileError:\n").append(outcome.compileError());
        }
        return payload.toString();
    }

    private static String codePayloadWithoutSandbox(String language, String source, String sandboxLog) {
        String payload = source;
        if (language != null && !language.isBlank()) {
            payload = "language: " + language + "\n" + source;
        }
        return payload + "\n\nSandbox: " + sandboxLog;
    }

    private GradeResult scoreWithLlm(SubQuestion subQuestion, String studentPayload, int max, String payloadKind) {
        String user = buildUserPrompt(subQuestion, studentPayload, max, payloadKind);
        String raw = chat(user);
        try {
            return toGradeResult(ScoreJsonParser.parse(raw), max);
        } catch (IllegalArgumentException first) {
            String retry = chat(user + "\nReturn JSON only with keys score and evidence.");
            try {
                return toGradeResult(ScoreJsonParser.parse(retry), max);
            } catch (IllegalArgumentException second) {
                throw new IllegalStateException("model output was not parseable as score JSON", second);
            }
        }
    }

    private String chat(String user) {
        long timeoutMs = Math.max(1, gradingProperties.getLlm().getTimeoutMs());
        try {
            return CompletableFuture.supplyAsync(() -> {
                ChatResponse response = gradingChatModel.chat(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(user));
                if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                    throw new IllegalStateException("empty chat model response");
                }
                return response.aiMessage().text();
            }).orTimeout(timeoutMs, TimeUnit.MILLISECONDS).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new IllegalStateException("chat model timed out", e);
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("chat model failed", e);
        }
    }

    private static GradeResult toGradeResult(ScoreJsonParser.ParsedScore parsed, int max) {
        int score = Math.max(0, Math.min(parsed.score(), max));
        String evidence = parsed.evidence() == null || parsed.evidence().isBlank()
                ? EVIDENCE_FALLBACK
                : parsed.evidence();
        return new GradeResult(score, evidence, 0, "llm scored");
    }

    private static GradeResult emptyResult() {
        return new GradeResult(0, EMPTY_EVIDENCE, 0, EMPTY_EVIDENCE);
    }

    static String buildUserPrompt(SubQuestion subQuestion, String studentPayload, int max, String payloadKind) {
        return """
                maxScore: %d
                stem:
                %s

                referenceAnswer:
                %s

                rubric:
                %s

                %s:
                %s
                """.formatted(
                max,
                nullToEmpty(subQuestion.getContent()),
                nullToEmpty(subQuestion.getReferenceAnswer()),
                nullToEmpty(subQuestion.getRubric()),
                payloadKind,
                studentPayload);
    }

    static String extractTextContent(JsonNode answer) {
        return joinBlockField(answer, "text", "content");
    }

    static String extractCodeContent(JsonNode answer) {
        String code = joinBlockField(answer, "code", "content");
        if (!code.isBlank()) {
            return code;
        }
        return joinAnyContent(answer);
    }

    private static String joinBlockField(JsonNode answer, String type, String field) {
        JsonNode blocks = answer == null ? null : answer.get("blocks");
        if (blocks == null || !blocks.isArray()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode block : blocks) {
            if (block == null || !block.has(field) || block.get(field).isNull()) {
                continue;
            }
            if (type != null && block.has("type") && !type.equalsIgnoreCase(block.get("type").asText(""))) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("\n");
            }
            out.append(block.get(field).asText("").trim());
        }
        return out.toString().trim();
    }

    private static String joinAnyContent(JsonNode answer) {
        JsonNode blocks = answer == null ? null : answer.get("blocks");
        if (blocks == null || !blocks.isArray()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (JsonNode block : blocks) {
            if (block == null || !block.has("content") || block.get("content").isNull()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("\n");
            }
            out.append(block.get("content").asText("").trim());
        }
        return out.toString().trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
