package com.tfzhang.linuxback.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.tfzhang.linuxback.common.GradeException;
import com.tfzhang.linuxback.dto.GradeRosterRow;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.excel.GradeExcelRow;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.SubmissionRoundStatus;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.GradeListResponse;
import com.tfzhang.linuxback.vo.GradeStudentRowVO;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherGradeServiceImplTest {

    private static final long TEACHER_B = 9L;
    private static final long CLASS_ID = 88L;
    private static final long EXPERIMENT_ID = 501L;

    @Mock
    private ClassMapper classMapper;
    @Mock
    private ExperimentMapper experimentMapper;
    @Mock
    private ExperimentQuestionMapper experimentQuestionMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private SubmissionMapper submissionMapper;

    private TeacherGradeServiceImpl service;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_B);
        service = new TeacherGradeServiceImpl(
                classMapper,
                experimentMapper,
                experimentQuestionMapper,
                questionMapper,
                classStudentMapper,
                submissionMapper);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void list_nullClassId_is400() {
        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(null, null, null, 1, 10, null));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("classId", ex.getFieldErrors().getFirst().field());
        verify(classStudentMapper, never()).countGradeRoster(anyLong(), anyLong(), any());
    }

    @Test
    void list_otherTeachersClass_is404WithoutRosterQuery() {
        TeachingClass row = teachingClass(CLASS_ID, 1L, "2026春");
        when(classMapper.selectById(CLASS_ID)).thenReturn(row);

        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(CLASS_ID, null, null, 1, 10, null));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(classStudentMapper, never()).countGradeRoster(anyLong(), anyLong(), any());
    }

    @Test
    void list_deletedClass_is404() {
        when(classMapper.selectById(CLASS_ID)).thenReturn(null);

        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(CLASS_ID, null, null, 1, 10, null));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(classStudentMapper, never()).countGradeRoster(anyLong(), anyLong(), any());
    }

    @Test
    void list_matchingSemester_is200() {
        stubOwnedClass();
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(0L);

        GradeListResponse response = service.list(CLASS_ID, null, "2026春", 1, 10, null);

        assertEquals(0, response.getTotal());
        assertFalse(response.isExperimentSelected());
    }

    @Test
    void list_mismatchedSemester_is400() {
        stubOwnedClass();

        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(CLASS_ID, null, "2025秋", 1, 10, null));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("semester", ex.getFieldErrors().getFirst().field());
        verify(classStudentMapper, never()).countGradeRoster(anyLong(), anyLong(), any());
    }

    @Test
    void list_blankSemester_skipsCheck() {
        stubOwnedClass();
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(0L);

        GradeListResponse response = service.list(CLASS_ID, null, "  ", 1, 10, null);
        assertFalse(response.isExperimentSelected());
    }

    @Test
    void list_experimentOnOtherClass_is400() {
        stubOwnedClass();
        Experiment other = experiment(EXPERIMENT_ID, 99L, 1);
        when(experimentMapper.selectById(EXPERIMENT_ID)).thenReturn(other);

        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("experimentId", ex.getFieldErrors().getFirst().field());
        verify(submissionMapper, never()).findCurrentRoundAggregates(anyLong(), any());
    }

    @Test
    void list_missingExperiment_is404() {
        stubOwnedClass();
        when(experimentMapper.selectById(EXPERIMENT_ID)).thenReturn(null);

        GradeException ex = assertThrows(
                GradeException.class,
                () -> service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void list_draftExperiment_isSelected() {
        stubOwnedClass();
        when(experimentMapper.selectById(EXPERIMENT_ID)).thenReturn(experiment(EXPERIMENT_ID, CLASS_ID, 0));
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(0L);

        GradeListResponse response = service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null);

        assertTrue(response.isExperimentSelected());
        verify(submissionMapper, never()).findCurrentRoundAggregates(anyLong(), any());
    }

    @Test
    void list_withoutExperimentId_nullScoresAndNoSubmissionQuery() {
        stubOwnedClass();
        GradeRosterRow row = rosterRow(4102L, 4021L, "2021001234", "张伟");
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(1L);
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, 10, 0))
                .thenReturn(List.of(row));

        GradeListResponse response = service.list(CLASS_ID, null, null, 1, 10, null);

        assertFalse(response.isExperimentSelected());
        GradeStudentRowVO vo = response.getData().getFirst();
        assertEquals("2021001234", vo.getStudentId());
        assertNull(vo.getScore());
        assertNull(vo.getStatus());
        verify(submissionMapper, never()).findCurrentRoundAggregates(anyLong(), any());
    }

    @Test
    void list_unsubmittedStudent_isNullNotPending() {
        stubOwnedClassAndPublishedExperiment();
        GradeRosterRow row = rosterRow(4102L, 4021L, "2021001234", "张伟");
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(1L);
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, 10, 0))
                .thenReturn(List.of(row));
        when(submissionMapper.findCurrentRoundAggregates(eq(EXPERIMENT_ID), any()))
                .thenReturn(List.of());

        GradeListResponse response = service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null);

        assertTrue(response.isExperimentSelected());
        assertNull(response.getData().getFirst().getScore());
        assertNull(response.getData().getFirst().getStatus());
    }

    @Test
    void list_pendingRound_scoreNull() {
        assertDerivedStatus(0, 0, 0, SubmissionRoundStatus.PENDING, null);
    }

    @Test
    void list_gradingInProgress_scoreNull() {
        assertDerivedStatus(1, 0, 0, SubmissionRoundStatus.GRADING_IN_PROGRESS, null);
    }

    @Test
    void list_gradingFailed_scoreNull() {
        assertDerivedStatus(0, 1, 1, SubmissionRoundStatus.GRADING_FAILED, null);
    }

    @Test
    void list_completed_usesScoreSumIncludingZero() {
        assertDerivedStatus(0, 3, 0, SubmissionRoundStatus.COMPLETED, 0);
    }

    @Test
    void list_keepsLatestSubmitTime() {
        stubOwnedClassAndPublishedExperiment();
        GradeRosterRow row = rosterRow(4102L, 4021L, "2021001234", "张伟");
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(1L);
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, 10, 0))
                .thenReturn(List.of(row));
        SubmissionRoundAggregateRow older = aggregate(4021L, LocalDateTime.of(2026, 9, 1, 10, 0), 3, 0, 3, 0, 40L);
        SubmissionRoundAggregateRow newer = aggregate(4021L, LocalDateTime.of(2026, 9, 2, 10, 0), 3, 0, 3, 0, 87L);
        when(submissionMapper.findCurrentRoundAggregates(eq(EXPERIMENT_ID), any()))
                .thenReturn(List.of(older, newer));

        GradeListResponse response = service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null);

        assertEquals(SubmissionRoundStatus.COMPLETED, response.getData().getFirst().getStatus());
        assertEquals(87, response.getData().getFirst().getScore());
    }

    @Test
    void studentName_trimAndEscapeLike() {
        stubOwnedClass();
        when(classStudentMapper.countGradeRoster(eq(TEACHER_B), eq(CLASS_ID), any())).thenReturn(0L);

        service.list(CLASS_ID, null, null, 1, 10, "100%_x");
        verify(classStudentMapper).countGradeRoster(TEACHER_B, CLASS_ID, "%100\\%\\_x%");

        service.list(CLASS_ID, null, null, 1, 10, "  ");
        verify(classStudentMapper).countGradeRoster(TEACHER_B, CLASS_ID, null);
    }

    @Test
    void findGradeRoster_ordersByIdAscNotJoinTime() throws Exception {
        Select select = ClassStudentMapper.class.getMethod(
                "findGradeRoster",
                long.class,
                long.class,
                String.class,
                Integer.class,
                Integer.class).getAnnotation(Select.class);
        String sql = String.join("\n", select.value());
        int orderBy = sql.indexOf("ORDER BY");
        assertTrue(orderBy >= 0);
        String order = sql.substring(orderBy);
        assertTrue(order.contains("cs.id ASC"));
        assertFalse(order.contains("joinTime"));
    }

    @Test
    void export_nullIds_is400WithoutStream() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        GradeException missingClass = assertThrows(
                GradeException.class,
                () -> service.export(null, EXPERIMENT_ID, response));
        assertEquals("INVALID_PARAMETER", missingClass.getCode());

        stubOwnedClass();
        GradeException missingExperiment = assertThrows(
                GradeException.class,
                () -> service.export(CLASS_ID, null, response));
        assertEquals("INVALID_PARAMETER", missingExperiment.getCode());
        assertEquals("experimentId", missingExperiment.getFieldErrors().getFirst().field());
        assertEquals(0, response.getContentAsByteArray().length);
        verify(classStudentMapper, never()).findGradeRoster(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void export_writesAllEnrollmentsWithoutNameFilter() throws Exception {
        stubOwnedClassAndPublishedExperiment();
        GradeRosterRow first = rosterRow(4102L, 4021L, "2021001234", "张伟");
        GradeRosterRow second = rosterRow(4103L, 4022L, "2021005678", "李明");
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, null, null))
                .thenReturn(List.of(first, second));
        when(submissionMapper.findCurrentRoundAggregates(eq(EXPERIMENT_ID), any()))
                .thenReturn(List.of(
                        aggregate(4021L, LocalDateTime.of(2026, 9, 2, 10, 0), 3, 0, 3, 0, 87L)));
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setQuestionId(11L);
        eq.setPoints(0);
        Question question = new Question();
        question.setId(11L);
        question.setTotalScore(4);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(eq));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question));

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.export(CLASS_ID, EXPERIMENT_ID, response);

        assertTrue(response.getContentType().startsWith(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition.contains("filename=\"grades.xlsx\""));
        assertTrue(disposition.contains("filename*=UTF-8''"));
        List<GradeExcelRow> rows = readExcel(response.getContentAsByteArray());
        assertEquals(2, rows.size());
        assertEquals("2021001234", rows.get(0).getStudentId());
        assertEquals("计科2401", rows.get(0).getMajor());
        assertEquals(87, rows.get(0).getScore());
        assertEquals(4, rows.get(0).getTotalScore());
        assertEquals("已完成", rows.get(0).getStatusLabel());
        assertEquals("2021005678", rows.get(1).getStudentId());
        assertNull(rows.get(1).getScore());
        assertEquals(4, rows.get(1).getTotalScore());
        assertEquals("未提交", rows.get(1).getStatusLabel());
        verify(classStudentMapper).findGradeRoster(TEACHER_B, CLASS_ID, null, null, null);
        verify(classStudentMapper, never()).findGradeRoster(
                anyLong(), anyLong(), eq("%张%"), any(), any());
    }

    @Test
    void export_enUs_usesEnglishHeadSheetStatusAndFilename() throws Exception {
        stubOwnedClassAndPublishedExperiment();
        GradeRosterRow first = rosterRow(4102L, 4021L, "2021001234", "张伟");
        GradeRosterRow second = rosterRow(4103L, 4022L, "2021005678", "李明");
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, null, null))
                .thenReturn(List.of(first, second));
        when(submissionMapper.findCurrentRoundAggregates(eq(EXPERIMENT_ID), any()))
                .thenReturn(List.of(
                        aggregate(4021L, LocalDateTime.of(2026, 9, 2, 10, 0), 3, 0, 3, 0, 87L)));
        ExperimentQuestion eq = new ExperimentQuestion();
        eq.setQuestionId(11L);
        eq.setPoints(0);
        Question question = new Question();
        question.setId(11L);
        question.setTotalScore(4);
        when(experimentQuestionMapper.selectList(any())).thenReturn(List.of(eq));
        when(questionMapper.selectBatchIds(any())).thenReturn(List.of(question));
        LocaleContextHolder.setLocale(Locale.US);

        MockHttpServletResponse response = new MockHttpServletResponse();
        service.export(CLASS_ID, EXPERIMENT_ID, response);

        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition.contains("filename=\"grades.xlsx\""));
        assertFalse(disposition.contains("成绩"));
        int star = disposition.indexOf("filename*=UTF-8''");
        String encoded = disposition.substring(star + "filename*=UTF-8''".length());
        assertEquals(
                "22计科3班-实验一-grades.xlsx",
                URLDecoder.decode(encoded, StandardCharsets.UTF_8));

        RawSheet raw = readRawExcel(response.getContentAsByteArray());
        assertEquals("Grades", raw.sheetName());
        assertEquals("Student ID", raw.rows().get(0).get(0));
        assertEquals("Name", raw.rows().get(0).get(1));
        assertEquals("Major", raw.rows().get(0).get(2));
        assertEquals("Score", raw.rows().get(0).get(3));
        assertEquals("Total", raw.rows().get(0).get(4));
        assertEquals("Status", raw.rows().get(0).get(5));
        assertEquals("Completed", raw.rows().get(1).get(5));
        assertEquals("Not submitted", raw.rows().get(2).get(5));
    }

    @Test
    void statusLabel_maps008Strings() {
        assertEquals("待批改", TeacherGradeServiceImpl.statusLabel(SubmissionRoundStatus.PENDING));
        assertEquals("批改中", TeacherGradeServiceImpl.statusLabel(SubmissionRoundStatus.GRADING_IN_PROGRESS));
        assertEquals("已完成", TeacherGradeServiceImpl.statusLabel(SubmissionRoundStatus.COMPLETED));
        assertEquals("批改异常", TeacherGradeServiceImpl.statusLabel(SubmissionRoundStatus.GRADING_FAILED));
        assertNull(TeacherGradeServiceImpl.statusLabel(null));
    }

    private void assertDerivedStatus(
            long gradingCount, long doneCount, long failedCount, String expectedStatus, Integer expectedScore) {
        stubOwnedClassAndPublishedExperiment();
        GradeRosterRow row = rosterRow(4102L, 4021L, "2021001234", "张伟");
        when(classStudentMapper.countGradeRoster(TEACHER_B, CLASS_ID, null)).thenReturn(1L);
        when(classStudentMapper.findGradeRoster(TEACHER_B, CLASS_ID, null, 10, 0))
                .thenReturn(List.of(row));
        when(submissionMapper.findCurrentRoundAggregates(eq(EXPERIMENT_ID), any()))
                .thenReturn(List.of(aggregate(
                        4021L,
                        LocalDateTime.of(2026, 9, 2, 10, 0),
                        3,
                        gradingCount,
                        doneCount,
                        failedCount,
                        expectedScore == null ? 40L : expectedScore.longValue())));

        GradeListResponse response = service.list(CLASS_ID, EXPERIMENT_ID, null, 1, 10, null);
        GradeStudentRowVO vo = response.getData().getFirst();
        assertEquals(expectedStatus, vo.getStatus());
        assertEquals(expectedScore, vo.getScore());
    }

    private void stubOwnedClass() {
        when(classMapper.selectById(CLASS_ID)).thenReturn(teachingClass(CLASS_ID, TEACHER_B, "2026春"));
    }

    private void stubOwnedClassAndPublishedExperiment() {
        stubOwnedClass();
        Experiment experiment = experiment(EXPERIMENT_ID, CLASS_ID, 1);
        experiment.setLastPublishedTime(LocalDateTime.of(2026, 9, 1, 8, 0));
        when(experimentMapper.selectById(EXPERIMENT_ID)).thenReturn(experiment);
    }

    private static TeachingClass teachingClass(long id, long teacherId, String semester) {
        TeachingClass row = new TeachingClass();
        row.setId(id);
        row.setName("22计科3班");
        row.setTeacherId(teacherId);
        row.setSemester(semester);
        return row;
    }

    private static Experiment experiment(long id, long classId, int status) {
        Experiment experiment = new Experiment();
        experiment.setId(id);
        experiment.setClassId(classId);
        experiment.setStatus(status);
        experiment.setTitle("实验一");
        return experiment;
    }

    private static GradeRosterRow rosterRow(long classStudentId, long userId, String account, String name) {
        GradeRosterRow row = new GradeRosterRow();
        row.setClassStudentId(classStudentId);
        row.setUserId(userId);
        row.setStudentId(account);
        row.setStudentName(name);
        row.setClassName("22计科3班");
        row.setMajor("计科2401");
        return row;
    }

    private static SubmissionRoundAggregateRow aggregate(
            long studentId,
            LocalDateTime submitTime,
            long questionCount,
            long gradingCount,
            long doneCount,
            long failedCount,
            Long scoreSum) {
        SubmissionRoundAggregateRow row = new SubmissionRoundAggregateRow();
        row.setStudentId(studentId);
        row.setSubmitTime(submitTime);
        row.setQuestionCount(questionCount);
        row.setGradingCount(gradingCount);
        row.setDoneCount(doneCount);
        row.setFailedCount(failedCount);
        row.setScoreSum(scoreSum);
        return row;
    }

    private static List<GradeExcelRow> readExcel(byte[] bytes) {
        List<GradeExcelRow> rows = new ArrayList<>();
        EasyExcel.read(new ByteArrayInputStream(bytes), GradeExcelRow.class, new ReadListener<GradeExcelRow>() {
            @Override
            public void invoke(GradeExcelRow data, AnalysisContext context) {
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        return rows;
    }

    private static RawSheet readRawExcel(byte[] bytes) {
        List<Map<Integer, String>> rows = new ArrayList<>();
        String[] sheetName = {null};
        EasyExcel.read(new ByteArrayInputStream(bytes), new ReadListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                if (sheetName[0] == null) {
                    sheetName[0] = context.readSheetHolder().getSheetName();
                }
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().headRowNumber(0).doRead();
        return new RawSheet(sheetName[0], rows);
    }

    private record RawSheet(String sheetName, List<Map<Integer, String>> rows) {
    }
}
