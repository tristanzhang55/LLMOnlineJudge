package com.tfzhang.linuxback.grading;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GradingWorkerService {

    private static final Logger log = LoggerFactory.getLogger(GradingWorkerService.class);

    private final SubmissionMapper submissionMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final SubQuestionMapper subQuestionMapper;
    private final SubQuestionGrader subQuestionGrader;
    private final GradingPersistenceService gradingPersistenceService;
    private final ObjectMapper objectMapper;

    public GradingOutcome handle(long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new PermanentGradingException("submission not found: " + submissionId);
        }
        if (submission.getStatus() == null || submission.getStatus() != 0) {
            return GradingOutcome.DUPLICATE;
        }
        int version = submission.getVersion() == null ? 0 : submission.getVersion();
        int claimed = submissionMapper.claimPending(submissionId, version);
        if (claimed == 0) {
            return GradingOutcome.DUPLICATE;
        }

        try {
            List<SubQuestion> subQuestions = loadSubQuestions(submission);
            JsonNode answerBlocks = parseAnswerBlocks(submission.getAnswerBlocks());
            List<SubQuestionScore> scores = new ArrayList<>();
            int sum = 0;
            LocalDateTime gradedAt = LocalDateTime.now();
            for (SubQuestion subQuestion : subQuestions) {
                JsonNode answer = findAnswer(answerBlocks, subQuestion.getId());
                SubQuestionGrader.GradeResult result = subQuestionGrader.grade(subQuestion, answer);
                SubQuestionScore row = new SubQuestionScore();
                row.setSubmissionId(submissionId);
                row.setSubQuestionId(subQuestion.getId());
                BigDecimal ai = BigDecimal.valueOf(result.aiScore());
                row.setAiScore(ai);
                row.setEffectiveScore(ai);
                row.setUsedSandbox(result.usedSandbox());
                row.setAiEvidence(result.aiEvidence());
                row.setGradingLog(result.gradingLog());
                row.setGradedAt(gradedAt);
                scores.add(row);
                sum += result.aiScore();
            }
            gradingPersistenceService.saveCompleted(submission, scores, sum);
            return GradingOutcome.COMPLETED;
        } catch (PermanentGradingException e) {
            log.warn("Permanent grading failure for submissionId={}: {}", submissionId, e.getMessage());
            gradingPersistenceService.markFailed(submissionId);
            return GradingOutcome.PERMANENT_FAILURE;
        }
    }

    private List<SubQuestion> loadSubQuestions(Submission submission) {
        if (submission.getExperimentQuestionId() == null) {
            throw new PermanentGradingException("submission has no experimentQuestionId");
        }
        ExperimentQuestion eq = experimentQuestionMapper.selectById(submission.getExperimentQuestionId());
        if (eq == null || eq.getQuestionId() == null) {
            throw new PermanentGradingException("experiment_question not found for submission");
        }
        LambdaQueryWrapper<SubQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubQuestion::getQuestionId, eq.getQuestionId())
                .orderByAsc(SubQuestion::getOrderIndex, SubQuestion::getId);
        List<SubQuestion> rows = new ArrayList<>(subQuestionMapper.selectList(wrapper));
        rows.sort(Comparator.comparing(SubQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SubQuestion::getId, Comparator.nullsLast(Long::compareTo)));
        return rows;
    }

    private JsonNode parseAnswerBlocks(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || node.isNull()) {
                return objectMapper.createArrayNode();
            }
            if (!node.isArray()) {
                throw new PermanentGradingException("answerBlocks is not a JSON array");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new PermanentGradingException("answerBlocks is not valid JSON", e);
        }
    }

    private static JsonNode findAnswer(JsonNode answerBlocks, Long subQuestionId) {
        if (answerBlocks == null || subQuestionId == null) {
            return null;
        }
        for (JsonNode item : answerBlocks) {
            if (item != null && item.has("subQuestionId")
                    && item.get("subQuestionId").isNumber()
                    && item.get("subQuestionId").asLong() == subQuestionId) {
                return item;
            }
        }
        return null;
    }
}
