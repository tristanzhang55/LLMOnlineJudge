package com.tfzhang.linuxback.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tfzhang.linuxback.config.GradingProperties;
import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.entity.Attachment;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.grading.ocr.QwenVlOcrClient;
import com.tfzhang.linuxback.grading.sandbox.SandboxRunner;
import com.tfzhang.linuxback.mapper.AttachmentMapper;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmSubQuestionGraderTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private QwenVlOcrClient ocrClient;
    @Mock
    private AttachmentMapper attachmentMapper;
    @Mock
    private AttachmentStorageService storageService;
    @Mock
    private SandboxRunner sandboxRunner;

    private LlmSubQuestionGrader grader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        grader = new LlmSubQuestionGrader(
                chatModel, ocrClient, attachmentMapper, storageService, sandboxRunner, new GradingProperties());
    }

    @Test
    void emptyText_scoresZeroWithoutLlm() {
        SubQuestion sq = subQuestion("text", 5);
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("subQuestionId", 1);
        answer.set("blocks", objectMapper.createArrayNode());

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(0, result.aiScore());
        assertEquals(LlmSubQuestionGrader.EMPTY_EVIDENCE, result.aiEvidence());
        verify(chatModel, never()).chat(any(), any());
    }

    @Test
    void text_clampsScoreToMax() {
        when(chatModel.chat(any(), any())).thenReturn(aiJson("{\"score\": 99, \"evidence\": \"too high\"}"));
        SubQuestion sq = subQuestion("text", 4);
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "text");
        block.put("content", "ls -la");
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(4, result.aiScore());
        assertEquals("too high", result.aiEvidence());
    }

    @Test
    void image_usesOcrThenLlm() {
        Attachment attachment = new Attachment();
        attachment.setId(5001L);
        attachment.setStorageKey("submission/x.png");
        attachment.setMimeType("image/png");
        when(attachmentMapper.selectById(5001L)).thenReturn(attachment);
        when(storageService.read("submission/x.png")).thenReturn(new byte[]{1, 2, 3});
        when(ocrClient.recognize(any(), anyString())).thenReturn("container started");
        when(chatModel.chat(any(), any())).thenReturn(aiJson("{\"score\": 2, \"evidence\": \"matches\"}"));
        SubQuestion sq = subQuestion("image", 5);
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "image");
        block.put("attachmentId", 5001);
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(2, result.aiScore());
        assertTrue(result.gradingLog().contains("OCR"));
        verify(ocrClient).recognize(any(), anyString());
    }

    @Test
    void image_missingFile_scoresZero() {
        Attachment attachment = new Attachment();
        attachment.setId(7L);
        attachment.setStorageKey("missing.png");
        when(attachmentMapper.selectById(7L)).thenReturn(attachment);
        when(storageService.read("missing.png"))
                .thenThrow(new AttachmentException("Failed to read file", "STORAGE_ERROR"));
        SubQuestion sq = subQuestion("image", 5);
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("attachmentId", 7);
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(0, result.aiScore());
        verify(chatModel, never()).chat(any(), any());
    }

    @Test
    void code_usedSandboxZeroWhenNoOp() {
        when(sandboxRunner.run(anyString(), anyString()))
                .thenReturn(new SandboxRunner.Result(false, "sandbox not wired"));
        when(chatModel.chat(any(), any())).thenReturn(aiJson("{\"score\": 1, \"evidence\": \"source only\"}"));
        SubQuestion sq = subQuestion("code", 3);
        sq.setAllowSandbox(1);
        sq.setLanguage("c");
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "code");
        block.put("language", "bash");
        block.put("content", "echo hi");
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(1, result.aiScore());
        assertEquals(0, result.usedSandbox());
        assertTrue(result.gradingLog().contains("sandbox not wired"));
        verify(sandboxRunner).run("echo hi", "c");
    }

    @Test
    void code_usedSandboxOneWhenRan_compileErrorInPrompt() {
        when(sandboxRunner.run(anyString(), anyString())).thenReturn(new SandboxRunner.Result(
                true,
                "{\"compileError\":\"expected ';'\",\"stdout\":\"\",\"stderr\":\"\",\"exitCode\":null,\"timedOut\":false}",
                new SandboxRunner.SandboxOutcome("expected ';'", "", "", null, false)));
        when(chatModel.chat(any(), any())).thenReturn(aiJson("{\"score\": 0, \"evidence\": \"did not compile\"}"));
        SubQuestion sq = subQuestion("code", 3);
        sq.setAllowSandbox(1);
        sq.setLanguage("c");
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "code");
        block.put("content", "int main(){return 0}");
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(1, result.usedSandbox());
        assertEquals(0, result.aiScore());
        verify(chatModel).chat(any(), argThat(msg -> {
            String prompt = String.valueOf(msg);
            if (msg instanceof UserMessage userMessage) {
                prompt = userMessage.singleText();
            }
            return prompt.contains("compileError")
                    && prompt.contains("expected ';'")
                    && prompt.contains("student source code with sandbox stdout/stderr/compileError")
                    && !prompt.contains("sandbox not used as execution truth this pass");
        }));
    }

    @Test
    void code_allowSandboxZero_doesNotCallRunner() {
        SubQuestion sq = subQuestion("code", 3);
        sq.setAllowSandbox(0);
        sq.setLanguage("bash");
        when(chatModel.chat(any(), any())).thenReturn(aiJson("{\"score\": 1, \"evidence\": \"ok\"}"));
        ObjectNode answer = objectMapper.createObjectNode();
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "code");
        block.put("content", "echo hi");
        blocks.add(block);
        answer.set("blocks", blocks);

        SubQuestionGrader.GradeResult result = grader.grade(sq, answer);

        assertEquals(0, result.usedSandbox());
        verify(sandboxRunner, never()).run(anyString(), anyString());
        assertTrue(result.gradingLog().contains("allowSandbox=0"));
    }

    private static SubQuestion subQuestion(String format, int score) {
        SubQuestion sq = new SubQuestion();
        sq.setId(1L);
        sq.setAnswerFormat(format);
        sq.setScore(score);
        sq.setContent("stem");
        sq.setReferenceAnswer("ref");
        sq.setRubric("rubric");
        return sq;
    }

    private static ChatResponse aiJson(String json) {
        return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
    }
}
