package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ErrorResponse;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.dto.ExperimentCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentQuestionCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentStatusUpdateRequest;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.vo.ExperimentQuestionVO;
import com.tfzhang.linuxback.vo.ExperimentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExperimentServiceImpl implements ExperimentService {

    // i18n: persisted content, do not localize
    private static final String DISPUTE_AUTO_CLOSE_NOTE = "[系统] 作业已重新发布，原申诉自动关闭";

    private final ExperimentMapper experimentMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final ClassMapper classMapper;
    private final QuestionMapper questionMapper;
    private final SubQuestionMapper subQuestionMapper;
    private final DisputeMapper disputeMapper;
    private final SubmissionQuery submissionQuery;

    @Override
    public PageResult<ExperimentVO> listExperiments(
            int page, int pageSize, String title, Integer classId, Integer status) {
        persistExpiredPublished(null, CurrentUser.requireId());

        LambdaQueryWrapper<Experiment> wrapper = buildExperimentWrapper(title, classId, status);

        Page<Experiment> pageObj = new Page<>(page, pageSize);
        experimentMapper.selectPage(pageObj, wrapper);

        List<ExperimentVO> data = pageObj.getRecords().stream()
                .map(this::toExperimentVO)
                .toList();
        return PageResult.of(data, pageObj.getTotal());
    }

    @Override
    @Transactional
    public ExperimentVO createExperiment(ExperimentCreateRequest request) {
        List<ExperimentQuestionCreateRequest> rows = request.getQuestions();
        List<ErrorResponse.FieldError> fieldErrors = validateReferences(request.getClassId(), rows);
        if (!fieldErrors.isEmpty()) {
            throw ExperimentException.validationFailed(fieldErrors);
        }

        Experiment experiment = new Experiment();
        experiment.setTitle(request.getTitle());
        experiment.setDescription(request.getDescription());
        experiment.setClassId(request.getClassId());
        experiment.setDeadlineTime(request.getDeadlineTime());
        experiment.setStatus(0);
        experiment.setUserId(CurrentUser.requireId());
        experimentMapper.insert(experiment);

        Long experimentId = experiment.getId();
        for (int i = 0; i < rows.size(); i++) {
            ExperimentQuestionCreateRequest rowReq = rows.get(i);
            ExperimentQuestion row = new ExperimentQuestion();
            row.setExperimentId(experimentId);
            row.setQuestionId(rowReq.getQuestionId());
            row.setSortOrder(i);
            row.setPoints(resolvePoints(rowReq));
            experimentQuestionMapper.insert(row);
        }

        return loadExperimentVO(experimentId);
    }

    @Override
    public ExperimentVO getExperiment(long id) {
        loadExperimentOrThrow(id);
        persistExpiredPublished(id, CurrentUser.requireId());
        return loadExperimentVO(id);
    }

    @Override
    public Experiment requireOwnedExperiment(long id) {
        persistExpiredPublished(id, CurrentUser.requireId());
        return loadExperimentOrThrow(id);
    }

    @Override
    @Transactional
    public ExperimentVO updateExperiment(long id, ExperimentCreateRequest request) {
        loadExperimentOrThrow(id);
        List<ExperimentQuestion> currentRows = loadActiveRows(id);
        Map<Long, ExperimentQuestion> currentByQuestionId = currentRows.stream()
                .collect(Collectors.toMap(ExperimentQuestion::getQuestionId, eq -> eq));

        List<ExperimentQuestionCreateRequest> submitted = request.getQuestions();
        List<ErrorResponse.FieldError> fieldErrors = validateReferences(request.getClassId(), submitted);
        if (!fieldErrors.isEmpty()) {
            throw ExperimentException.validationFailed(fieldErrors);
        }

        Set<Long> submittedQuestionIds = submitted.stream()
                .map(ExperimentQuestionCreateRequest::getQuestionId)
                .collect(Collectors.toCollection(HashSet::new));

        for (ExperimentQuestion existing : currentRows) {
            if (!submittedQuestionIds.contains(existing.getQuestionId())
                    && submissionQuery.hasSubmissionsForExperimentQuestion(existing.getId())) {
                throw ExperimentException.questionRemovalBlocked();
            }
        }

        Experiment experiment = experimentMapper.selectById(id);
        experiment.setTitle(request.getTitle());
        experiment.setDescription(request.getDescription());
        experiment.setClassId(request.getClassId());
        experiment.setDeadlineTime(request.getDeadlineTime());
        experimentMapper.updateById(experiment);

        for (int i = 0; i < submitted.size(); i++) {
            ExperimentQuestionCreateRequest item = submitted.get(i);
            Long questionId = item.getQuestionId();

            ExperimentQuestion existing = currentByQuestionId.get(questionId);
            if (existing != null) {
                existing.setPoints(resolvePoints(item));
                existing.setSortOrder(i);
                experimentQuestionMapper.updateById(existing);
            } else {
                ExperimentQuestion row = new ExperimentQuestion();
                row.setExperimentId(id);
                row.setQuestionId(questionId);
                row.setPoints(resolvePoints(item));
                row.setSortOrder(i);
                experimentQuestionMapper.insert(row);
            }
        }

        for (ExperimentQuestion existing : currentRows) {
            if (!submittedQuestionIds.contains(existing.getQuestionId())) {
                experimentQuestionMapper.deleteById(existing.getId());
            }
        }

        return loadExperimentVO(id);
    }

    @Override
    @Transactional
    public void deleteExperiment(long id) {
        Experiment experiment = experimentMapper.selectById(id);
        if (experiment == null) {
            return;
        }
        if (experiment.getUserId() == null || experiment.getUserId() != CurrentUser.requireId()) {
            return;
        }

        LambdaQueryWrapper<ExperimentQuestion> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.eq(ExperimentQuestion::getExperimentId, id);
        experimentQuestionMapper.delete(childWrapper);

        LambdaQueryWrapper<Experiment> own = new LambdaQueryWrapper<>();
        own.eq(Experiment::getId, id).eq(Experiment::getUserId, CurrentUser.requireId());
        experimentMapper.delete(own);
    }

    @Override
    @Transactional
    public ExperimentVO updateExperimentStatus(long id, ExperimentStatusUpdateRequest request) {
        loadExperimentOrThrow(id);
        persistExpiredPublished(id, CurrentUser.requireId());

        Experiment experiment = experimentMapper.selectById(id);
        int target = request.getStatus();
        int current = experiment.getStatus();

        List<ErrorResponse.FieldError> fieldErrors = validateStatusTransition(current, target, id);
        if (!fieldErrors.isEmpty()) {
            throw ExperimentException.validationFailed(fieldErrors);
        }

        if (current != target) {
            LocalDateTime now = LocalDateTime.now();
            LambdaUpdateWrapper<Experiment> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Experiment::getId, id)
                    .eq(Experiment::getUserId, CurrentUser.requireId());
            if (target == 1) {
                wrapper.set(Experiment::getStatus, 1)
                        .set(Experiment::getLastPublishedTime, now);
            } else {
                wrapper.set(Experiment::getStatus, 0);
            }
            experimentMapper.update(null, wrapper);
            if (target == 0) {
                cascadeDisputeAutoClose(id, now);
            }
        }

        return loadExperimentVO(id);
    }

    @Override
    public void persistExpiredPublished(Long experimentId, Long userId) {
        persistExpiredPublishedInternal(experimentId, userId);
    }

    @Override
    public void expirePublishedPastDeadlines() {
        persistExpiredPublishedInternal(null, null);
    }

    void persistExpiredPublishedInternal(Long experimentId, Long userId) {
        LambdaUpdateWrapper<Experiment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Experiment::getStatus, 2)
                .eq(Experiment::getStatus, 1)
                .isNotNull(Experiment::getDeadlineTime)
                .lt(Experiment::getDeadlineTime, LocalDateTime.now());
        if (experimentId != null) {
            wrapper.eq(Experiment::getId, experimentId);
        }
        if (userId != null) {
            wrapper.eq(Experiment::getUserId, userId);
        }
        experimentMapper.update(null, wrapper);
    }

    List<ErrorResponse.FieldError> validateStatusTransition(int currentStatus, int targetStatus, long experimentId) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();

        if (targetStatus != 0 && targetStatus != 1) {
            fieldErrors.add(new ErrorResponse.FieldError("status", Msg.t("error.experiment.statusNotClientSettable")));
            return fieldErrors;
        }

        if (currentStatus == targetStatus) {
            return fieldErrors;
        }

        boolean allowed = switch (currentStatus) {
            case 0 -> targetStatus == 1;
            case 1 -> targetStatus == 0;
            case 2 -> targetStatus == 0;
            default -> false;
        };

        if (!allowed) {
            fieldErrors.add(new ErrorResponse.FieldError("status", Msg.t("error.experiment.invalidStatusTransition")));
            return fieldErrors;
        }

        if (targetStatus == 1 && !hasUsableQuestions(experimentId)) {
            fieldErrors.add(new ErrorResponse.FieldError(
                    "status", Msg.t("error.experiment.cannotPublishEmpty")));
        }

        return fieldErrors;
    }

    private void cascadeDisputeAutoClose(long experimentId, LocalDateTime now) {
        List<Dispute> pending = disputeMapper.findOpenByExperimentId(experimentId);
        for (Dispute open : pending) {
            if (open.getId() == null) {
                continue;
            }
            disputeMapper.casResolve(open.getId(), DISPUTE_AUTO_CLOSE_NOTE, now);
        }
    }

    private boolean hasUsableQuestions(long experimentId) {
        List<ExperimentQuestion> rows = loadActiveRows(experimentId);
        if (rows.isEmpty()) {
            return false;
        }
        List<Long> questionIds = rows.stream()
                .map(ExperimentQuestion::getQuestionId)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return false;
        }
        return !questionMapper.selectBatchIds(questionIds).isEmpty();
    }

    private Experiment loadExperimentOrThrow(long id) {
        Experiment experiment = experimentMapper.selectById(id);
        if (experiment == null || experiment.getUserId() == null
                || experiment.getUserId() != CurrentUser.requireId()) {
            throw ExperimentException.notFound();
        }
        return experiment;
    }

    private List<ExperimentQuestion> loadActiveRows(long experimentId) {
        LambdaQueryWrapper<ExperimentQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExperimentQuestion::getExperimentId, experimentId)
                .orderByAsc(ExperimentQuestion::getSortOrder, ExperimentQuestion::getId);
        return experimentQuestionMapper.selectList(wrapper);
    }

    List<ErrorResponse.FieldError> validateReferences(
            Long classId, List<ExperimentQuestionCreateRequest> rows) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();

        TeachingClass clazz = classId == null ? null : classMapper.selectById(classId);
        if (classId == null || clazz == null
                || clazz.getTeacherId() == null
                || clazz.getTeacherId() != CurrentUser.requireId()) {
            fieldErrors.add(new ErrorResponse.FieldError("classId", Msg.t("error.experiment.classIdMissing")));
        }

        List<Long> ids = rows.stream()
                .map(ExperimentQuestionCreateRequest::getQuestionId)
                .toList();
        Map<Long, Question> found = ids.isEmpty()
                ? Map.of()
                : questionMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        for (int i = 0; i < rows.size(); i++) {
            Long questionId = rows.get(i).getQuestionId();
            if (questionId != null && !found.containsKey(questionId)) {
                fieldErrors.add(new ErrorResponse.FieldError(
                        "questions[" + i + "].questionId", Msg.t("error.experiment.questionIdMissing")));
            }
        }

        Map<Long, Integer> firstIndexById = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Long questionId = rows.get(i).getQuestionId();
            if (questionId == null) {
                continue;
            }
            if (firstIndexById.containsKey(questionId)) {
                fieldErrors.add(new ErrorResponse.FieldError(
                        "questions[" + i + "].questionId", Msg.t("error.experiment.duplicateQuestionId")));
            } else {
                firstIndexById.put(questionId, i);
            }
        }

        return fieldErrors;
    }

    private ExperimentVO loadExperimentVO(long experimentId) {
        Experiment experiment = experimentMapper.selectById(experimentId);
        ExperimentVO vo = toExperimentVO(experiment);

        List<ExperimentQuestion> eqRows = loadActiveRows(experimentId);

        List<Long> questionIds = eqRows.stream()
                .map(ExperimentQuestion::getQuestionId)
                .distinct()
                .toList();
        Map<Long, Question> questions = questionIds.isEmpty()
                ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));
        Map<Long, Integer> subQuestionCounts = countSubQuestionsByQuestionId(questionIds);
        Set<Long> eqIdsWithSubmissions = submissionQuery.findExperimentQuestionIdsWithSubmissions(
                eqRows.stream().map(ExperimentQuestion::getId).toList());

        vo.setSubmittedStudentCount((int) submissionQuery.countDistinctStudentsForExperiment(experimentId));
        vo.setQuestions(eqRows.stream()
                .map(eq -> toExperimentQuestionVO(
                        eq, questions.get(eq.getQuestionId()), subQuestionCounts, eqIdsWithSubmissions))
                .toList());
        return vo;
    }

    private Map<Long, Integer> countSubQuestionsByQuestionId(List<Long> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<SubQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SubQuestion::getQuestionId, questionIds);
        return subQuestionMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(
                        SubQuestion::getQuestionId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private ExperimentQuestionVO toExperimentQuestionVO(
            ExperimentQuestion eq, Question question, Map<Long, Integer> subQuestionCounts,
            Set<Long> eqIdsWithSubmissions) {
        ExperimentQuestionVO vo = new ExperimentQuestionVO();
        vo.setId(eq.getId());
        vo.setExperimentId(eq.getExperimentId());
        vo.setQuestionId(eq.getQuestionId());
        if (question != null) {
            vo.setQuestionTitle(question.getTitle());
            vo.setQuestionTotalScore(question.getTotalScore());
            vo.setSubQuestionCount(subQuestionCounts.getOrDefault(eq.getQuestionId(), 0));
            vo.setQuestionDeleted(false);
        } else {
            vo.setQuestionDeleted(true);
            vo.setSubQuestionCount(0);
        }
        vo.setPoints(eq.getPoints());
        vo.setEffectivePoints(computeEffectivePoints(eq.getPoints(), question));
        vo.setSortOrder(eq.getSortOrder());
        vo.setHasSubmissions(eqIdsWithSubmissions.contains(eq.getId()));
        vo.setCreateTime(eq.getCreateTime());
        vo.setUpdateTime(eq.getUpdateTime());
        vo.setIsDelete(toBoolean(eq.getIsDelete()));
        return vo;
    }

    static int computeEffectivePoints(Integer points, Question question) {
        if (points == null || points == 0) {
            return question != null && question.getTotalScore() != null ? question.getTotalScore() : 0;
        }
        return points;
    }

    static int resolvePoints(ExperimentQuestionCreateRequest req) {
        Integer points = req.getPoints();
        return points == null ? 0 : points;
    }

    static LambdaQueryWrapper<Experiment> buildExperimentWrapper(
            String title, Integer classId, Integer status) {
        LambdaQueryWrapper<Experiment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Experiment::getUserId, CurrentUser.requireId());
        if (title != null && !title.isBlank()) {
            wrapper.like(Experiment::getTitle, escapeLike(title.trim()));
        }
        if (classId != null) {
            wrapper.eq(Experiment::getClassId, classId);
        }
        if (status != null) {
            wrapper.eq(Experiment::getStatus, status);
        }
        wrapper.orderByDesc(Experiment::getCreateTime, Experiment::getId);
        return wrapper;
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private ExperimentVO toExperimentVO(Experiment experiment) {
        ExperimentVO vo = new ExperimentVO();
        vo.setId(experiment.getId());
        vo.setTitle(experiment.getTitle());
        vo.setDescription(experiment.getDescription());
        vo.setClassId(experiment.getClassId());
        vo.setStatus(experiment.getStatus());
        vo.setUserId(experiment.getUserId());
        vo.setCreateTime(experiment.getCreateTime());
        vo.setUpdateTime(experiment.getUpdateTime());
        vo.setDeadlineTime(experiment.getDeadlineTime());
        vo.setLastPublishedTime(experiment.getLastPublishedTime());
        vo.setIsDelete(toBoolean(experiment.getIsDelete()));
        return vo;
    }

    private static Boolean toBoolean(Integer value) {
        return value != null && value == 1;
    }
}
