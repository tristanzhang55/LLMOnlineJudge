package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.dto.TeacherDisputeInboxRow;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.SubmissionRoundStatus;
import com.tfzhang.linuxback.service.TeacherSubmissionService;
import com.tfzhang.linuxback.vo.TeacherDisputeHistoryItemVO;
import com.tfzhang.linuxback.vo.TeacherDisputeInboxItemVO;
import com.tfzhang.linuxback.vo.TeacherStudentSubmissionsVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionHistoryRoundVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionQuestionVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionRoundItemVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionSubQuestionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherSubmissionServiceImpl implements TeacherSubmissionService {

    private final SubmissionMapper submissionMapper;
    private final DisputeMapper disputeMapper;
    private final ExperimentService experimentService;
    private final UserMapper userMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final SubQuestionMapper subQuestionMapper;
    private final SubQuestionScoreMapper subQuestionScoreMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<TeacherSubmissionRoundItemVO> listRounds(
            int page, int pageSize, String studentName, Integer classId) {
        long teacherId = CurrentUser.requireId();
        int offset = (page - 1) * pageSize;
        String studentNameLike = toLikePattern(studentName);
        long total = submissionMapper.countTeacherSubmissionRounds(teacherId, classId, studentNameLike);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<TeacherSubmissionRoundItemVO> data = submissionMapper
                .findTeacherSubmissionRounds(teacherId, classId, studentNameLike, pageSize, offset)
                .stream()
                .map(this::toListItem)
                .toList();
        return PageResult.of(data, total);
    }

    @Override
    public PageResult<TeacherDisputeInboxItemVO> listDisputes(
            int page, int pageSize, String studentName,
            Integer classId, Integer experimentId, Integer disputeStatus) {
        if (disputeStatus != null && disputeStatus != 1 && disputeStatus != 2) {
            throw ExperimentException.invalidDisputeStatusFilter();
        }
        int statusFilter = disputeStatus == null ? 1 : disputeStatus;
        long teacherId = CurrentUser.requireId();
        int offset = (page - 1) * pageSize;
        String studentNameLike = toLikePattern(studentName);
        long total = disputeMapper.countTeacherDisputes(
                teacherId, classId, experimentId, studentNameLike, statusFilter);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        List<TeacherDisputeInboxItemVO> data = disputeMapper
                .findTeacherDisputes(
                        teacherId, classId, experimentId, studentNameLike, statusFilter, pageSize, offset)
                .stream()
                .map(this::toInboxItem)
                .toList();
        return PageResult.of(data, total);
    }

    @Override
    public TeacherStudentSubmissionsVO getStudentSubmissions(long experimentId, long studentId) {
        Experiment experiment = experimentService.requireOwnedExperiment(experimentId);
        User student = userMapper.selectById(studentId);
        if (student == null || experiment.getClassId() == null
                || !isEnrolled(experiment.getClassId(), studentId)) {
            throw ExperimentException.notFound();
        }

        TeacherStudentSubmissionsVO vo = new TeacherStudentSubmissionsVO();
        vo.setExperimentId(experimentId);
        vo.setStudentId(studentId);

        List<Submission> submissions = submissionMapper.findConfirmedByExperimentAndStudent(
                experimentId, studentId);
        if (submissions.isEmpty()) {
            vo.setRounds(List.of());
            return vo;
        }

        List<ExperimentQuestion> eqRows = loadActiveRows(experimentId);
        Map<Long, List<SubQuestion>> subQuestionsByQuestionId = loadSubQuestionsByQuestionId(
                eqRows.stream()
                        .map(ExperimentQuestion::getQuestionId)
                        .filter(qid -> qid != null)
                        .distinct()
                        .toList());
        Map<Long, Map<Long, SubQuestionScore>> scoresBySubmission = loadScoresBySubmission(submissions);
        Map<Long, List<Dispute>> disputesBySubmission = loadDisputesBySubmission(submissions);

        Map<LocalDateTime, Map<Long, Submission>> byRound = new LinkedHashMap<>();
        for (Submission submission : submissions) {
            byRound.computeIfAbsent(submission.getSubmitTime(), key -> new LinkedHashMap<>())
                    .putIfAbsent(submission.getExperimentQuestionId(), submission);
        }

        List<TeacherSubmissionHistoryRoundVO> rounds = new ArrayList<>();
        for (Map.Entry<LocalDateTime, Map<Long, Submission>> entry : byRound.entrySet()) {
            TeacherSubmissionHistoryRoundVO round = new TeacherSubmissionHistoryRoundVO();
            round.setSubmitTime(entry.getKey());
            List<TeacherSubmissionQuestionVO> questions = new ArrayList<>();
            for (ExperimentQuestion eq : eqRows) {
                questions.add(toQuestionVo(
                        eq,
                        entry.getValue().get(eq.getId()),
                        subQuestionsByQuestionId.get(eq.getQuestionId()),
                        scoresBySubmission,
                        disputesBySubmission));
            }
            round.setQuestions(questions);
            rounds.add(round);
        }
        vo.setRounds(rounds);
        return vo;
    }

    private TeacherSubmissionQuestionVO toQuestionVo(
            ExperimentQuestion eq,
            Submission submission,
            List<SubQuestion> bankRows,
            Map<Long, Map<Long, SubQuestionScore>> scoresBySubmission,
            Map<Long, List<Dispute>> disputesBySubmission) {
        TeacherSubmissionQuestionVO qvo = new TeacherSubmissionQuestionVO();
        qvo.setExperimentQuestionId(eq.getId());
        if (submission == null) {
            qvo.setSubmissionId(null);
            qvo.setStatus(null);
            qvo.setScore(null);
            qvo.setDisputes(List.of());
            qvo.setDisputeStatus(0);
            qvo.setSubQuestions(toSubQuestionVos(bankRows, null, Map.of()));
            return qvo;
        }
        qvo.setSubmissionId(submission.getId());
        qvo.setStatus(submission.getStatus());
        if (submission.getStatus() != null && submission.getStatus() == 2) {
            qvo.setScore(submission.getScore());
        } else {
            qvo.setScore(null);
        }
        applyDisputeHistory(qvo, disputesBySubmission.getOrDefault(submission.getId(), List.of()));
        Map<Long, SubQuestionScore> scores = scoresBySubmission.getOrDefault(submission.getId(), Map.of());
        qvo.setSubQuestions(toSubQuestionVos(bankRows, parseAnswerBlocks(submission.getAnswerBlocks()), scores));
        return qvo;
    }

    private static void applyDisputeHistory(TeacherSubmissionQuestionVO qvo, List<Dispute> disputes) {
        qvo.setDisputes(disputes.stream().map(TeacherSubmissionServiceImpl::toHistoryItem).toList());
        if (disputes.isEmpty()) {
            qvo.setDisputeStatus(0);
            return;
        }
        Dispute latest = disputes.get(0);
        qvo.setDisputeStatus(DisputeStatus.derive(latest));
        qvo.setDisputeReason(latest.getDisputeReason());
        qvo.setDisputeTime(latest.getDisputeTime());
        qvo.setManualComment(latest.getManualComment());
        qvo.setDisputeResolveTime(latest.getResolveTime());
    }

    private static TeacherDisputeHistoryItemVO toHistoryItem(Dispute dispute) {
        TeacherDisputeHistoryItemVO vo = new TeacherDisputeHistoryItemVO();
        vo.setId(dispute.getId());
        vo.setDisputeReason(dispute.getDisputeReason());
        vo.setDisputeTime(dispute.getDisputeTime());
        vo.setManualComment(dispute.getManualComment());
        vo.setResolveTime(dispute.getResolveTime());
        vo.setWithdrawnTime(dispute.getWithdrawnTime());
        return vo;
    }

    private TeacherDisputeInboxItemVO toInboxItem(TeacherDisputeInboxRow row) {
        TeacherDisputeInboxItemVO vo = new TeacherDisputeInboxItemVO();
        vo.setId(row.getId());
        vo.setSubmissionId(row.getSubmissionId());
        vo.setExperimentId(row.getExperimentId());
        vo.setExperimentTitle(row.getExperimentTitle());
        vo.setClassId(row.getClassId());
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setExperimentQuestionId(row.getExperimentQuestionId());
        vo.setQuestionTitle(row.getQuestionTitle());
        vo.setSubmissionStatus(row.getSubmissionStatus());
        vo.setScore(row.getScore());
        vo.setDisputeStatus(row.getDisputeStatus());
        vo.setDisputeReason(row.getDisputeReason());
        vo.setDisputeTime(row.getDisputeTime());
        vo.setManualComment(row.getManualComment());
        vo.setResolveTime(row.getResolveTime());
        vo.setIsManualOverride(row.getIsManualOverride() == null ? 0 : row.getIsManualOverride());
        return vo;
    }

    private List<TeacherSubmissionSubQuestionVO> toSubQuestionVos(
            List<SubQuestion> bankRows,
            JsonNode studentAnswerBlocks,
            Map<Long, SubQuestionScore> scores) {
        if (bankRows == null || bankRows.isEmpty()) {
            return List.of();
        }
        return bankRows.stream()
                .sorted(Comparator.comparing(SubQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SubQuestion::getId))
                .map(sq -> {
                    TeacherSubmissionSubQuestionVO svo = new TeacherSubmissionSubQuestionVO();
                    svo.setSubQuestionId(sq.getId());
                    svo.setContent(sq.getContent());
                    svo.setReferenceAnswer(sq.getReferenceAnswer());
                    svo.setRubric(sq.getRubric());
                    svo.setStudentAnswerBlocks(studentAnswerBlocks);
                    svo.setMaxScore(sq.getScore() == null ? 0 : sq.getScore());
                    SubQuestionScore row = scores.get(sq.getId());
                    if (row == null) {
                        svo.setAiScore(null);
                        svo.setEffectiveScore(null);
                        svo.setUsedSandbox(null);
                        svo.setAiEvidence(null);
                        svo.setGradingLog(null);
                        return svo;
                    }
                    svo.setAiScore(toInt(row.getAiScore()));
                    svo.setEffectiveScore(toInt(row.getEffectiveScore()));
                    svo.setUsedSandbox(toBoolean(row.getUsedSandbox()));
                    svo.setAiEvidence(row.getAiEvidence());
                    svo.setGradingLog(row.getGradingLog());
                    return svo;
                })
                .toList();
    }

    private TeacherSubmissionRoundItemVO toListItem(SubmissionRoundAggregateRow row) {
        String status = SubmissionRoundStatus.derive(row);
        TeacherSubmissionRoundItemVO vo = new TeacherSubmissionRoundItemVO();
        vo.setExperimentId(row.getExperimentId());
        vo.setExperimentTitle(row.getExperimentTitle());
        vo.setClassId(row.getClassId());
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setSubmitTime(row.getSubmitTime());
        vo.setQuestionCount(row.getQuestionCount() == null ? 0 : row.getQuestionCount().intValue());
        vo.setStatus(status);
        vo.setScore(SubmissionRoundStatus.scoreForStatus(status, row.getScoreSum()));
        return vo;
    }

    static String toLikePattern(String studentName) {
        if (studentName == null || studentName.isBlank()) {
            return null;
        }
        return "%" + ExperimentServiceImpl.escapeLike(studentName.trim()) + "%";
    }

    private boolean isEnrolled(Long classId, long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStudentId, studentId);
        return classStudentMapper.selectCount(wrapper) > 0;
    }

    private List<ExperimentQuestion> loadActiveRows(long experimentId) {
        LambdaQueryWrapper<ExperimentQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExperimentQuestion::getExperimentId, experimentId)
                .orderByAsc(ExperimentQuestion::getSortOrder, ExperimentQuestion::getId);
        return experimentQuestionMapper.selectList(wrapper);
    }

    private Map<Long, List<SubQuestion>> loadSubQuestionsByQuestionId(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SubQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SubQuestion::getQuestionId, questionIds);
        return subQuestionMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(SubQuestion::getQuestionId));
    }

    private Map<Long, Map<Long, SubQuestionScore>> loadScoresBySubmission(List<Submission> submissions) {
        List<Long> ids = submissions.stream()
                .map(Submission::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SubQuestionScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SubQuestionScore::getSubmissionId, ids);
        return subQuestionScoreMapper.selectList(wrapper).stream()
                .filter(row -> row.getSubmissionId() != null && row.getSubQuestionId() != null)
                .collect(Collectors.groupingBy(
                        SubQuestionScore::getSubmissionId,
                        Collectors.toMap(SubQuestionScore::getSubQuestionId, row -> row, (a, b) -> a)));
    }

    private Map<Long, List<Dispute>> loadDisputesBySubmission(List<Submission> submissions) {
        List<Long> ids = submissions.stream()
                .map(Submission::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<Dispute> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Dispute::getSubmissionId, ids)
                .orderByDesc(Dispute::getDisputeTime, Dispute::getId);
        Map<Long, List<Dispute>> bySubmission = new LinkedHashMap<>();
        for (Dispute dispute : disputeMapper.selectList(wrapper)) {
            if (dispute.getSubmissionId() == null) {
                continue;
            }
            bySubmission.computeIfAbsent(dispute.getSubmissionId(), key -> new ArrayList<>()).add(dispute);
        }
        return bySubmission;
    }

    private JsonNode parseAnswerBlocks(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static Integer toInt(BigDecimal value) {
        return value == null ? null : value.intValue();
    }

    private static Boolean toBoolean(Integer value) {
        if (value == null) {
            return null;
        }
        return value != 0;
    }
}
