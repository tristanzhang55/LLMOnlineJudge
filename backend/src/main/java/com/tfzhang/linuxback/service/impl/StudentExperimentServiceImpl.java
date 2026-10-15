package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ErrorResponse;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.DisputeCreateRequest;
import com.tfzhang.linuxback.dto.StudentSubmitAnswerRequest;
import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.SubQuestionScore;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionScoreMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.StudentExperimentService;
import com.tfzhang.linuxback.service.SubmissionDraftService;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.vo.ConfirmSubmitItemVO;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.MyExperimentScoresVO;
import com.tfzhang.linuxback.vo.MyQuestionScoresVO;
import com.tfzhang.linuxback.vo.MySubQuestionScoreVO;
import com.tfzhang.linuxback.vo.MySubmissionQuestionStateVO;
import com.tfzhang.linuxback.vo.MySubmissionStateVO;
import com.tfzhang.linuxback.vo.StudentExperimentDetailVO;
import com.tfzhang.linuxback.vo.StudentExperimentListItem;
import com.tfzhang.linuxback.vo.StudentExperimentQuestionVO;
import com.tfzhang.linuxback.vo.StudentSubQuestionVO;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryAnswerVO;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryRoundVO;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentExperimentServiceImpl implements StudentExperimentService {

    private final ExperimentMapper experimentMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final ClassStudentMapper classStudentMapper;
    private final QuestionMapper questionMapper;
    private final SubQuestionMapper subQuestionMapper;
    private final SubQuestionScoreMapper subQuestionScoreMapper;
    private final SubmissionMapper submissionMapper;
    private final DisputeMapper disputeMapper;

    private final SubmissionQuery submissionQuery;
    private final SubmissionDraftService submissionDraftService;
    private final ExperimentService experimentService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<StudentExperimentListItem> listExperiments(int page, int pageSize) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(null, null);

        List<Long> classIds = loadStudentClassIds(studentId);
        if (classIds.isEmpty()) {
            return PageResult.of(List.of(), 0);
        }

        LambdaQueryWrapper<Experiment> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Experiment::getClassId, classIds)
                .in(Experiment::getStatus, 1, 2)
                .last("ORDER BY (deadlineTime IS NULL), deadlineTime ASC, id DESC");

        Page<Experiment> pageObj = new Page<>(page, pageSize);
        experimentMapper.selectPage(pageObj, wrapper);

        List<StudentExperimentListItem> data = pageObj.getRecords().stream()
                .map(exp -> toListItem(exp, studentId))
                .toList();
        return PageResult.of(data, pageObj.getTotal());
    }

    @Override
    public StudentExperimentDetailVO getExperiment(long id) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(id, null);
        Experiment experiment = loadVisibleExperimentOrThrow(id, studentId);
        return buildDetailVO(experiment, studentId);
    }

    @Override
    @Transactional
    public ConfirmSubmitVO finalizeSubmit(long id, StudentSubmitRequest request) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(id, null);
        Experiment experiment = loadVisibleExperimentOrThrow(id, studentId);

        if (experiment.getStatus() == null || experiment.getStatus() != 1) {
            throw ExperimentException.notOpenForSubmission();
        }
        if (submissionQuery.isLocked(id, studentId)) {
            throw ExperimentException.alreadySubmitted();
        }

        List<ExperimentQuestion> activeRows = loadActiveRows(id);
        Map<Long, ExperimentQuestion> byEqId = activeRows.stream()
                .collect(Collectors.toMap(ExperimentQuestion::getId, eq -> eq));

        Set<Long> seen = new HashSet<>();
        List<StudentSubmitAnswerRequest> answers = request.getAnswers();
        for (int i = 0; i < answers.size(); i++) {
            StudentSubmitAnswerRequest answer = answers.get(i);
            Long eqId = answer.getExperimentQuestionId();
            if (eqId == null || !byEqId.containsKey(eqId)) {
                throw ExperimentException.validationFailed(List.of(
                        new ErrorResponse.FieldError(
                                "answers[" + i + "].experimentQuestionId",
                                Msg.t("error.experiment.experimentQuestionMissing"))));
            }
            if (!seen.add(eqId)) {
                throw ExperimentException.validationFailed(List.of(
                        new ErrorResponse.FieldError("answers", Msg.t("error.experiment.duplicateExperimentQuestionId"))));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<ConfirmSubmitItemVO> items = new ArrayList<>();
        for (StudentSubmitAnswerRequest answer : answers) {
            long submissionId = submissionDraftService.persistDraftRow(
                    answer.getExperimentQuestionId(), studentId, toJson(answer.getAnswerBlocks()));
            LambdaUpdateWrapper<Submission> finalize = new LambdaUpdateWrapper<>();
            finalize.eq(Submission::getId, submissionId)
                    .eq(Submission::getStatus, -1)
                    .set(Submission::getStatus, 0)
                    .set(Submission::getSubmitTime, now);
            submissionMapper.update(null, finalize);
            items.add(new ConfirmSubmitItemVO(answer.getExperimentQuestionId(), submissionId));
        }

        for (ExperimentQuestion eq : activeRows) {
            if (seen.contains(eq.getId())) {
                continue;
            }
            Submission unused = submissionMapper.findDraft(eq.getId(), studentId);
            if (unused != null) {
                submissionMapper.deleteById(unused.getId());
            }
        }

        ConfirmSubmitVO vo = new ConfirmSubmitVO();
        vo.setSubmitTime(now);
        vo.setSubmissions(items);
        return vo;
    }

    @Override
    public MySubmissionStateVO getMySubmissionState(long id) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(id, null);
        Experiment experiment = loadVisibleExperimentOrThrow(id, studentId);

        boolean locked = submissionQuery.isLocked(id, studentId);
        boolean resubmissionEligible = submissionQuery.isResubmissionEligible(experiment, studentId);
        boolean editable = !locked && experiment.getStatus() != null && experiment.getStatus() == 1;

        MySubmissionStateVO vo = new MySubmissionStateVO();
        vo.setId(experiment.getId());
        vo.setStatus(experiment.getStatus());
        vo.setLocked(locked);
        vo.setResubmissionEligible(resubmissionEligible);
        vo.setEditable(editable);
        vo.setIsRoundGradingComplete(submissionQuery.isRoundGradingComplete(id, studentId));

        List<MySubmissionQuestionStateVO> questions = new ArrayList<>();
        for (ExperimentQuestion eq : loadActiveRows(id)) {
            questions.add(toQuestionState(eq.getId(), studentId, editable, resubmissionEligible));
        }
        vo.setQuestions(questions);
        return vo;
    }


    @Override
    public MyExperimentScoresVO getMyScores(long id) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(id, null);
        Experiment experiment = loadVisibleExperimentOrThrow(id, studentId);

        MyExperimentScoresVO vo = new MyExperimentScoresVO();
        vo.setExperimentId(experiment.getId());
        vo.setIsRoundGradingComplete(submissionQuery.isRoundGradingComplete(id, studentId));

        List<ExperimentQuestion> eqRows = loadActiveRows(id);

        List<Long> questionIds = eqRows.stream()
                .map(ExperimentQuestion::getQuestionId)
                .filter(qid -> qid != null)
                .distinct()
                .toList();

        Map<Long, List<SubQuestion>> subQuestionsByQuestionId = loadSubQuestionsByQuestionId(questionIds);

        List<MyQuestionScoresVO> questions = new ArrayList<>();
        for (ExperimentQuestion eq : eqRows) {
            questions.add(toQuestionScores(eq, studentId, subQuestionsByQuestionId));
        }
        vo.setQuestions(questions);
        disputeMapper.markRepliesRead(studentId, experiment.getId(), LocalDateTime.now());
        return vo;
    }

    @Override
    @Transactional
    public DisputeVO fileDispute(long experimentId, long submissionId, DisputeCreateRequest request) {
        long studentId = CurrentUser.requireId();
        experimentService.persistExpiredPublished(experimentId, null);
        loadVisibleExperimentOrThrow(experimentId, studentId);

        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw ExperimentException.notFound();
        }
        if (submission.getStudentId() == null || submission.getStudentId() != studentId) {
            throw ExperimentException.notFound();
        }

        ExperimentQuestion eq = experimentQuestionMapper.selectById(submission.getExperimentQuestionId());
        if (eq == null || eq.getExperimentId() == null || eq.getExperimentId() != experimentId) {
            throw ExperimentException.notFound();
        }

        Submission current = submissionQuery.getCurrentSubmission(eq.getId(), studentId);
        if (current == null || current.getId() == null || !current.getId().equals(submissionId)) {
            throw ExperimentException.notFound();
        }

        Integer status = submission.getStatus();
        if (status == null || (status != 2 && status != 3)) {
            throw ExperimentException.disputeNotGraded();
        }

        LambdaQueryWrapper<Submission> lock = new LambdaQueryWrapper<>();
        lock.eq(Submission::getId, submissionId).last("FOR UPDATE");
        Submission locked = submissionMapper.selectOne(lock);
        if (locked == null) {
            throw ExperimentException.notFound();
        }

        if (findOpenDispute(submissionId) != null) {
            throw ExperimentException.disputeAlreadyOpen();
        }

        LocalDateTime now = LocalDateTime.now();
        Dispute dispute = new Dispute();
        dispute.setSubmissionId(submissionId);
        dispute.setDisputeReason(request.getDisputeReason());
        dispute.setDisputeTime(now);
        dispute.setManualComment(null);
        dispute.setResolveTime(null);
        dispute.setWithdrawnTime(null);
        dispute.setIsDelete(0);
        disputeMapper.insert(dispute);

        return DisputeStatus.toVo(dispute);
    }

    private MyQuestionScoresVO toQuestionScores(
            ExperimentQuestion eq,
            long studentId,
            Map<Long, List<SubQuestion>> subQuestionsByQuestionId) {
        MyQuestionScoresVO qvo = new MyQuestionScoresVO();
        qvo.setExperimentQuestionId(eq.getId());

        Submission current = submissionQuery.getCurrentSubmission(eq.getId(), studentId);
        if (current == null) {
            qvo.setSubmissionId(null);
            qvo.setStatus(null);
            qvo.setScore(null);
            qvo.setDisputeStatus(0);
            qvo.setSubQuestions(List.of());
            return qvo;
        }

        qvo.setSubmissionId(current.getId());
        qvo.setStatus(current.getStatus());
        applyLatestDispute(qvo, current);

        if (current.getStatus() != null && current.getStatus() == 2) {
            qvo.setScore(current.getScore());
            qvo.setSubQuestions(toAwardedSubQuestions(
                    subQuestionsByQuestionId.get(eq.getQuestionId()),
                    current.getId()));
            return qvo;
        }

        qvo.setScore(null);
        qvo.setSubQuestions(List.of());
        return qvo;
    }

    private List<MySubQuestionScoreVO> toAwardedSubQuestions(
            List<SubQuestion> bankRows, long submissionId) {
        if (bankRows == null || bankRows.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SubQuestionScore> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubQuestionScore::getSubmissionId, submissionId);
        Map<Long, SubQuestionScore> bySubId = subQuestionScoreMapper.selectList(wrapper).stream()
                .filter(row -> row.getSubQuestionId() != null)
                .collect(Collectors.toMap(SubQuestionScore::getSubQuestionId, row -> row, (a, b) -> a));

        return bankRows.stream()
                .sorted(Comparator.comparing(SubQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SubQuestion::getId))
                .map(sq -> {
                    MySubQuestionScoreVO svo = new MySubQuestionScoreVO();
                    svo.setSubQuestionId(sq.getId());
                    svo.setMaxScore(sq.getScore() == null ? 0 : sq.getScore());
                    SubQuestionScore row = bySubId.get(sq.getId());
                    if (row == null) {
                        svo.setEffectiveScore(null);
                        svo.setComment("");
                        return svo;
                    }
                    svo.setEffectiveScore(row.getEffectiveScore() == null
                            ? null
                            : row.getEffectiveScore().intValue());
                    String evidence = row.getAiEvidence();
                    svo.setComment(evidence == null || evidence.isBlank() ? "" : evidence);
                    return svo;
                })
                .toList();
    }

    private MySubmissionQuestionStateVO toQuestionState(
            long experimentQuestionId, long studentId, boolean editable,
            boolean resubmissionEligible) {
        MySubmissionQuestionStateVO qvo = new MySubmissionQuestionStateVO();
        qvo.setExperimentQuestionId(experimentQuestionId);

        Submission draft = submissionQuery.findDraft(experimentQuestionId, studentId);
        if (draft != null) {
            qvo.setSource("draft");
            qvo.setSubmissionId(draft.getId());
            qvo.setAnswerBlocks(parseAnswerBlocks(draft.getAnswerBlocks()));
            return qvo;
        }

        Submission current = submissionQuery.getCurrentSubmission(experimentQuestionId, studentId);
        if (current != null) {
            qvo.setSource("current");
            qvo.setSubmissionId(current.getId());
            qvo.setAnswerBlocks(parseAnswerBlocks(current.getAnswerBlocks()));
            return qvo;
        }

        if (editable || resubmissionEligible) {
            Submission latest = submissionQuery.getLatestSubmissionForPrefill(experimentQuestionId, studentId);
            if (latest != null) {
                qvo.setSource("prefill");
                qvo.setSubmissionId(null);
                qvo.setAnswerBlocks(parseAnswerBlocks(latest.getAnswerBlocks()));
                return qvo;
            }
        }

        qvo.setSource("empty");
        qvo.setSubmissionId(null);
        qvo.setAnswerBlocks(null);
        return qvo;
    }

    private StudentExperimentDetailVO buildDetailVO(Experiment experiment, long studentId) {
        long experimentId = experiment.getId();
        boolean locked = submissionQuery.isLocked(experimentId, studentId);
        boolean resubmissionEligible = submissionQuery.isResubmissionEligible(experiment, studentId);
        boolean showPrefill = !locked && experiment.getStatus() != null && experiment.getStatus() == 1;

        StudentExperimentDetailVO vo = new StudentExperimentDetailVO();
        vo.setId(experiment.getId());
        vo.setTitle(experiment.getTitle());
        vo.setDescription(experiment.getDescription());
        vo.setClassId(experiment.getClassId());
        vo.setStatus(experiment.getStatus());
        vo.setDeadlineTime(experiment.getDeadlineTime());
        vo.setLastPublishedTime(experiment.getLastPublishedTime());
        vo.setLocked(locked);
        vo.setResubmissionEligible(resubmissionEligible);

        List<ExperimentQuestion> eqRows = loadActiveRows(experimentId);
        List<Long> questionIds = eqRows.stream().map(ExperimentQuestion::getQuestionId).distinct().toList();
        Map<Long, Question> questions = questionIds.isEmpty()
                ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, List<SubQuestion>> subQuestionsByQuestionId = loadSubQuestionsByQuestionId(questionIds);

        List<StudentExperimentQuestionVO> questionVos = new ArrayList<>();
        for (ExperimentQuestion eq : eqRows) {
            Question question = questions.get(eq.getQuestionId());
            StudentExperimentQuestionVO qvo = new StudentExperimentQuestionVO();
            qvo.setId(eq.getId());
            qvo.setExperimentId(eq.getExperimentId());
            qvo.setQuestionId(eq.getQuestionId());
            qvo.setSortOrder(eq.getSortOrder());
            if (question != null) {
                qvo.setQuestionTitle(question.getTitle());
                qvo.setQuestionContent(question.getContent());
            }
            qvo.setEffectivePoints(ExperimentServiceImpl.computeEffectivePoints(eq.getPoints(), question));
            qvo.setSubQuestions(toStudentSubQuestions(subQuestionsByQuestionId.get(eq.getQuestionId())));

            if (locked || (experiment.getStatus() != null && experiment.getStatus() == 2)) {
                Submission current = submissionQuery.getCurrentSubmission(eq.getId(), studentId);
                qvo.setCurrentAnswerBlocks(parseAnswerBlocks(current == null ? null : current.getAnswerBlocks()));
                qvo.setPrefillAnswerBlocks(null);
            } else if (showPrefill) {
                Submission latest = submissionQuery.getLatestSubmissionForPrefill(eq.getId(), studentId);
                qvo.setPrefillAnswerBlocks(parseAnswerBlocks(latest == null ? null : latest.getAnswerBlocks()));
                qvo.setCurrentAnswerBlocks(null);
            } else {
                qvo.setCurrentAnswerBlocks(null);
                qvo.setPrefillAnswerBlocks(null);
            }
            questionVos.add(qvo);
        }
        vo.setQuestions(questionVos);
        vo.setHistory(buildHistory(experimentId, studentId));
        return vo;
    }

    private List<StudentSubmissionHistoryRoundVO> buildHistory(long experimentId, long studentId) {
        List<Submission> prior = submissionMapper.findPriorRoundSubmissions(experimentId, studentId);
        if (prior.isEmpty()) {
            return List.of();
        }
        Map<LocalDateTime, List<Submission>> byTime = prior.stream()
                .collect(Collectors.groupingBy(
                        Submission::getSubmitTime,
                        LinkedHashMap::new,
                        Collectors.toList()));
        return byTime.entrySet().stream()
                .sorted(Map.Entry.<LocalDateTime, List<Submission>>comparingByKey().reversed())
                .map(entry -> {
                    StudentSubmissionHistoryRoundVO round = new StudentSubmissionHistoryRoundVO();
                    round.setSubmitTime(entry.getKey());
                    round.setRoundLabel(Msg.t("label.submissionHistory.roundLabel"));
                    round.setAnswers(entry.getValue().stream().map(s -> {
                        StudentSubmissionHistoryAnswerVO answer = new StudentSubmissionHistoryAnswerVO();
                        answer.setExperimentQuestionId(s.getExperimentQuestionId());
                        answer.setSubmitTime(s.getSubmitTime());
                        answer.setAnswerBlocks(parseAnswerBlocks(s.getAnswerBlocks()));
                        return answer;
                    }).toList());
                    return round;
                })
                .toList();
    }

    private List<StudentSubQuestionVO> toStudentSubQuestions(List<SubQuestion> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .sorted(Comparator.comparing(SubQuestion::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SubQuestion::getId))
                .map(sq -> {
                    StudentSubQuestionVO vo = new StudentSubQuestionVO();
                    vo.setId(sq.getId());
                    vo.setOrderIndex(sq.getOrderIndex());
                    vo.setContent(sq.getContent());
                    vo.setAnswerFormat(sq.getAnswerFormat());
                    vo.setLanguage(sq.getLanguage());
                    vo.setScore(sq.getScore());
                    return vo;
                })
                .toList();
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

    private StudentExperimentListItem toListItem(Experiment experiment, long studentId) {
        StudentExperimentListItem item = new StudentExperimentListItem();
        item.setId(experiment.getId());
        item.setTitle(experiment.getTitle());
        item.setDescription(experiment.getDescription());
        item.setClassId(experiment.getClassId());
        item.setStatus(experiment.getStatus());
        item.setDeadlineTime(experiment.getDeadlineTime());
        item.setLastPublishedTime(experiment.getLastPublishedTime());
        item.setLocked(submissionQuery.isLocked(experiment.getId(), studentId));
        item.setResubmissionEligible(submissionQuery.isResubmissionEligible(experiment, studentId));
        return item;
    }

    private void applyLatestDispute(MyQuestionScoresVO qvo, Submission current) {
        Dispute latest = findLatestDispute(current.getId());
        qvo.setDisputeStatus(DisputeStatus.derive(latest));
        if (latest == null) {
            return;
        }
        qvo.setDisputeId(latest.getId());
        qvo.setDisputeReason(latest.getDisputeReason());
        qvo.setDisputeTime(latest.getDisputeTime());
        qvo.setManualComment(latest.getManualComment());
        qvo.setDisputeResolveTime(latest.getResolveTime());
    }

    private Dispute findLatestDispute(long submissionId) {
        LambdaQueryWrapper<Dispute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dispute::getSubmissionId, submissionId)
                .orderByDesc(Dispute::getDisputeTime, Dispute::getId)
                .last("LIMIT 1");
        return disputeMapper.selectOne(wrapper);
    }

    private Dispute findOpenDispute(long submissionId) {
        LambdaQueryWrapper<Dispute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Dispute::getSubmissionId, submissionId)
                .isNull(Dispute::getResolveTime)
                .isNull(Dispute::getWithdrawnTime);
        return disputeMapper.selectOne(wrapper);
    }

    private Experiment loadVisibleExperimentOrThrow(long id, long studentId) {
        Experiment experiment = experimentMapper.selectById(id);
        if (experiment == null || experiment.getStatus() == null || experiment.getStatus() == 0) {
            throw ExperimentException.notFound();
        }
        if (experiment.getClassId() == null || !isEnrolled(experiment.getClassId(), studentId)) {
            throw ExperimentException.notFound();
        }
        if (experiment.getStatus() != 1 && experiment.getStatus() != 2) {
            throw ExperimentException.notFound();
        }
        return experiment;
    }

    private boolean isEnrolled(Long classId, long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getClassId, classId).eq(ClassStudent::getStudentId, studentId);
        return classStudentMapper.selectCount(wrapper) > 0;
    }

    private List<Long> loadStudentClassIds(long studentId) {
        LambdaQueryWrapper<ClassStudent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassStudent::getStudentId, studentId);
        return classStudentMapper.selectList(wrapper).stream()
                .map(ClassStudent::getClassId)
                .toList();
    }

    private List<ExperimentQuestion> loadActiveRows(long experimentId) {
        LambdaQueryWrapper<ExperimentQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExperimentQuestion::getExperimentId, experimentId)
                .orderByAsc(ExperimentQuestion::getSortOrder, ExperimentQuestion::getId);
        return experimentQuestionMapper.selectList(wrapper);
    }

    private String toJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ExperimentException.validationFailed(List.of(
                    new ErrorResponse.FieldError("answers", Msg.t("error.experiment.invalidAnswerBlocks"))));
        }
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
}
