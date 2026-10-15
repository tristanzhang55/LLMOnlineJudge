package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tfzhang.linuxback.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.common.QuestionException;
import com.tfzhang.linuxback.dto.QuestionCreateRequest;
import com.tfzhang.linuxback.dto.QuestionUpdateRequest;
import com.tfzhang.linuxback.dto.SubQuestionCreateRequest;
import com.tfzhang.linuxback.dto.SubQuestionUpdateRequest;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.service.AttachmentService;
import com.tfzhang.linuxback.service.MarkdownOwner;
import com.tfzhang.linuxback.vo.QuestionVO;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceImplTest {

    private static final long TEACHER_ID = 1L;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private SubQuestionMapper subQuestionMapper;

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void listQuestions_returnsEmptyResultWithoutQueryingSubQuestions() {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<Question> p = invocation.getArgument(0);
            p.setRecords(Collections.emptyList());
            p.setTotal(28);
            return p;
        }).when(questionMapper).selectPage(any(Page.class), any());

        PageResult<QuestionVO> result = questionService.listQuestions(99, 10, null);

        assertTrue(result.getData().isEmpty());
        assertEquals(28, result.getTotal());
        verify(subQuestionMapper, never()).selectList(any());
    }

    @Test
    void buildQuestionWrapper_blankTitle_hasNoLikeCondition() {
        LambdaQueryWrapper<Question> wrapper = QuestionServiceImpl.buildQuestionWrapper("  ");
        assertFalse(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));

        wrapper = QuestionServiceImpl.buildQuestionWrapper(null);
        assertFalse(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));
    }

    @Test
    void buildQuestionWrapper_nonBlankTitle_hasLikeCondition() {
        LambdaQueryWrapper<Question> wrapper = QuestionServiceImpl.buildQuestionWrapper("Shell");
        assertTrue(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));
    }

    @Test
    void escapeLike_escapesWildcards() {
        assertEquals("100\\%\\_x", QuestionServiceImpl.escapeLike("100%_x"));
    }

    @Test
    void normalizeTags_trimsDedupesAndJoins() {
        assertEquals("Linux,Shell", QuestionServiceImpl.normalizeTags(" Linux , , Shell ,Linux"));
        assertNull(QuestionServiceImpl.normalizeTags(""));
        assertNull(QuestionServiceImpl.normalizeTags("  ,  "));
        assertNull(QuestionServiceImpl.normalizeTags(null));
    }

    @Test
    void mapAllowSandbox_nullOrTrueMapsToOne_falseMapsToZero() {
        assertEquals(1, QuestionServiceImpl.mapAllowSandbox(null));
        assertEquals(1, QuestionServiceImpl.mapAllowSandbox(true));
        assertEquals(0, QuestionServiceImpl.mapAllowSandbox(false));
    }

    @Test
    void getQuestion_missingQuestionThrowsNotFound() {
        when(questionMapper.selectById(99L)).thenReturn(null);

        QuestionException ex = assertThrows(QuestionException.class, () -> questionService.getQuestion(99L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(subQuestionMapper, never()).selectList(any());
    }

    @Test
    void getQuestion_returnsNestedSubQuestions() {
        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2901L, 29L, 0, 10, 1),
                savedSubQuestion(2902L, 29L, 1, 15, 0)));

        QuestionVO result = questionService.getQuestion(29L);

        assertEquals(29L, result.getId());
        assertEquals(2, result.getSubQuestions().size());
        assertEquals(0, result.getSubQuestions().get(0).getOrderIndex());
        assertEquals(1, result.getSubQuestions().get(1).getOrderIndex());
        assertNull(result.getSubQuestions().get(0).getLanguage());
    }

    @Test
    void createQuestion_computesTotalScoreAndOrderIndex() {
        QuestionCreateRequest request = buildCreateRequest(10, 15);

        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            q.setId(29L);
            return 1;
        });
        when(subQuestionMapper.insert(any(SubQuestion.class))).thenAnswer(invocation -> {
            SubQuestion sq = invocation.getArgument(0);
            sq.setId(sq.getOrderIndex() == 0 ? 2901L : 2902L);
            return 1;
        });
        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2901L, 29L, 0, 10, 1),
                savedSubQuestion(2902L, 29L, 1, 15, 0)));

        QuestionVO result = questionService.createQuestion(request);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insert(questionCaptor.capture());
        assertEquals(25, questionCaptor.getValue().getTotalScore());
        assertEquals(TEACHER_ID, questionCaptor.getValue().getUserId());
        assertEquals("Linux,Shell", questionCaptor.getValue().getTags());

        ArgumentCaptor<SubQuestion> subCaptor = ArgumentCaptor.forClass(SubQuestion.class);
        verify(subQuestionMapper, org.mockito.Mockito.times(2)).insert(subCaptor.capture());
        List<SubQuestion> inserted = subCaptor.getAllValues();
        assertEquals(0, inserted.get(0).getOrderIndex());
        assertEquals(1, inserted.get(1).getOrderIndex());
        assertEquals(29L, inserted.get(0).getQuestionId());
        assertNull(inserted.get(0).getLanguage());
        assertEquals("c", inserted.get(1).getLanguage());

        assertEquals(25, result.getTotalScore());
        assertEquals(0, result.getSubmitNum());
        assertEquals(2, result.getSubQuestions().size());
        assertEquals(0, result.getSubQuestions().get(0).getOrderIndex());
        assertFalse(result.getSubQuestions().get(1).getAllowSandbox());
    }

    @Test
    void createQuestion_attachesMarkdownImagesForEachOwner() {
        QuestionCreateRequest request = buildCreateRequest(10, 5);
        request.setContent("![](/api/files/question/2026/08/a.png)");
        request.getSubQuestions().get(0).setContent("![](/api/files/sub_question/2026/08/b.png)");
        request.getSubQuestions().get(1).setReferenceAnswer("![](/api/files/sub_question_answer/2026/08/c.png)");

        when(questionMapper.insert(any(Question.class))).thenAnswer(invocation -> {
            Question q = invocation.getArgument(0);
            q.setId(30L);
            return 1;
        });
        when(subQuestionMapper.insert(any(SubQuestion.class))).thenAnswer(invocation -> {
            SubQuestion sq = invocation.getArgument(0);
            sq.setId(sq.getOrderIndex() == 0 ? 3001L : 3002L);
            return 1;
        });
        when(questionMapper.selectById(30L)).thenReturn(savedQuestion(30L, 15));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(3001L, 30L, 0, 10, 1),
                savedSubQuestion(3002L, 30L, 1, 5, 1)));

        questionService.createQuestion(request);

        verify(attachmentService).attachMarkdownImages(eq("question"), eq(30L), eq(request.getContent()));
        verify(attachmentService).attachMarkdownImages(
                eq("sub_question"), eq(3001L), eq(request.getSubQuestions().get(0).getContent()));
        verify(attachmentService).attachMarkdownImages(
                eq("sub_question_answer"), eq(3002L), eq(request.getSubQuestions().get(1).getReferenceAnswer()));
    }

    @Test
    void updateQuestion_updatesRetainedInsertsNewAndDeletesOmitted() {
        QuestionUpdateRequest request = buildUpdateRequest();

        Question existingQuestion = savedQuestion(29L, 25);
        Question updatedQuestion = savedQuestion(29L, 28);
        SubQuestion sq1 = savedSubQuestion(2901L, 29L, 0, 10, 1);
        SubQuestion sq2 = savedSubQuestion(2902L, 29L, 1, 15, 0);
        SubQuestion sq3 = savedSubQuestion(2903L, 29L, 2, 5, 1);

        when(questionMapper.selectById(29L)).thenReturn(existingQuestion, updatedQuestion);
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(sq1, sq2, sq3))
                .thenReturn(List.of(
                        updatedSubQuestion(2902L, 29L, 0, 20, 0),
                        updatedSubQuestion(2904L, 29L, 1, 8, 1)));

        when(subQuestionMapper.insert(any(SubQuestion.class))).thenAnswer(invocation -> {
            SubQuestion sq = invocation.getArgument(0);
            sq.setId(2904L);
            return 1;
        });

        QuestionVO result = questionService.updateQuestion(29L, request);

        verify(questionMapper).updateById(any(Question.class));
        verify(subQuestionMapper).updateById(any(SubQuestion.class));
        verify(subQuestionMapper).insert(any(SubQuestion.class));
        verify(subQuestionMapper).deleteById(2901L);
        verify(subQuestionMapper).deleteById(2903L);
        verify(attachmentService).reconcileMarkdownImages(any(), any());
        assertEquals(28, result.getTotalScore());
    }

    @Test
    void updateQuestion_foreignSubQuestionIdFailsBeforeWrites() {
        QuestionUpdateRequest request = buildUpdateRequest();
        request.getSubQuestions().get(0).setId(9999L);

        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2902L, 29L, 0, 15, 1)));

        QuestionException ex = assertThrows(
                QuestionException.class, () -> questionService.updateQuestion(29L, request));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("subQuestions[0].id", ex.getFieldErrors().get(0).field());
        verify(questionMapper, never()).updateById(any(Question.class));
        verify(subQuestionMapper, never()).updateById(any(SubQuestion.class));
        verify(subQuestionMapper, never()).insert(any(SubQuestion.class));
        verify(subQuestionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void updateQuestion_duplicateSubQuestionIdFailsBeforeWrites() {
        QuestionUpdateRequest request = buildUpdateRequest();
        request.getSubQuestions().get(0).setId(2902L);
        request.getSubQuestions().get(1).setId(2902L);

        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2902L, 29L, 0, 15, 1)));

        QuestionException ex = assertThrows(
                QuestionException.class, () -> questionService.updateQuestion(29L, request));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertTrue(ex.getFieldErrors().stream()
                .anyMatch(error -> error.field().equals("subQuestions[1].id")
                        && error.message().equals("duplicate sub-question id")));
        verify(questionMapper, never()).updateById(any(Question.class));
    }

    @Test
    void updateQuestion_passesReconcileOwnersWithGeneratedChildId() {
        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setTitle("Updated");
        request.setContent("Body");
        request.setType("shell_script");
        request.setDifficulty("easy");

        SubQuestionUpdateRequest retained = new SubQuestionUpdateRequest();
        retained.setId(2902L);
        retained.setContent("Retained");
        retained.setAnswerFormat("text");
        retained.setScore(15);

        SubQuestionUpdateRequest created = new SubQuestionUpdateRequest();
        created.setContent("New");
        created.setAnswerFormat("code");
        created.setLanguage("bash");
        created.setScore(8);

        request.setSubQuestions(List.of(retained, created));

        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2902L, 29L, 0, 15, 1)))
                .thenReturn(List.of(
                        savedSubQuestion(2902L, 29L, 0, 15, 1),
                        savedSubQuestion(2904L, 29L, 1, 8, 1)));
        when(subQuestionMapper.insert(any(SubQuestion.class))).thenAnswer(invocation -> {
            SubQuestion sq = invocation.getArgument(0);
            sq.setId(2904L);
            return 1;
        });

        questionService.updateQuestion(29L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarkdownOwner>> previousCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarkdownOwner>> desiredCaptor = ArgumentCaptor.forClass(List.class);
        verify(attachmentService).reconcileMarkdownImages(previousCaptor.capture(), desiredCaptor.capture());

        assertTrue(previousCaptor.getValue().stream()
                .anyMatch(owner -> owner.ownerType().equals("question") && owner.ownerId() == 29L));
        assertTrue(desiredCaptor.getValue().stream()
                .anyMatch(owner -> owner.ownerType().equals("sub_question") && owner.ownerId() == 2904L));
    }

    @Test
    void deleteQuestion_isTransactional() throws NoSuchMethodException {
        assertTrue(QuestionServiceImpl.class
                .getDeclaredMethod("deleteQuestion", long.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void deleteQuestion_activeQuestion_deletesAggregate() {
        SubQuestion sq1 = savedSubQuestion(2901L, 29L, 0, 10, 1);
        SubQuestion sq2 = savedSubQuestion(2902L, 29L, 1, 15, 0);

        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(sq1, sq2));

        questionService.deleteQuestion(29L);

        verify(attachmentService).softDeleteAggregateAttachments(eq(29L), eq(List.of(2901L, 2902L)));
        verify(subQuestionMapper).deleteById(2901L);
        verify(subQuestionMapper).deleteById(2902L);
        verify(questionMapper).deleteById(29L);
    }

    @Test
    void deleteQuestion_missingQuestion_isNoOp() {
        when(questionMapper.selectById(99L)).thenReturn(null);

        questionService.deleteQuestion(99L);

        verify(attachmentService, never()).softDeleteAggregateAttachments(any(Long.class), any());
        verify(subQuestionMapper, never()).deleteById(any(Long.class));
        verify(questionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void listSandboxLanguages_returnsCatalogCodes() {
        List<com.tfzhang.linuxback.vo.SandboxLanguageVO> result = questionService.listSandboxLanguages();
        assertEquals(List.of("bash", "c"), result.stream().map(com.tfzhang.linuxback.vo.SandboxLanguageVO::getCode).toList());
    }

    @Test
    void createQuestion_codeWithoutLanguage_fails() {
        QuestionCreateRequest request = buildCreateRequest(10, 15);
        request.getSubQuestions().get(1).setLanguage(null);

        QuestionException ex = assertThrows(
                QuestionException.class, () -> questionService.createQuestion(request));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("subQuestions[1].language", ex.getFieldErrors().get(0).field());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    @Test
    void createQuestion_codeWithUnknownOrNonCanonicalLanguage_fails() {
        QuestionCreateRequest python = buildCreateRequest(10, 15);
        python.getSubQuestions().get(1).setLanguage("python");
        QuestionException pythonEx = assertThrows(
                QuestionException.class, () -> questionService.createQuestion(python));
        assertEquals("subQuestions[1].language", pythonEx.getFieldErrors().get(0).field());

        QuestionCreateRequest mixedCase = buildCreateRequest(10, 15);
        mixedCase.getSubQuestions().get(1).setLanguage("Bash");
        QuestionException caseEx = assertThrows(
                QuestionException.class, () -> questionService.createQuestion(mixedCase));
        assertEquals("subQuestions[1].language", caseEx.getFieldErrors().get(0).field());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    @Test
    void createQuestion_textWithLanguage_fails() {
        QuestionCreateRequest request = buildCreateRequest(10, 15);
        request.getSubQuestions().get(0).setLanguage("bash");

        QuestionException ex = assertThrows(
                QuestionException.class, () -> questionService.createQuestion(request));

        assertEquals("subQuestions[0].language", ex.getFieldErrors().get(0).field());
        verify(questionMapper, never()).insert(any(Question.class));
    }

    @Test
    void getQuestion_mapsPersistedLanguage() {
        SubQuestion codeSq = savedSubQuestion(2902L, 29L, 1, 15, 0);
        codeSq.setAnswerFormat("code");
        codeSq.setLanguage("bash");
        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2901L, 29L, 0, 10, 1),
                codeSq));

        QuestionVO result = questionService.getQuestion(29L);

        assertEquals("bash", result.getSubQuestions().get(1).getLanguage());
    }

    @Test
    void updateQuestion_codeWithoutLanguage_failsBeforeWrites() {
        QuestionUpdateRequest request = buildUpdateRequest();
        request.getSubQuestions().get(1).setLanguage(null);

        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                savedSubQuestion(2902L, 29L, 0, 15, 1)));

        QuestionException ex = assertThrows(
                QuestionException.class, () -> questionService.updateQuestion(29L, request));

        assertEquals("subQuestions[1].language", ex.getFieldErrors().get(0).field());
        verify(questionMapper, never()).updateById(any(Question.class));
    }

    @Test
    void deleteQuestion_questionWithNoSubQuestions_stillDeletesQuestion() {
        when(questionMapper.selectById(29L)).thenReturn(savedQuestion(29L, 25));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of());

        questionService.deleteQuestion(29L);

        verify(attachmentService).softDeleteAggregateAttachments(eq(29L), eq(List.of()));
        verify(subQuestionMapper, never()).deleteById(any(Long.class));
        verify(questionMapper).deleteById(29L);
    }

    private static QuestionCreateRequest buildCreateRequest(int score1, int score2) {
        SubQuestionCreateRequest sq1 = new SubQuestionCreateRequest();
        sq1.setContent("Sub 1");
        sq1.setAnswerFormat("text");
        sq1.setScore(score1);

        SubQuestionCreateRequest sq2 = new SubQuestionCreateRequest();
        sq2.setContent("Sub 2");
        sq2.setAnswerFormat("code");
        sq2.setLanguage("c");
        sq2.setAllowSandbox(false);
        sq2.setScore(score2);

        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setTitle("Test");
        request.setContent("Body");
        request.setType("shell_script");
        request.setDifficulty("easy");
        request.setTags(" Linux , , Shell ,Linux");
        request.setSubQuestions(List.of(sq1, sq2));
        return request;
    }

    private static QuestionUpdateRequest buildUpdateRequest() {
        SubQuestionUpdateRequest retained = new SubQuestionUpdateRequest();
        retained.setId(2902L);
        retained.setContent("Updated retained");
        retained.setAnswerFormat("text");
        retained.setAllowSandbox(false);
        retained.setScore(20);

        SubQuestionUpdateRequest created = new SubQuestionUpdateRequest();
        created.setContent("Brand new");
        created.setAnswerFormat("code");
        created.setLanguage("bash");
        created.setScore(8);

        QuestionUpdateRequest request = new QuestionUpdateRequest();
        request.setTitle("Updated title");
        request.setContent("Updated body");
        request.setType("shell_script");
        request.setDifficulty("medium");
        request.setTags(" Linux ,Shell ");
        request.setSubQuestions(List.of(retained, created));
        return request;
    }

    private static Question savedQuestion(long id, int totalScore) {
        Question question = new Question();
        question.setId(id);
        question.setTitle("Test");
        question.setContent("Body");
        question.setType("shell_script");
        question.setDifficulty("easy");
        question.setTags("Linux,Shell");
        question.setTotalScore(totalScore);
        question.setSubmitNum(0);
        question.setUserId(TEACHER_ID);
        question.setCreateTime(LocalDateTime.of(2026, 8, 16, 14, 3, 11));
        question.setUpdateTime(LocalDateTime.of(2026, 8, 16, 14, 3, 11));
        question.setIsDelete(0);
        return question;
    }

    private static SubQuestion savedSubQuestion(
            long id, long questionId, int orderIndex, int score, int allowSandbox) {
        SubQuestion subQuestion = new SubQuestion();
        subQuestion.setId(id);
        subQuestion.setQuestionId(questionId);
        subQuestion.setOrderIndex(orderIndex);
        subQuestion.setContent("Sub");
        subQuestion.setAnswerFormat("text");
        subQuestion.setAllowSandbox(allowSandbox);
        subQuestion.setScore(score);
        subQuestion.setCreateTime(LocalDateTime.of(2026, 8, 16, 14, 3, 11));
        subQuestion.setUpdateTime(LocalDateTime.of(2026, 8, 16, 14, 3, 11));
        subQuestion.setIsDelete(0);
        return subQuestion;
    }

    private static SubQuestion updatedSubQuestion(
            long id, long questionId, int orderIndex, int score, int allowSandbox) {
        SubQuestion subQuestion = savedSubQuestion(id, questionId, orderIndex, score, allowSandbox);
        subQuestion.setContent("Updated");
        return subQuestion;
    }
}
