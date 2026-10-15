package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ErrorResponse;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.common.QuestionException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.dto.QuestionCreateRequest;
import com.tfzhang.linuxback.dto.QuestionUpdateRequest;
import com.tfzhang.linuxback.dto.SubQuestionCreateRequest;
import com.tfzhang.linuxback.dto.SubQuestionUpdateRequest;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.grading.sandbox.SandboxLanguage;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.service.AttachmentService;
import com.tfzhang.linuxback.service.MarkdownOwner;
import com.tfzhang.linuxback.service.QuestionService;
import com.tfzhang.linuxback.vo.QuestionVO;
import com.tfzhang.linuxback.vo.SandboxLanguageVO;
import com.tfzhang.linuxback.vo.SubQuestionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final QuestionMapper questionMapper;
    private final SubQuestionMapper subQuestionMapper;
    private final AttachmentService attachmentService;

    @Override
    public PageResult<QuestionVO> listQuestions(int page, int pageSize, String title) {
        LambdaQueryWrapper<Question> questionWrapper = buildQuestionWrapper(title);

        Page<Question> pageObj = new Page<>(page, pageSize);
        questionMapper.selectPage(pageObj, questionWrapper);

        List<Question> questions = pageObj.getRecords();
        if (questions.isEmpty()) {
            return PageResult.of(List.of(), pageObj.getTotal());
        }

        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .toList();

        LambdaQueryWrapper<SubQuestion> subQuestionWrapper = new LambdaQueryWrapper<>();
        subQuestionWrapper.in(SubQuestion::getQuestionId, questionIds)
                .orderByAsc(SubQuestion::getQuestionId, SubQuestion::getOrderIndex, SubQuestion::getId);
        List<SubQuestion> subQuestions = subQuestionMapper.selectList(subQuestionWrapper);

        Map<Long, List<SubQuestionVO>> subQuestionsByQuestionId = subQuestions.stream()
                .map(this::toSubQuestionVO)
                .collect(Collectors.groupingBy(SubQuestionVO::getQuestionId));

        List<QuestionVO> result = questions.stream()
                .map(q -> toQuestionVO(q, subQuestionsByQuestionId.getOrDefault(q.getId(), List.of())))
                .toList();

        return PageResult.of(result, pageObj.getTotal());
    }

    @Override
    public List<SandboxLanguageVO> listSandboxLanguages() {
        return SandboxLanguage.codes().stream()
                .map(code -> {
                    SandboxLanguageVO vo = new SandboxLanguageVO();
                    vo.setCode(code);
                    return vo;
                })
                .toList();
    }

    @Override
    public QuestionVO getQuestion(long qid) {
        loadQuestionOrThrow(qid);
        return loadQuestionVO(qid);
    }

    @Override
    @Transactional
    public QuestionVO createQuestion(QuestionCreateRequest request) {
        validateCreateLanguages(request.getSubQuestions());
        int totalScore = request.getSubQuestions().stream()
                .mapToInt(SubQuestionCreateRequest::getScore)
                .sum();

        Question question = new Question();
        applyQuestionFields(
                question,
                request.getTitle(),
                request.getContent(),
                request.getType(),
                request.getDifficulty(),
                request.getTags(),
                totalScore);
        question.setUserId(CurrentUser.requireId());
        questionMapper.insert(question);

        Long questionId = question.getId();

        attachmentService.attachMarkdownImages("question", questionId, request.getContent());

        for (int i = 0; i < request.getSubQuestions().size(); i++) {
            SubQuestionCreateRequest sqReq = request.getSubQuestions().get(i);
            SubQuestion subQuestion = new SubQuestion();
            subQuestion.setQuestionId(questionId);
            applySubQuestionFields(subQuestion, sqReq, i);
            subQuestionMapper.insert(subQuestion);

            attachmentService.attachMarkdownImages("sub_question", subQuestion.getId(), sqReq.getContent());
            if (StringUtils.hasText(sqReq.getReferenceAnswer())) {
                attachmentService.attachMarkdownImages(
                        "sub_question_answer", subQuestion.getId(), sqReq.getReferenceAnswer());
            }
        }

        return loadQuestionVO(questionId);
    }

    @Override
    @Transactional
    public QuestionVO updateQuestion(long qid, QuestionUpdateRequest request) {
        Question question = loadQuestionOrThrow(qid);
        List<SubQuestion> existingSubQuestions = loadSubQuestions(qid);
        Map<Long, SubQuestion> existingById = existingSubQuestions.stream()
                .collect(Collectors.toMap(SubQuestion::getId, sq -> sq));

        validateSubmittedSubQuestionIds(request, existingById);
        validateUpdateLanguages(request.getSubQuestions());

        List<MarkdownOwner> previousOwners = buildPreviousOwners(qid, existingSubQuestions);

        int totalScore = request.getSubQuestions().stream()
                .mapToInt(SubQuestionUpdateRequest::getScore)
                .sum();
        applyQuestionFields(
                question,
                request.getTitle(),
                request.getContent(),
                request.getType(),
                request.getDifficulty(),
                request.getTags(),
                totalScore);
        questionMapper.updateById(question);

        Set<Long> retainedIds = new HashSet<>();
        List<MarkdownOwner> desiredOwners = new ArrayList<>();
        desiredOwners.add(new MarkdownOwner("question", qid, request.getContent()));

        for (int i = 0; i < request.getSubQuestions().size(); i++) {
            SubQuestionUpdateRequest sqReq = request.getSubQuestions().get(i);
            SubQuestion subQuestion;
            if (sqReq.getId() != null) {
                subQuestion = existingById.get(sqReq.getId());
                applySubQuestionFields(subQuestion, sqReq, i);
                subQuestionMapper.updateById(subQuestion);
            } else {
                subQuestion = new SubQuestion();
                subQuestion.setQuestionId(qid);
                applySubQuestionFields(subQuestion, sqReq, i);
                subQuestionMapper.insert(subQuestion);
            }
            retainedIds.add(subQuestion.getId());

            desiredOwners.add(new MarkdownOwner("sub_question", subQuestion.getId(), sqReq.getContent()));
            if (StringUtils.hasText(sqReq.getReferenceAnswer())) {
                desiredOwners.add(new MarkdownOwner(
                        "sub_question_answer", subQuestion.getId(), sqReq.getReferenceAnswer()));
            }
        }

        for (SubQuestion existing : existingSubQuestions) {
            if (!retainedIds.contains(existing.getId())) {
                subQuestionMapper.deleteById(existing.getId());
            }
        }

        attachmentService.reconcileMarkdownImages(previousOwners, desiredOwners);

        return loadQuestionVO(qid);
    }

    @Override
    @Transactional
    public void deleteQuestion(long qid) {
        Question question = questionMapper.selectById(qid);
        if (question == null) {
            return;
        }

        List<SubQuestion> subQuestions = loadSubQuestions(qid);
        List<Long> subQuestionIds = subQuestions.stream()
                .map(SubQuestion::getId)
                .toList();

        attachmentService.softDeleteAggregateAttachments(qid, subQuestionIds);

        for (SubQuestion subQuestion : subQuestions) {
            subQuestionMapper.deleteById(subQuestion.getId());
        }

        questionMapper.deleteById(qid);
    }

    private Question loadQuestionOrThrow(long qid) {
        Question question = questionMapper.selectById(qid);
        if (question == null) {
            throw QuestionException.notFound();
        }
        return question;
    }

    private List<SubQuestion> loadSubQuestions(long questionId) {
        LambdaQueryWrapper<SubQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SubQuestion::getQuestionId, questionId)
                .orderByAsc(SubQuestion::getOrderIndex, SubQuestion::getId);
        return subQuestionMapper.selectList(wrapper);
    }

    private QuestionVO loadQuestionVO(long questionId) {
        Question question = questionMapper.selectById(questionId);
        List<SubQuestion> subQuestions = loadSubQuestions(questionId);
        List<SubQuestionVO> subQuestionVOs = subQuestions.stream()
                .map(this::toSubQuestionVO)
                .toList();
        return toQuestionVO(question, subQuestionVOs);
    }

    private static List<MarkdownOwner> buildPreviousOwners(long qid, List<SubQuestion> existingSubQuestions) {
        List<MarkdownOwner> owners = new ArrayList<>();
        owners.add(new MarkdownOwner("question", qid));
        for (SubQuestion subQuestion : existingSubQuestions) {
            owners.add(new MarkdownOwner("sub_question", subQuestion.getId()));
            owners.add(new MarkdownOwner("sub_question_answer", subQuestion.getId()));
        }
        return owners;
    }

    private static void validateSubmittedSubQuestionIds(
            QuestionUpdateRequest request,
            Map<Long, SubQuestion> existingById) {
        Map<Long, Integer> firstIndexById = new HashMap<>();
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();

        List<SubQuestionUpdateRequest> subQuestions = request.getSubQuestions();
        for (int i = 0; i < subQuestions.size(); i++) {
            Long id = subQuestions.get(i).getId();
            if (id == null) {
                continue;
            }
            if (firstIndexById.containsKey(id)) {
                String duplicate = Msg.t("error.question.duplicateSubQuestionId");
                fieldErrors.add(new ErrorResponse.FieldError(
                        "subQuestions[" + i + "].id", duplicate));
                fieldErrors.add(new ErrorResponse.FieldError(
                        "subQuestions[" + firstIndexById.get(id) + "].id", duplicate));
                continue;
            }
            firstIndexById.put(id, i);
            if (!existingById.containsKey(id)) {
                fieldErrors.add(new ErrorResponse.FieldError(
                        "subQuestions[" + i + "].id",
                        Msg.t("error.question.subQuestionNotBelong")));
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw QuestionException.validationFailed(fieldErrors);
        }
    }

    private static void validateCreateLanguages(List<SubQuestionCreateRequest> subQuestions) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (int i = 0; i < subQuestions.size(); i++) {
            SubQuestionCreateRequest sq = subQuestions.get(i);
            addLanguageFieldError(fieldErrors, i, sq.getAnswerFormat(), sq.getLanguage());
        }
        if (!fieldErrors.isEmpty()) {
            throw QuestionException.validationFailed(fieldErrors);
        }
    }

    private static void validateUpdateLanguages(List<SubQuestionUpdateRequest> subQuestions) {
        List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
        for (int i = 0; i < subQuestions.size(); i++) {
            SubQuestionUpdateRequest sq = subQuestions.get(i);
            addLanguageFieldError(fieldErrors, i, sq.getAnswerFormat(), sq.getLanguage());
        }
        if (!fieldErrors.isEmpty()) {
            throw QuestionException.validationFailed(fieldErrors);
        }
    }

    static void addLanguageFieldError(
            List<ErrorResponse.FieldError> fieldErrors, int index, String answerFormat, String language) {
        String field = "subQuestions[" + index + "].language";
        boolean code = "code".equals(answerFormat);
        if (code) {
            if (SandboxLanguage.fromCode(language).isEmpty()) {
                fieldErrors.add(new ErrorResponse.FieldError(field, requiredLanguageMessage()));
            }
            return;
        }
        if (StringUtils.hasText(language)) {
            fieldErrors.add(new ErrorResponse.FieldError(
                    field, Msg.t("error.question.languageMustBeNull")));
        }
    }

    static String requiredLanguageMessage() {
        return Msg.t("error.question.languageRequired", String.join(", ", SandboxLanguage.codes()));
    }

    private static String persistLanguage(String answerFormat, String language) {
        if (!"code".equals(answerFormat)) {
            return null;
        }
        return SandboxLanguage.fromCode(language).orElseThrow().getCode();
    }

    private static void applyQuestionFields(
            Question question,
            String title,
            String content,
            String type,
            String difficulty,
            String tags,
            int totalScore) {
        question.setTitle(title);
        question.setContent(content);
        question.setType(type);
        question.setDifficulty(difficulty);
        question.setTags(normalizeTags(tags));
        question.setTotalScore(totalScore);
    }

    private static void applySubQuestionFields(SubQuestion subQuestion, SubQuestionCreateRequest request, int orderIndex) {
        subQuestion.setOrderIndex(orderIndex);
        subQuestion.setContent(request.getContent());
        subQuestion.setAnswerFormat(request.getAnswerFormat());
        subQuestion.setReferenceAnswer(request.getReferenceAnswer());
        subQuestion.setRubric(request.getRubric());
        subQuestion.setAllowSandbox(mapAllowSandbox(request.getAllowSandbox()));
        subQuestion.setLanguage(persistLanguage(request.getAnswerFormat(), request.getLanguage()));
        subQuestion.setScore(request.getScore());
    }

    private static void applySubQuestionFields(SubQuestion subQuestion, SubQuestionUpdateRequest request, int orderIndex) {
        subQuestion.setOrderIndex(orderIndex);
        subQuestion.setContent(request.getContent());
        subQuestion.setAnswerFormat(request.getAnswerFormat());
        subQuestion.setReferenceAnswer(request.getReferenceAnswer());
        subQuestion.setRubric(request.getRubric());
        subQuestion.setAllowSandbox(mapAllowSandbox(request.getAllowSandbox()));
        subQuestion.setLanguage(persistLanguage(request.getAnswerFormat(), request.getLanguage()));
        subQuestion.setScore(request.getScore());
    }

    static LambdaQueryWrapper<Question> buildQuestionWrapper(String title) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) {
            wrapper.like(Question::getTitle, escapeLike(title.trim()));
        }
        wrapper.orderByDesc(Question::getCreateTime, Question::getId);
        return wrapper;
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    static String normalizeTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
        if (seen.isEmpty()) {
            return null;
        }
        return String.join(",", seen);
    }

    static int mapAllowSandbox(Boolean allowSandbox) {
        return Boolean.FALSE.equals(allowSandbox) ? 0 : 1;
    }

    private QuestionVO toQuestionVO(Question question, List<SubQuestionVO> subQuestionVOs) {
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setTitle(question.getTitle());
        vo.setContent(question.getContent());
        vo.setType(question.getType());
        vo.setDifficulty(question.getDifficulty());
        vo.setTags(question.getTags());
        vo.setTotalScore(question.getTotalScore());
        vo.setSubmitNum(question.getSubmitNum());
        vo.setUserId(question.getUserId());
        vo.setCreateTime(question.getCreateTime());
        vo.setUpdateTime(question.getUpdateTime());
        vo.setIsDelete(toBoolean(question.getIsDelete()));
        vo.setSubQuestions(subQuestionVOs);
        return vo;
    }

    private SubQuestionVO toSubQuestionVO(SubQuestion subQuestion) {
        SubQuestionVO vo = new SubQuestionVO();
        vo.setId(subQuestion.getId());
        vo.setQuestionId(subQuestion.getQuestionId());
        vo.setOrderIndex(subQuestion.getOrderIndex());
        vo.setContent(subQuestion.getContent());
        vo.setAnswerFormat(subQuestion.getAnswerFormat());
        vo.setLanguage(subQuestion.getLanguage());
        vo.setReferenceAnswer(subQuestion.getReferenceAnswer());
        vo.setRubric(subQuestion.getRubric());
        vo.setAllowSandbox(toBoolean(subQuestion.getAllowSandbox()));
        vo.setScore(subQuestion.getScore());
        vo.setCreateTime(subQuestion.getCreateTime());
        vo.setUpdateTime(subQuestion.getUpdateTime());
        vo.setIsDelete(toBoolean(subQuestion.getIsDelete()));
        return vo;
    }

    private static Boolean toBoolean(Integer value) {
        return value != null && value == 1;
    }
}
