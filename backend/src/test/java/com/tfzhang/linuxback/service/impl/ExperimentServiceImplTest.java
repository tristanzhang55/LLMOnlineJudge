package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.ExperimentCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentQuestionCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentStatusUpdateRequest;
import com.tfzhang.linuxback.scheduler.ExperimentStatusScheduler;
import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.SubQuestion;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.DisputeMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubQuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.SubmissionQuery;
import com.tfzhang.linuxback.vo.ExperimentQuestionVO;
import com.tfzhang.linuxback.vo.ExperimentVO;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExperimentServiceImplTest {

    private static final long TEACHER_ID = 1L;

    @Mock
    private ExperimentMapper experimentMapper;

    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private QuestionMapper questionMapper;

    @Mock
    private SubQuestionMapper subQuestionMapper;

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private DisputeMapper disputeMapper;

    @Mock
    private SubmissionQuery submissionQuery;

    @InjectMocks
    private ExperimentServiceImpl experimentService;

    @BeforeAll
    static void initMybatisPlus() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_ID);
        lenient().when(submissionQuery.findExperimentQuestionIdsWithSubmissions(anyCollection()))
                .thenReturn(Set.of());
        lenient().when(submissionQuery.countDistinctStudentsForExperiment(anyLong())).thenReturn(0L);
        lenient().when(submissionQuery.hasSubmissionsForExperimentQuestion(anyLong())).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void listExperiments_returnsMappedVosForDevTeacher() {
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    experiment(2L, "Shell 脚本综合练习", 1, null),
                    experiment(1L, "Linux 基础作业一", 0, "2026-08-20T23:59:00")));
            page.setTotal(2);
            return page;
        });

        PageResult<ExperimentVO> result = experimentService.listExperiments(1, 10, null, null, null);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getData().size());
        assertEquals(2L, result.getData().get(0).getId());
        assertEquals("Shell 脚本综合练习", result.getData().get(0).getTitle());
        assertEquals(1, result.getData().get(0).getStatus());
        assertNull(result.getData().get(0).getDeadlineTime());
        assertFalse(result.getData().get(0).getIsDelete());
        assertNull(result.getData().get(0).getQuestions());
    }

    @Test
    void listExperiments_passesPageAndPageSizeToPageObject() {
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        });

        experimentService.listExperiments(2, 5, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Page<Experiment>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(experimentMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(2, pageCaptor.getValue().getCurrent());
        assertEquals(5, pageCaptor.getValue().getSize());
    }

    @Test
    void listExperiments_totalComesFromPageNotDataSize() {
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(List.of(experiment(1L, "A", 0, null)));
            page.setTotal(17);
            return page;
        });

        PageResult<ExperimentVO> result = experimentService.listExperiments(1, 10, null, null, null);

        assertEquals(17, result.getTotal());
        assertEquals(1, result.getData().size());
    }

    @Test
    void listExperiments_emptyPageRecords_returnsEmptyDataWithPageTotal() {
        doAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(28);
            return page;
        }).when(experimentMapper).selectPage(any(Page.class), any());

        PageResult<ExperimentVO> result = experimentService.listExperiments(99, 10, null, null, null);

        assertTrue(result.getData().isEmpty());
        assertEquals(28, result.getTotal());
    }

    @Test
    void listExperiments_filtersByDevUserIdAndOrdersByCreateTimeDesc() {
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        });

        experimentService.listExperiments(1, 10, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Experiment>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(experimentMapper).selectPage(any(Page.class), captor.capture());

        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("USERID"));
        assertTrue(sql.contains("ORDER BY"));
    }

    @Test
    void listExperiments_preservesNullDescriptionAndDeadlineTime() {
        Experiment row = experiment(3L, "Draft", 0, null);
        row.setDescription(null);
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(List.of(row));
            page.setTotal(1);
            return page;
        });

        ExperimentVO vo = experimentService.listExperiments(1, 10, null, null, null).getData().get(0);

        assertNull(vo.getDescription());
        assertNull(vo.getDeadlineTime());
        assertEquals(0, vo.getStatus());
    }

    @Test
    void buildExperimentWrapper_blankOrNullTitle_hasNoLikeCondition() {
        LambdaQueryWrapper<Experiment> wrapper = ExperimentServiceImpl.buildExperimentWrapper("  ", null, null);
        assertFalse(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));

        wrapper = ExperimentServiceImpl.buildExperimentWrapper(null, null, null);
        assertFalse(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));

        wrapper = ExperimentServiceImpl.buildExperimentWrapper("", null, null);
        assertFalse(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));
    }

    @Test
    void buildExperimentWrapper_nonBlankTitle_hasLikeCondition() {
        LambdaQueryWrapper<Experiment> wrapper = ExperimentServiceImpl.buildExperimentWrapper("Shell", null, null);
        assertTrue(wrapper.getSqlSegment().toUpperCase().contains("LIKE"));
    }

    @Test
    void buildExperimentWrapper_nullClassIdAndStatus_haveNoEqOnThoseColumns() {
        LambdaQueryWrapper<Experiment> wrapper =
                ExperimentServiceImpl.buildExperimentWrapper(null, null, null);
        String sql = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sql.contains("USERID"));
        assertFalse(sql.contains("CLASSID"));
        assertFalse(sql.contains("STATUS"));
    }

    @Test
    void buildExperimentWrapper_classIdAndStatus_addEqPredicates() {
        LambdaQueryWrapper<Experiment> wrapper =
                ExperimentServiceImpl.buildExperimentWrapper(null, 2, 1);
        String sql = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sql.contains("CLASSID"));
        assertTrue(sql.contains("STATUS"));
    }

    @Test
    void buildExperimentWrapper_allFilters_combinedWithAnd() {
        LambdaQueryWrapper<Experiment> wrapper =
                ExperimentServiceImpl.buildExperimentWrapper("Linux", 1, 0);
        String sql = wrapper.getSqlSegment().toUpperCase();
        assertTrue(sql.contains("LIKE"));
        assertTrue(sql.contains("CLASSID"));
        assertTrue(sql.contains("STATUS"));
        assertTrue(sql.contains("USERID"));
    }

    @Test
    void buildExperimentWrapper_statusZero_isApplied() {
        LambdaQueryWrapper<Experiment> wrapper =
                ExperimentServiceImpl.buildExperimentWrapper(null, null, 0);
        assertTrue(wrapper.getSqlSegment().toUpperCase().contains("STATUS"));
    }

    @Test
    void escapeLike_escapesWildcards() {
        assertEquals("100\\%\\_x", ExperimentServiceImpl.escapeLike("100%_x"));
    }

    @Test
    void createExperiment_setsStatusZeroAndDevUserId() {
        stubHappyCreatePath(12L, 7L);

        experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        ArgumentCaptor<Experiment> captor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        assertEquals(TEACHER_ID, captor.getValue().getUserId());
    }

    @Test
    void createExperiment_assignsSortOrderFromArrayPosition() {
        stubHappyCreatePath(12L, 7L);

        experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        ArgumentCaptor<ExperimentQuestion> captor = ArgumentCaptor.forClass(ExperimentQuestion.class);
        verify(experimentQuestionMapper, times(2)).insert(captor.capture());
        List<ExperimentQuestion> inserted = captor.getAllValues();
        assertEquals(0, inserted.get(0).getSortOrder());
        assertEquals(1, inserted.get(1).getSortOrder());
    }

    @Test
    void createExperiment_storesPointsLiterally() {
        stubHappyCreatePath(12L, 7L);

        ExperimentQuestionCreateRequest override = questionRow(12L, 20);
        ExperimentQuestionCreateRequest zero = questionRow(7L, 0);
        experimentService.createExperiment(createRequest(override, zero));

        ArgumentCaptor<ExperimentQuestion> captor = ArgumentCaptor.forClass(ExperimentQuestion.class);
        verify(experimentQuestionMapper, times(2)).insert(captor.capture());
        assertEquals(20, captor.getAllValues().get(0).getPoints());
        assertEquals(0, captor.getAllValues().get(1).getPoints());
    }

    @Test
    void resolvePoints_mapsNullToZero() {
        ExperimentQuestionCreateRequest req = new ExperimentQuestionCreateRequest();
        req.setPoints(null);
        assertEquals(0, ExperimentServiceImpl.resolvePoints(req));
        req.setPoints(0);
        assertEquals(0, ExperimentServiceImpl.resolvePoints(req));
        req.setPoints(20);
        assertEquals(20, ExperimentServiceImpl.resolvePoints(req));
    }

    @Test
    void computeEffectivePoints_resolvesZeroSentinel() {
        Question q = question(12L, 25);
        assertEquals(25, ExperimentServiceImpl.computeEffectivePoints(0, q));
        assertEquals(25, ExperimentServiceImpl.computeEffectivePoints(null, q));
        assertEquals(20, ExperimentServiceImpl.computeEffectivePoints(20, q));
    }

    @Test
    void createExperiment_returnsEffectivePointsInResponse() {
        stubHappyCreatePath(12L, 7L);

        ExperimentVO vo = experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        assertEquals(20, vo.getQuestions().get(0).getEffectivePoints());
        assertEquals(15, vo.getQuestions().get(1).getEffectivePoints());
        assertEquals(0, vo.getQuestions().get(1).getPoints());
        assertFalse(vo.getQuestions().get(0).getQuestionDeleted());
        assertFalse(vo.getQuestions().get(1).getQuestionDeleted());
    }

    @Test
    void createExperiment_unknownQuestionIdFailsBeforeInsert() {
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.createExperiment(createRequest(
                        questionRow(12L, 20),
                        questionRow(999L, 0))));

        assertTrue(ex.getFieldErrors().stream().anyMatch(error ->
                error.field().equals("questions[1].questionId")
                        && error.message().equals("questionId does not exist")));
        verify(experimentMapper, never()).insert(any(Experiment.class));
    }

    @Test
    void createExperiment_unknownClassFailsBeforeInsert() {
        when(classMapper.selectById(99L)).thenReturn(null);

        ExperimentCreateRequest request = createRequest(questionRow(12L, 20));
        request.setClassId(99L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.createExperiment(request));

        assertTrue(ex.getFieldErrors().stream().anyMatch(error ->
                error.field().equals("classId") && error.message().equals("classId does not exist")));
        verify(experimentMapper, never()).insert(any(Experiment.class));
    }

    @Test
    void createExperiment_otherTeacherClassFailsWithSameMessage() {
        TeachingClass other = ownedClass(2L);
        other.setTeacherId(999L);
        when(classMapper.selectById(2L)).thenReturn(other);

        ExperimentCreateRequest request = createRequest(questionRow(12L, 20));
        request.setClassId(2L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.createExperiment(request));

        assertTrue(ex.getFieldErrors().stream().anyMatch(error ->
                error.field().equals("classId") && error.message().equals("classId does not exist")));
    }

    @Test
    void createExperiment_duplicateQuestionIdReportsSecondOccurrence() {
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.createExperiment(createRequest(
                        questionRow(12L, 20),
                        questionRow(12L, 10))));

        assertTrue(ex.getFieldErrors().stream().anyMatch(error ->
                error.field().equals("questions[1].questionId")
                        && error.message().equals("duplicate questionId")));
    }

    @Test
    void createExperiment_collectsMultipleValidationErrors() {
        when(classMapper.selectById(99L)).thenReturn(null);
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentCreateRequest request = createRequest(
                questionRow(12L, 20),
                questionRow(999L, 0));
        request.setClassId(99L);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.createExperiment(request));

        assertEquals(2, ex.getFieldErrors().size());
        assertTrue(ex.getFieldErrors().stream().anyMatch(e -> e.field().equals("classId")));
        assertTrue(ex.getFieldErrors().stream().anyMatch(e -> e.field().equals("questions[1].questionId")));
    }

    @Test
    void createExperiment_looksUpQuestionsInOneBatchQueryNotOnePerRow() {
        stubHappyCreatePath(12L, 7L);

        experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        verify(questionMapper, times(2)).selectBatchIds(anyCollection());
    }

    @Test
    void createExperiment_responseHasNonNullTimestampsFromReselect() {
        stubHappyCreatePath(12L, 7L);

        ExperimentVO vo = experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        assertNotNull(vo.getCreateTime());
        assertNotNull(vo.getUpdateTime());
        assertNotNull(vo.getQuestions().get(0).getCreateTime());
        assertNotNull(vo.getQuestions().get(0).getUpdateTime());
    }

    @Test
    void createExperiment_nestedQuestionsOrderedBySortOrder() {
        stubHappyCreatePath(12L, 7L);

        ExperimentVO vo = experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        assertEquals(0, vo.getQuestions().get(0).getSortOrder());
        assertEquals(1, vo.getQuestions().get(1).getSortOrder());
        assertEquals(12L, vo.getQuestions().get(0).getQuestionId());
        assertEquals(7L, vo.getQuestions().get(1).getQuestionId());
    }

    @Test
    void getExperiment_returnsNestedQuestionsWithEffectivePoints() {
        stubDetailPath(5L, List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts()),
                savedEqRow(42L, 5L, 7L, 0, 1, ts())));

        ExperimentVO result = experimentService.getExperiment(5L);

        assertEquals(5L, result.getId());
        assertEquals(2, result.getQuestions().size());
        assertEquals(20, result.getQuestions().get(0).getEffectivePoints());
        assertEquals(15, result.getQuestions().get(1).getEffectivePoints());
        assertEquals(0, result.getQuestions().get(0).getSortOrder());
        assertEquals(1, result.getQuestions().get(1).getSortOrder());
    }

    @Test
    void getExperiment_missingExperimentThrowsNotFound() {
        when(experimentMapper.selectById(99L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.getExperiment(99L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentQuestionMapper, never()).selectList(any());
    }

    @Test
    void getExperiment_foreignUserIdThrowsNotFound() {
        Experiment foreign = savedExperiment(5L, ts());
        foreign.setUserId(999L);
        when(experimentMapper.selectById(5L)).thenReturn(foreign);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.getExperiment(5L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentQuestionMapper, never()).selectList(any());
    }

    @Test
    void getExperiment_otherTeacherTokenCannotLoadOwnedExperiment() {
        TestAuth.setTeacher(99L);
        Experiment ownedByTeacherOne = savedExperiment(5L, ts());
        ownedByTeacherOne.setUserId(TEACHER_ID);
        when(experimentMapper.selectById(5L)).thenReturn(ownedByTeacherOne);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.getExperiment(5L));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentQuestionMapper, never()).selectList(any());
    }

    @Test
    void getExperiment_flagsDeletedQuestion() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 99L, 0, 0, ts())));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(subQuestionMapper.selectList(any())).thenReturn(List.of());

        ExperimentVO result = experimentService.getExperiment(5L);

        ExperimentQuestionVO row = result.getQuestions().get(0);
        assertTrue(row.getQuestionDeleted());
        assertNull(row.getQuestionTitle());
        assertNull(row.getQuestionTotalScore());
        assertEquals(0, row.getSubQuestionCount());
        assertEquals(0, row.getEffectivePoints());
    }

    @Test
    void getExperiment_andCreateUseSameVoBuilder() {
        stubHappyCreatePath(12L, 7L);
        ExperimentVO fromCreate = experimentService.createExperiment(createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        stubDetailPath(5L, List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts()),
                savedEqRow(42L, 5L, 7L, 0, 1, ts())));
        ExperimentVO fromDetail = experimentService.getExperiment(5L);

        assertEquals(fromCreate.getQuestions().get(0).getEffectivePoints(),
                fromDetail.getQuestions().get(0).getEffectivePoints());
        assertEquals(fromCreate.getQuestions().get(1).getEffectivePoints(),
                fromDetail.getQuestions().get(1).getEffectivePoints());
        assertEquals(fromCreate.getQuestions().get(0).getQuestionTitle(),
                fromDetail.getQuestions().get(0).getQuestionTitle());
    }

    @Test
    void updateExperiment_updatesRetainedInsertsNewAndDeletesOmitted() {
        LocalDateTime ts = ts();
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts));
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                question(12L, 25), question(7L, 15), question(19L, 10)));

        ExperimentQuestion retained12 = savedEqRow(41L, 5L, 12L, 20, 0, ts);
        ExperimentQuestion retained7 = savedEqRow(42L, 5L, 7L, 0, 1, ts);
        ExperimentQuestion dropped = savedEqRow(43L, 5L, 15L, 10, 2, ts);
        when(experimentQuestionMapper.selectList(any()))
                .thenReturn(List.of(retained12, retained7, dropped))
                .thenReturn(List.of(
                        savedEqRow(42L, 5L, 7L, 0, 0, ts),
                        savedEqRow(41L, 5L, 12L, 30, 1, ts),
                        savedEqRow(44L, 5L, 19L, 0, 2, ts)));

        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                subQuestion(12L), subQuestion(12L), subQuestion(12L),
                subQuestion(7L), subQuestion(7L),
                subQuestion(19L)));

        ExperimentCreateRequest request = createRequest(
                questionRow(7L, 0),
                questionRow(12L, 30),
                questionRow(19L, 0));

        ExperimentVO result = experimentService.updateExperiment(5L, request);

        verify(experimentMapper).updateById(any(Experiment.class));
        verify(experimentQuestionMapper).updateById(retained7);
        verify(experimentQuestionMapper).updateById(retained12);
        verify(experimentQuestionMapper).insert(any(ExperimentQuestion.class));
        verify(experimentQuestionMapper).deleteById(43L);
        assertEquals(3, result.getQuestions().size());
        assertEquals(7L, result.getQuestions().get(0).getQuestionId());
        assertEquals(12L, result.getQuestions().get(1).getQuestionId());
        assertEquals(19L, result.getQuestions().get(2).getQuestionId());
    }

    @Test
    void updateExperiment_sortOrderFromArrayPosition() {
        stubUpdateHappyPath();

        ExperimentCreateRequest request = createRequest(
                questionRow(7L, 0),
                questionRow(12L, 20));

        experimentService.updateExperiment(5L, request);

        ArgumentCaptor<ExperimentQuestion> captor = ArgumentCaptor.forClass(ExperimentQuestion.class);
        verify(experimentQuestionMapper, times(2)).updateById(captor.capture());
        assertEquals(0, captor.getAllValues().get(0).getSortOrder());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
    }

    @Test
    void updateExperiment_doesNotModifyStatusUserIdOrCreateTime() {
        LocalDateTime createTime = LocalDateTime.of(2026, 8, 10, 10, 0);
        Experiment experiment = savedExperiment(5L, ts());
        experiment.setStatus(1);
        experiment.setCreateTime(createTime);
        when(experimentMapper.selectById(5L)).thenReturn(experiment);
        stubUpdateReferences();

        experimentService.updateExperiment(5L, createRequest(questionRow(12L, 20)));

        ArgumentCaptor<Experiment> captor = ArgumentCaptor.forClass(Experiment.class);
        verify(experimentMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        assertEquals(TEACHER_ID, captor.getValue().getUserId());
        assertEquals(createTime, captor.getValue().getCreateTime());
    }

    @Test
    void updateExperiment_unknownExperimentFailsBeforeWrites() {
        when(experimentMapper.selectById(99L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperiment(99L, createRequest(questionRow(12L, 20))));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentMapper, never()).updateById(any(Experiment.class));
        verify(experimentQuestionMapper, never()).insert(any(ExperimentQuestion.class));
    }

    @Test
    void updateExperiment_unknownQuestionIdFailsBeforeWrites() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts())));
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperiment(5L, createRequest(
                        questionRow(12L, 20),
                        questionRow(999L, 0))));

        assertEquals("INVALID_PARAMETER", ex.getCode());
        verify(experimentMapper, never()).updateById(any(Experiment.class));
        verify(experimentQuestionMapper, never()).updateById(any(ExperimentQuestion.class));
    }

    @Test
    void updateExperiment_badClassIdFailsBeforeWrites() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts())));
        when(classMapper.selectById(99L)).thenReturn(null);
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentCreateRequest request = createRequest(questionRow(12L, 20));
        request.setClassId(99L);

        assertThrows(ExperimentException.class, () ->
                experimentService.updateExperiment(5L, request));

        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }

    @Test
    void updateExperiment_duplicateQuestionIdFailsBeforeWrites() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts())));
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperiment(5L, createRequest(
                        questionRow(12L, 20),
                        questionRow(12L, 10))));

        assertTrue(ex.getFieldErrors().stream().anyMatch(e ->
                e.field().equals("questions[1].questionId")
                        && e.message().equals("duplicate questionId")));
        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }

    @Test
    void updateExperiment_repeatedIdenticalUpdatePerformsNoInsertsOrDeletes() {
        stubUpdateHappyPath();

        ExperimentCreateRequest request = createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0));

        experimentService.updateExperiment(5L, request);
        verify(experimentQuestionMapper, never()).insert(any(ExperimentQuestion.class));
        verify(experimentQuestionMapper, never()).deleteById(anyLong());

        experimentService.updateExperiment(5L, request);
        verify(experimentQuestionMapper, never()).insert(any(ExperimentQuestion.class));
        verify(experimentQuestionMapper, never()).deleteById(anyLong());
    }

    @Test
    void updateExperiment_rereadsAggregateBeforeReturning() {
        stubUpdateHappyPath();

        ExperimentVO result = experimentService.updateExperiment(5L, createRequest(
                questionRow(12L, 20),
                questionRow(7L, 0)));

        assertNotNull(result.getUpdateTime());
        assertNotNull(result.getQuestions().get(0).getUpdateTime());
    }

    @Test
    void deleteExperiment_isTransactional() throws NoSuchMethodException {
        assertTrue(ExperimentServiceImpl.class
                .getDeclaredMethod("deleteExperiment", long.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void deleteExperiment_activeOwnedAssignment_deletesChildrenThenParent() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));

        experimentService.deleteExperiment(5L);

        InOrder order = inOrder(experimentQuestionMapper, experimentMapper);
        order.verify(experimentQuestionMapper).delete(any(LambdaQueryWrapper.class));
        order.verify(experimentMapper).delete(any(LambdaQueryWrapper.class));
        verifyNoInteractions(questionMapper, subQuestionMapper);
    }

    @Test
    void deleteExperiment_missingAssignment_isNoOp() {
        when(experimentMapper.selectById(99L)).thenReturn(null);

        experimentService.deleteExperiment(99L);

        verify(experimentQuestionMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(experimentMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void deleteExperiment_foreignUserId_isNoOp() {
        Experiment foreign = savedExperiment(5L, ts());
        foreign.setUserId(999L);
        when(experimentMapper.selectById(5L)).thenReturn(foreign);

        experimentService.deleteExperiment(5L);

        verify(experimentQuestionMapper, never()).delete(any(LambdaQueryWrapper.class));
        verify(experimentMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void deleteExperiment_childDeleteScopedToExperimentId() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));

        experimentService.deleteExperiment(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<ExperimentQuestion>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(experimentQuestionMapper).delete(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().toUpperCase().contains("EXPERIMENTID"));
    }

    @Test
    void deleteExperiment_parentDeleteScopedToOwner() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));

        experimentService.deleteExperiment(5L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<Experiment>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(experimentMapper).delete(captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("USERID"));
    }

    @Test
    void deleteExperiment_noActiveChildRows_stillDeletesParent() {
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts()));

        experimentService.deleteExperiment(5L);

        verify(experimentQuestionMapper).delete(any(LambdaQueryWrapper.class));
        verify(experimentMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void updateExperimentStatus_isTransactional() throws NoSuchMethodException {
        assertTrue(ExperimentServiceImpl.class
                .getDeclaredMethod("updateExperimentStatus", long.class, ExperimentStatusUpdateRequest.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void updateExperimentStatus_draftToPublished_usesStatusOnlyUpdate() {
        LocalDateTime ts = ts();
        Experiment draft = savedExperiment(5L, ts);
        draft.setStatus(0);
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        when(experimentMapper.selectById(5L)).thenReturn(draft, draft, published);
        stubStatusQuestions(ts);

        ExperimentVO result = experimentService.updateExperimentStatus(5L, statusRequest(1));

        assertEquals(1, result.getStatus());
        verify(experimentMapper, never()).updateById(any(Experiment.class));
        verify(experimentMapper, times(2)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateExperimentStatus_publishedToWithdrawn_succeeds() {
        LocalDateTime ts = ts();
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        published.setDeadlineTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        Experiment withdrawn = savedExperiment(5L, ts);
        withdrawn.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(published, published, withdrawn);
        stubStatusQuestions(ts);
        when(disputeMapper.findOpenByExperimentId(5L)).thenReturn(List.of());

        ExperimentVO result = experimentService.updateExperimentStatus(5L, statusRequest(0));

        assertEquals(0, result.getStatus());
    }

    @Test
    void updateExperimentStatus_draftRepublish_succeeds() {
        LocalDateTime ts = ts();
        Experiment draft = savedExperiment(5L, ts);
        draft.setStatus(0);
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        when(experimentMapper.selectById(5L)).thenReturn(draft, draft, published);
        stubStatusQuestions(ts);

        ExperimentVO result = experimentService.updateExperimentStatus(5L, statusRequest(1));

        assertEquals(1, result.getStatus());
    }

    @Test
    void updateExperimentStatus_samePublishedStatus_performsOnlyExpiryUpdate() {
        LocalDateTime ts = ts();
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        published.setDeadlineTime(LocalDateTime.of(2026, 12, 31, 23, 59));
        when(experimentMapper.selectById(5L)).thenReturn(published, published, published);
        stubStatusQuestions(ts);

        experimentService.updateExperimentStatus(5L, statusRequest(1));

        verify(experimentMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateExperimentStatus_draftToWithdraw_isNoOp() {
        LocalDateTime ts = ts();
        Experiment draft = savedExperiment(5L, ts);
        draft.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(draft, draft, draft);
        stubStatusQuestions(ts);

        ExperimentVO result = experimentService.updateExperimentStatus(5L, statusRequest(0));

        assertEquals(0, result.getStatus());
        verify(experimentMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateExperimentStatus_expiredToPublished_invalidTransition() {
        LocalDateTime ts = ts();
        Experiment expired = savedExperiment(5L, ts);
        expired.setStatus(2);
        when(experimentMapper.selectById(5L)).thenReturn(expired, expired);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperimentStatus(5L, statusRequest(1)));

        assertTrue(ex.getFieldErrors().stream().anyMatch(e ->
                e.message().equals("invalid status transition")));
    }

    @Test
    void validateStatusTransition_targetTwoOrThree_notClientSettable() {
        assertTrue(experimentService.validateStatusTransition(0, 0, 5L).isEmpty());
        assertTrue(experimentService.validateStatusTransition(1, 2, 5L).stream()
                .anyMatch(e -> e.message().equals("status is not client-settable")));
        assertTrue(experimentService.validateStatusTransition(1, 3, 5L).stream()
                .anyMatch(e -> e.message().equals("status is not client-settable")));
    }

    @Test
    void updateExperimentStatus_withdrawClosesPendingDisputes() {
        LocalDateTime ts = ts();
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        Experiment withdrawn = savedExperiment(5L, ts);
        withdrawn.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(published, published, withdrawn);
        stubStatusQuestions(ts);

        Dispute open = new Dispute();
        open.setId(501L);
        open.setSubmissionId(100L);
        when(disputeMapper.findOpenByExperimentId(5L)).thenReturn(List.of(open));
        when(disputeMapper.casResolve(eq(501L), eq("[系统] 作业已重新发布，原申诉自动关闭"), any()))
                .thenReturn(1);

        experimentService.updateExperimentStatus(5L, statusRequest(0));

        verify(disputeMapper).casResolve(eq(501L), eq("[系统] 作业已重新发布，原申诉自动关闭"), any());
        verify(disputeMapper, never()).updateById(any(Dispute.class));
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    @Test
    void updateExperimentStatus_withdrawCasMiss_doesNotWriteSubmission() {
        LocalDateTime ts = ts();
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        Experiment withdrawn = savedExperiment(5L, ts);
        withdrawn.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(published, published, withdrawn);
        stubStatusQuestions(ts);

        Dispute open = new Dispute();
        open.setId(501L);
        when(disputeMapper.findOpenByExperimentId(5L)).thenReturn(List.of(open));
        when(disputeMapper.casResolve(eq(501L), any(), any())).thenReturn(0);

        experimentService.updateExperimentStatus(5L, statusRequest(0));

        verify(disputeMapper).casResolve(eq(501L), eq("[系统] 作业已重新发布，原申诉自动关闭"), any());
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    @Test
    void updateExperimentStatus_withdrawSkipsAlreadyClosedRows() {
        LocalDateTime ts = ts();
        Experiment published = savedExperiment(5L, ts);
        published.setStatus(1);
        Experiment withdrawn = savedExperiment(5L, ts);
        withdrawn.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(published, published, withdrawn);
        stubStatusQuestions(ts);
        when(disputeMapper.findOpenByExperimentId(5L)).thenReturn(List.of());

        experimentService.updateExperimentStatus(5L, statusRequest(0));

        verify(disputeMapper, never()).casResolve(anyLong(), any(), any());
        verify(submissionMapper, never()).updateById(any(Submission.class));
    }

    @Test
    void submission_hasNoDroppedDisputeTextColumns() {
        Set<String> names = Arrays.stream(Submission.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("manualComment"));
        assertFalse(names.contains("disputeReason"));
        assertFalse(names.contains("disputeStatus"));
        assertFalse(names.contains("disputeTime"));
        assertFalse(names.contains("disputeResolvedBy"));
        assertFalse(names.contains("disputeResolveTime"));
    }

    @Test
    void updateExperiment_omittingSubmittedQuestionThrowsConflict() {
        LocalDateTime ts = ts();
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts));
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));

        ExperimentQuestion retained = savedEqRow(41L, 5L, 12L, 20, 0, ts);
        ExperimentQuestion dropped = savedEqRow(43L, 5L, 15L, 10, 2, ts);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(retained, dropped));
        when(submissionQuery.hasSubmissionsForExperimentQuestion(43L)).thenReturn(true);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperiment(5L, createRequest(questionRow(12L, 20))));

        assertEquals("CONFLICT", ex.getCode());
        verify(experimentQuestionMapper, never()).deleteById(anyLong());
        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }

    @Test
    void updateExperimentStatus_publishWithoutQuestions_failsBeforeStatusUpdate() {
        LocalDateTime ts = ts();
        Experiment draft = savedExperiment(5L, ts);
        draft.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(draft, draft);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of());

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperimentStatus(5L, statusRequest(1)));

        assertTrue(ex.getFieldErrors().stream().anyMatch(e ->
                e.message().equals("cannot publish an assignment with no questions")));
        verify(experimentMapper, times(1)).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateExperimentStatus_publishWithDeletedQuestions_failsBeforeStatusUpdate() {
        LocalDateTime ts = ts();
        Experiment draft = savedExperiment(5L, ts);
        draft.setStatus(0);
        when(experimentMapper.selectById(5L)).thenReturn(draft, draft);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 99L, 0, 0, ts)));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperimentStatus(5L, statusRequest(1)));

        assertTrue(ex.getFieldErrors().stream().anyMatch(e ->
                e.message().equals("cannot publish an assignment with no questions")));
    }

    @Test
    void updateExperimentStatus_missingExperimentThrowsNotFound() {
        when(experimentMapper.selectById(99L)).thenReturn(null);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperimentStatus(99L, statusRequest(1)));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void updateExperimentStatus_foreignUserIdThrowsNotFound() {
        Experiment foreign = savedExperiment(5L, ts());
        foreign.setUserId(999L);
        when(experimentMapper.selectById(5L)).thenReturn(foreign);

        ExperimentException ex = assertThrows(ExperimentException.class, () ->
                experimentService.updateExperimentStatus(5L, statusRequest(1)));

        assertEquals("NOT_FOUND", ex.getCode());
        verify(experimentMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void listExperiments_invokesExpiryHelper() {
        when(experimentMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Experiment> page = invocation.getArgument(0);
            page.setRecords(Collections.emptyList());
            page.setTotal(0);
            return page;
        });

        experimentService.listExperiments(1, 10, null, null, null);

        verify(experimentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void getExperiment_invokesExpiryHelper() {
        stubDetailPath(5L, List.of(savedEqRow(41L, 5L, 12L, 20, 0, ts())));

        experimentService.getExperiment(5L);

        verify(experimentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void expirePublishedPastDeadlines_invokesUnscopedExpiryUpdate() {
        experimentService.expirePublishedPastDeadlines();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<Experiment>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(experimentMapper).update(isNull(), captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("STATUS"));
        assertTrue(sql.contains("DEADLINETIME"));
    }

    @Test
    void experimentStatusScheduler_hasScheduledAnnotation() throws NoSuchMethodException {
        assertTrue(ExperimentStatusScheduler.class
                .getDeclaredMethod("expirePublishedPastDeadlines")
                .isAnnotationPresent(org.springframework.scheduling.annotation.Scheduled.class));
    }

    private void stubStatusQuestions(LocalDateTime ts) {
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts)));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(question(12L, 25)));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(subQuestion(12L)));
    }

    private static ExperimentStatusUpdateRequest statusRequest(int status) {
        ExperimentStatusUpdateRequest request = new ExperimentStatusUpdateRequest();
        request.setStatus(status);
        return request;
    }

    private void stubDetailPath(long experimentId, List<ExperimentQuestion> rows) {
        when(experimentMapper.selectById(experimentId)).thenReturn(savedExperiment(experimentId, ts()));
        when(experimentQuestionMapper.selectList(any())).thenReturn(rows);
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                question(12L, 25), question(7L, 15)));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                subQuestion(12L), subQuestion(12L), subQuestion(12L),
                subQuestion(7L), subQuestion(7L)));
    }

    private void stubUpdateHappyPath() {
        LocalDateTime ts = ts();
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts));
        stubUpdateReferences();
        when(experimentQuestionMapper.selectList(any()))
                .thenReturn(List.of(
                        savedEqRow(41L, 5L, 12L, 20, 0, ts),
                        savedEqRow(42L, 5L, 7L, 0, 1, ts)))
                .thenReturn(List.of(
                        savedEqRow(41L, 5L, 12L, 20, 0, ts),
                        savedEqRow(42L, 5L, 7L, 0, 1, ts)));
        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                subQuestion(12L), subQuestion(12L), subQuestion(12L),
                subQuestion(7L), subQuestion(7L)));
    }

    private void stubUpdateReferences() {
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                question(12L, 25), question(7L, 15)));
    }

    private static LocalDateTime ts() {
        return LocalDateTime.of(2026, 8, 17, 19, 20, 11);
    }

    private void stubHappyCreatePath(long... questionIds) {
        when(classMapper.selectById(1L)).thenReturn(ownedClass(1L));

        List<Question> questions = new java.util.ArrayList<>();
        for (long id : questionIds) {
            questions.add(question(id, id == 12L ? 25 : 15));
        }
        when(questionMapper.selectBatchIds(anyCollection())).thenReturn(questions);

        doAnswer(invocation -> {
            Experiment exp = invocation.getArgument(0);
            exp.setId(5L);
            return 1;
        }).when(experimentMapper).insert(any(Experiment.class));

        doAnswer(invocation -> {
            ExperimentQuestion row = invocation.getArgument(0);
            row.setId(row.getSortOrder() == 0 ? 41L : 42L);
            return 1;
        }).when(experimentQuestionMapper).insert(any(ExperimentQuestion.class));

        LocalDateTime ts = LocalDateTime.of(2026, 8, 17, 19, 20, 11);
        when(experimentMapper.selectById(5L)).thenReturn(savedExperiment(5L, ts));

        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(
                savedEqRow(41L, 5L, 12L, 20, 0, ts),
                savedEqRow(42L, 5L, 7L, 0, 1, ts)));

        when(subQuestionMapper.selectList(any())).thenReturn(List.of(
                subQuestion(12L), subQuestion(12L), subQuestion(12L),
                subQuestion(7L), subQuestion(7L)));
    }

    private static ExperimentCreateRequest createRequest(ExperimentQuestionCreateRequest... rows) {
        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setTitle("Linux 基础作业一");
        request.setDescription("完成以下题目并在截止时间前提交。");
        request.setClassId(1L);
        request.setDeadlineTime(LocalDateTime.of(2026, 8, 30, 23, 59));
        request.setQuestions(new java.util.ArrayList<>(List.of(rows)));
        return request;
    }

    private static ExperimentQuestionCreateRequest questionRow(long questionId, Integer points) {
        ExperimentQuestionCreateRequest row = new ExperimentQuestionCreateRequest();
        row.setQuestionId(questionId);
        row.setPoints(points);
        return row;
    }

    private static TeachingClass ownedClass(long id) {
        TeachingClass clazz = new TeachingClass();
        clazz.setId(id);
        clazz.setTeacherId(TEACHER_ID);
        return clazz;
    }

    private static Question question(long id, int totalScore) {
        Question q = new Question();
        q.setId(id);
        q.setTitle(id == 12L ? "Shell 脚本练习" : "文件权限管理");
        q.setTotalScore(totalScore);
        return q;
    }

    private static SubQuestion subQuestion(long questionId) {
        SubQuestion sq = new SubQuestion();
        sq.setQuestionId(questionId);
        return sq;
    }

    private static Experiment savedExperiment(long id, LocalDateTime ts) {
        Experiment experiment = new Experiment();
        experiment.setId(id);
        experiment.setTitle("Linux 基础作业一");
        experiment.setDescription("完成以下题目并在截止时间前提交。");
        experiment.setClassId(1L);
        experiment.setStatus(0);
        experiment.setUserId(TEACHER_ID);
        experiment.setCreateTime(ts);
        experiment.setUpdateTime(ts);
        experiment.setDeadlineTime(LocalDateTime.of(2026, 8, 30, 23, 59));
        experiment.setIsDelete(0);
        return experiment;
    }

    private static ExperimentQuestion savedEqRow(
            long id, long experimentId, long questionId, int points, int sortOrder, LocalDateTime ts) {
        ExperimentQuestion row = new ExperimentQuestion();
        row.setId(id);
        row.setExperimentId(experimentId);
        row.setQuestionId(questionId);
        row.setPoints(points);
        row.setSortOrder(sortOrder);
        row.setCreateTime(ts);
        row.setUpdateTime(ts);
        row.setIsDelete(0);
        return row;
    }

    private static Experiment experiment(long id, String title, int status, String deadlineIso) {
        Experiment experiment = new Experiment();
        experiment.setId(id);
        experiment.setTitle(title);
        experiment.setClassId(1L);
        experiment.setStatus(status);
        experiment.setUserId(TEACHER_ID);
        experiment.setCreateTime(LocalDateTime.of(2026, 8, 10, 14, 0));
        experiment.setUpdateTime(LocalDateTime.of(2026, 8, 10, 14, 0));
        if (deadlineIso != null) {
            experiment.setDeadlineTime(LocalDateTime.of(2026, 8, 20, 23, 59));
        }
        experiment.setIsDelete(0);
        return experiment;
    }
}
