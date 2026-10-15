package com.tfzhang.linuxback.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.common.RosterException;
import com.tfzhang.linuxback.dto.RosterConfirmRequest;
import com.tfzhang.linuxback.dto.RosterCreateRequest;
import com.tfzhang.linuxback.dto.RosterRowRequest;
import com.tfzhang.linuxback.dto.RosterUpdateRequest;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.excel.RosterExcelRow;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.RosterConfirmResponse;
import com.tfzhang.linuxback.vo.RosterPreviewResponse;
import com.tfzhang.linuxback.vo.RosterPreviewRowVO;
import com.tfzhang.linuxback.vo.RosterResetPasswordResponse;
import com.tfzhang.linuxback.vo.RosterRowResultVO;
import com.tfzhang.linuxback.vo.RosterStudentRowVO;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherRosterServiceImplTest {

    private static final long TEACHER_A = 1L;
    private static final long TEACHER_B = 9L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private ClassMapper classMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private TeacherRosterServiceImpl service;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setTeacher(TEACHER_B);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        service = new TeacherRosterServiceImpl(
                userMapper, classMapper, classStudentMapper, passwordEncoder, transactionManager);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void newStudent_isCreatedWithHashedPasswordAndEnrollment() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubUserInsert(4021L);
        stubClassInsert(88L);

        RosterRowResultVO result = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));

        assertEquals(TeacherRosterServiceImpl.CREATED, result.getResult());
        assertEquals(4021L, result.getUserId());
        assertEquals(88L, result.getClassId());
        assertEquals(1, result.getRowIndex());
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User stored = userCaptor.getValue();
        assertEquals("2021001234", stored.getUserAccount());
        assertEquals("张伟", stored.getUserName());
        assertEquals("软件工程", stored.getMajor());
        assertEquals("student", stored.getUserType());
        assertEquals("user", stored.getUserRole());
        assertEquals(TEACHER_B, stored.getCreatedBy());
        assertNotEquals("secret123", stored.getUserPassword());
        assertTrue(passwordEncoder.matches("secret123", stored.getUserPassword()));
        ArgumentCaptor<ClassStudent> enrollment = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(enrollment.capture());
        assertEquals(88L, enrollment.getValue().getClassId());
        assertEquals(4021L, enrollment.getValue().getStudentId());
    }

    @Test
    void existingStudentNewClass_isEnrolledWithoutNewUserOrPasswordChange() {
        User existing = student(4021L, "2021001234", "张伟", "计算机科学与技术", TEACHER_A);
        existing.setUserPassword(passwordEncoder.encode("old-pass"));
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubClassInsert(88L);

        RosterRowResultVO result = service.create(
                createRequest("2021001234", "张伟", "软件工程", "ignored-password"));

        assertEquals(TeacherRosterServiceImpl.ENROLLED, result.getResult());
        verify(userMapper, never()).insert(any(User.class));
        verify(userMapper, never()).updateById(any(User.class));
        assertEquals("计算机科学与技术", existing.getMajor());
        assertTrue(passwordEncoder.matches("old-pass", existing.getUserPassword()));
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("专业与库中记录不同")));
    }

    @Test
    void previewMajorMismatch_enUs_isWarningWithEnglishMessage() throws Exception {
        User existing = student(4021L, "2021001234", "张伟", "计算机科学与技术", TEACHER_A);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(null);
        LocaleContextHolder.setLocale(Locale.US);

        RosterPreviewResponse response = service.preview(
                xlsx(excelRow("2021001234", "张伟", "软件工程", "secret123")));
        RosterPreviewRowVO row = response.getRows().getFirst();

        assertEquals(TeacherRosterServiceImpl.WARNING, row.getStatus());
        assertTrue(row.getMessages().stream().anyMatch(m -> m.contains("major differs")));
        assertFalse(row.getMessages().stream().anyMatch(m -> m.contains("专业与库中记录不同")));
    }

    @Test
    void nameMismatch_isRejectedWithNoWrites() {
        when(userMapper.selectOne(any())).thenReturn(student(4021L, "2021001234", "李明", "软件工程", TEACHER_A));

        RosterRowResultVO result = service.create(createRequest("2021001234", "李民", "软件工程", "secret123"));

        assertEquals(TeacherRosterServiceImpl.REJECTED, result.getResult());
        assertNull(result.getUserId());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("姓名与库中记录不一致")));
        verify(userMapper, never()).insert(any(User.class));
        verify(classMapper, never()).insert(any(TeachingClass.class));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void alreadyEnrolled_isAlreadyImported() {
        User existing = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_A);
        TeachingClass teachingClass = teachingClass(88L, TEACHER_B);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(teachingClass);
        when(classStudentMapper.selectOne(any())).thenReturn(enrollment(88L, 4021L));

        RosterRowResultVO result = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));

        assertEquals(TeacherRosterServiceImpl.ALREADY_IMPORTED, result.getResult());
        assertEquals(4021L, result.getUserId());
        assertEquals(88L, result.getClassId());
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void confirmDuplicateInFile_secondRowRejectedNotAlreadyImported() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubUserInsert(4021L);
        stubClassInsert(88L);

        RosterConfirmResponse response = service.confirm(confirm(
                row(2, "2021001234", "张伟", "软件工程", "secret123"),
                row(3, "2021001234", "张伟", "软件工程", "secret123")));

        assertEquals(TeacherRosterServiceImpl.CREATED, response.getResults().get(0).getResult());
        assertEquals(TeacherRosterServiceImpl.REJECTED, response.getResults().get(1).getResult());
        assertTrue(response.getResults().get(1).getMessages().stream()
                .anyMatch(m -> m.contains("与第 2 行重复")));
        verify(classStudentMapper, times(1)).insert(any(ClassStudent.class));
    }

    @Test
    void confirmMixedThenReupload_alreadyImportedThenCreated() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubUserInsert(4021L);
        stubClassInsert(88L);

        RosterConfirmResponse first = service.confirm(confirm(
                row(2, "2021001234", "张伟", "软件工程", "secret123"),
                row(3, "abc", "李明", "软件工程", "secret123")));
        assertEquals(TeacherRosterServiceImpl.CREATED, first.getResults().get(0).getResult());
        assertEquals(TeacherRosterServiceImpl.REJECTED, first.getResults().get(1).getResult());

        User created = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_B);
        TeachingClass teachingClass = teachingClass(88L, TEACHER_B);
        org.mockito.Mockito.reset(userMapper, classMapper, classStudentMapper);
        when(userMapper.selectOne(any())).thenReturn(created);
        when(classMapper.selectOne(any())).thenReturn(teachingClass);
        when(classStudentMapper.selectOne(any())).thenReturn(enrollment(88L, 4021L));
        RosterRowResultVO replayA = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));
        assertEquals(TeacherRosterServiceImpl.ALREADY_IMPORTED, replayA.getResult());

        org.mockito.Mockito.reset(userMapper, classMapper, classStudentMapper);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectOne(any())).thenReturn(teachingClass);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubUserInsert(4022L);
        RosterRowResultVO fixedB = service.create(createRequest("2021005678", "李明", "软件工程", "secret123"));
        assertEquals(TeacherRosterServiceImpl.CREATED, fixedB.getResult());
    }

    @Test
    void blankOrNonNumericStudentId_isRejected() {
        RosterRowResultVO blank = service.create(createRequest("  ", "张伟", "软件工程", "secret123"));
        assertEquals(TeacherRosterServiceImpl.REJECTED, blank.getResult());
        assertTrue(blank.getMessages().contains("studentId 不能为空"));

        RosterRowResultVO letters = service.create(createRequest("abc", "张伟", "软件工程", "secret123"));
        assertEquals(TeacherRosterServiceImpl.REJECTED, letters.getResult());
        assertTrue(letters.getMessages().contains("studentId 必须为纯数字"));
    }

    @Test
    void missingPasswordOnNewAccount_isRejected_onRetakeIsIgnored() {
        when(userMapper.selectOne(any())).thenReturn(null);
        RosterCreateRequest missing = createRequest("2021001234", "张伟", "软件工程", null);
        RosterRowResultVO rejected = service.create(missing);
        assertEquals(TeacherRosterServiceImpl.REJECTED, rejected.getResult());
        assertTrue(rejected.getMessages().contains("新学号必须填写 initialPassword"));

        User existing = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_A);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubClassInsert(88L);
        RosterRowResultVO enrolled = service.create(createRequest("2021001234", "张伟", "软件工程", null));
        assertEquals(TeacherRosterServiceImpl.ENROLLED, enrolled.getResult());
    }

    @Test
    void singleInsertAndOneRowConfirm_matchExceptRowIndex() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubUserInsert(4021L);
        stubClassInsert(88L);

        RosterRowResultVO single = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));
        RosterRowResultVO confirm = service.confirm(confirm(
                row(7, "2021001234", "张伟", "软件工程", "secret123"))).getResults().getFirst();

        assertEquals(single.getResult(), confirm.getResult());
        assertEquals(single.getUserId(), confirm.getUserId());
        assertEquals(single.getClassId(), confirm.getClassId());
        assertEquals(single.getMessages(), confirm.getMessages());
        assertEquals(1, single.getRowIndex());
        assertEquals(7, confirm.getRowIndex());
    }

    @Test
    void findOrCreateClass_duplicateKeyRetriesSelect() {
        TeachingClass existing = teachingClass(88L, TEACHER_B);
        when(classMapper.selectOne(any())).thenReturn(null, existing);
        when(classMapper.insert(any(TeachingClass.class))).thenThrow(new DuplicateKeyException("uk_class"));

        TeachingClass resolved = service.findOrCreateClass(TEACHER_B, "22计科3班", "2026春");

        assertEquals(88L, resolved.getId());
        verify(classMapper).insert(any(TeachingClass.class));
        verify(classMapper, times(2)).selectOne(any());
    }

    @Test
    void teacherAccountCollision_isRejected() {
        User teacher = student(9L, "2021001234", "张伟", null, null);
        teacher.setUserType("teacher");
        when(userMapper.selectOne(any())).thenReturn(teacher);

        RosterRowResultVO result = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));
        assertEquals(TeacherRosterServiceImpl.REJECTED, result.getResult());
        assertTrue(result.getMessages().stream().anyMatch(m -> m.contains("已被其他类型账号占用")));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void crossTeacherRetake_enrollsIntoCallerClassWithoutChangingCreatedBy() {
        User existing = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_A);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        stubClassInsert(99L);

        RosterRowResultVO result = service.create(createRequest("2021001234", "张伟", "软件工程", "secret123"));

        assertEquals(TeacherRosterServiceImpl.ENROLLED, result.getResult());
        assertEquals(99L, result.getClassId());
        assertEquals(TEACHER_A, existing.getCreatedBy());
        ArgumentCaptor<TeachingClass> classCaptor = ArgumentCaptor.forClass(TeachingClass.class);
        verify(classMapper).insert(classCaptor.capture());
        assertEquals(TEACHER_B, classCaptor.getValue().getTeacherId());
    }

    @Test
    void previewExistingEnrollment_alreadyImportedNullPasswordNoInserts() throws Exception {
        User existing = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_A);
        TeachingClass teachingClass = teachingClass(88L, TEACHER_B);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(classMapper.selectOne(any())).thenReturn(teachingClass);
        when(classStudentMapper.selectOne(any())).thenReturn(enrollment(88L, 4021L));

        RosterPreviewResponse response = service.preview(xlsx(excelRow("2021001234", "张伟", "软件工程", "secret123")));
        RosterPreviewRowVO row = response.getRows().getFirst();
        assertEquals(TeacherRosterServiceImpl.ALREADY_IMPORTED, row.getStatus());
        assertNull(row.getInitialPassword());
        verify(userMapper, never()).insert(any(User.class));
        verify(classMapper, never()).insert(any(TeachingClass.class));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void previewNumericStudentIdAndSkipsBlankRows() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, NumericWriteRow.class)
                .sheet()
                .doWrite(List.of(
                        numericRow(2021001234L, "张伟"),
                        blankNumericRow(),
                        numericRow(2021005678L, "李明")));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());

        RosterPreviewResponse response = service.preview(file);
        assertEquals(2, response.getRows().size());
        assertEquals("2021001234", response.getRows().get(0).getStudentId());
        assertEquals(2, response.getRows().get(0).getRowIndex());
        assertEquals("2021005678", response.getRows().get(1).getStudentId());
        assertEquals(4, response.getRows().get(1).getRowIndex());
        assertNotEquals("2.021001234E9", response.getRows().get(0).getStudentId());
    }

    @Test
    void previewRejectsNonXlsx() {
        MockMultipartFile file = new MockMultipartFile("file", "roster.csv", "text/csv", "a,b".getBytes());
        assertThrows(RosterException.class, () -> service.preview(file));
    }

    @Test
    void list_twoEnrollmentsSameStudent_areTwoRows() {
        RosterStudentRowVO first = listRow(4102L, "2021001234", 88L, "22计科3班", "2026春");
        RosterStudentRowVO second = listRow(4103L, "2021001234", 92L, "22软工1班", "2026春");
        when(classStudentMapper.countTeacherRoster(TEACHER_B, null, null, null)).thenReturn(2L);
        when(classStudentMapper.findTeacherRoster(TEACHER_B, null, null, null, 10, 0))
                .thenReturn(List.of(first, second));

        PageResult<RosterStudentRowVO> result = service.list(1, 10, null, null, null);

        assertEquals(2, result.getTotal());
        assertEquals(2, result.getData().size());
        assertEquals(4102L, result.getData().get(0).getClassStudentId());
        assertEquals(88L, result.getData().get(0).getClassId());
        assertEquals(4103L, result.getData().get(1).getClassStudentId());
        assertEquals(92L, result.getData().get(1).getClassId());
        assertEquals("2021001234", result.getData().get(0).getStudentId());
        assertEquals("2021001234", result.getData().get(1).getStudentId());
    }

    @Test
    void findTeacherRoster_ordersByIdDescNotJoinTime() throws Exception {
        Select select = ClassStudentMapper.class.getMethod(
                "findTeacherRoster",
                long.class,
                Long.class,
                String.class,
                String.class,
                int.class,
                int.class).getAnnotation(Select.class);
        String sql = String.join("\n", select.value());
        int orderBy = sql.indexOf("ORDER BY");
        assertTrue(orderBy >= 0);
        String order = sql.substring(orderBy);
        assertTrue(order.contains("cs.id DESC"));
        assertFalse(order.contains("joinTime"));
    }

    @Test
    void list_filtersAreAndCombinedAndUnknownClassIdIsEmpty() {
        when(classStudentMapper.countTeacherRoster(TEACHER_B, 88L, null, null)).thenReturn(1L);
        when(classStudentMapper.findTeacherRoster(TEACHER_B, 88L, null, null, 10, 0))
                .thenReturn(List.of(listRow(4102L, "2021001234", 88L, "22计科3班", "2026春")));
        when(classStudentMapper.countTeacherRoster(TEACHER_B, null, "2026春", null)).thenReturn(2L);
        when(classStudentMapper.findTeacherRoster(TEACHER_B, null, "2026春", null, 10, 0))
                .thenReturn(List.of(
                        listRow(4102L, "2021001234", 88L, "22计科3班", "2026春"),
                        listRow(4103L, "2021001234", 92L, "22软工1班", "2026春")));
        when(classStudentMapper.countTeacherRoster(TEACHER_B, 88L, "2026秋", null)).thenReturn(0L);
        when(classStudentMapper.countTeacherRoster(TEACHER_B, 999L, null, null)).thenReturn(0L);

        PageResult<RosterStudentRowVO> classOnly = service.list(1, 10, 88L, null, null);
        assertEquals(1, classOnly.getTotal());
        assertEquals(88L, classOnly.getData().getFirst().getClassId());

        PageResult<RosterStudentRowVO> semesterOnly = service.list(1, 10, null, "2026春", null);
        assertEquals(2, semesterOnly.getTotal());

        PageResult<RosterStudentRowVO> bothNoMatch = service.list(1, 10, 88L, "2026秋", null);
        assertEquals(0, bothNoMatch.getTotal());
        assertTrue(bothNoMatch.getData().isEmpty());
        verify(classStudentMapper, never()).findTeacherRoster(
                eq(TEACHER_B), eq(88L), eq("2026秋"), isNull(), anyInt(), anyInt());

        PageResult<RosterStudentRowVO> unknown = service.list(1, 10, 999L, null, null);
        assertEquals(0, unknown.getTotal());
        assertTrue(unknown.getData().isEmpty());
    }

    @Test
    void list_studentNameTrimEscapeAndBlankMeansNoLike() {
        when(classStudentMapper.countTeacherRoster(anyLong(), any(), any(), any())).thenReturn(0L);

        service.list(1, 10, null, null, " 张 ");
        verify(classStudentMapper).countTeacherRoster(TEACHER_B, null, null, "%张%");

        service.list(1, 10, null, null, "100%_x");
        verify(classStudentMapper).countTeacherRoster(TEACHER_B, null, null, "%100\\%\\_x%");

        service.list(1, 10, null, null, "  ");
        service.list(1, 10, null, null, null);
        verify(classStudentMapper, times(2)).countTeacherRoster(TEACHER_B, null, null, null);
    }

    @Test
    void escapeLike_escapesWildcards() {
        assertEquals("100\\%\\_x", TeacherRosterServiceImpl.escapeLike("100%_x"));
        assertNull(TeacherRosterServiceImpl.toStudentNameLike("  "));
        assertEquals("%张%", TeacherRosterServiceImpl.toStudentNameLike(" 张 "));
    }

    @Test
    void update_sameClass_updatesUserOnly() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        when(userMapper.update(isNull(), any())).thenReturn(1);

        RosterStudentRowVO vo = service.updateEnrollment(4102L, updateRequest("李伟", "计算机科学与技术", 88L));

        assertEquals(4102L, vo.getClassStudentId());
        assertEquals("李伟", vo.getStudentName());
        assertEquals("计算机科学与技术", vo.getMajor());
        assertEquals(88L, vo.getClassId());
        verify(userMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(classStudentMapper, never()).deleteById(any(Long.class));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void update_moveSameSemester_softDeletesAndInsertsNewRow() {
        ClassStudent current = stubActionable(4102L, 88L, TEACHER_B, "2026春");
        current.setJoinTime(LocalDateTime.of(2026, 9, 1, 10, 0));
        TeachingClass target = teachingClass(92L, TEACHER_B, "22软工1班", "2026春");
        when(classMapper.selectById(92L)).thenReturn(target);
        when(classStudentMapper.selectOne(any())).thenReturn(null);
        when(classStudentMapper.deleteById(4102L)).thenReturn(1);
        doAnswer(invocation -> {
            ClassStudent inserted = invocation.getArgument(0);
            inserted.setId(5001L);
            return 1;
        }).when(classStudentMapper).insert(any(ClassStudent.class));
        when(userMapper.update(isNull(), any())).thenReturn(1);

        RosterStudentRowVO vo = service.updateEnrollment(4102L, updateRequest("张伟", "软件工程", 92L));

        assertEquals(5001L, vo.getClassStudentId());
        assertEquals(92L, vo.getClassId());
        assertEquals("22软工1班", vo.getClassName());
        assertNotNull(vo.getJoinTime());
        assertTrue(vo.getJoinTime().isAfter(current.getJoinTime()));
        verify(classStudentMapper).deleteById(4102L);
        ArgumentCaptor<ClassStudent> inserted = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(inserted.capture());
        assertEquals(92L, inserted.getValue().getClassId());
        assertEquals(4021L, inserted.getValue().getStudentId());
        verify(userMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void update_differentSemester_is400WithNoWrites() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        when(classMapper.selectById(92L)).thenReturn(teachingClass(92L, TEACHER_B, "22软工1班", "2026秋"));

        RosterException ex = assertThrows(RosterException.class,
                () -> service.updateEnrollment(4102L, updateRequest("张伟", "软件工程", 92L)));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("classId", ex.getFieldErrors().getFirst().field());
        verifyNoEnrollmentOrUserWrites();
    }

    @Test
    void update_alreadyInTargetClass_is409WithNoWrites() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        when(classMapper.selectById(92L)).thenReturn(teachingClass(92L, TEACHER_B, "22软工1班", "2026春"));
        when(classStudentMapper.selectOne(any())).thenReturn(enrollment(92L, 4021L));

        RosterException ex = assertThrows(RosterException.class,
                () -> service.updateEnrollment(4102L, updateRequest("张伟", "软件工程", 92L)));
        assertEquals("CONFLICT", ex.getCode());
        assertEquals("classId", ex.getFieldErrors().getFirst().field());
        verifyNoEnrollmentOrUserWrites();
    }

    @Test
    void update_otherTeacherOrDeletedClass_is404() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        when(classMapper.selectById(92L)).thenReturn(teachingClass(92L, TEACHER_A, "别人的班", "2026春"));
        RosterException otherTeacher = assertThrows(RosterException.class,
                () -> service.updateEnrollment(4102L, updateRequest("张伟", "软件工程", 92L)));
        assertEquals("NOT_FOUND", otherTeacher.getCode());

        when(classMapper.selectById(93L)).thenReturn(null);
        RosterException deleted = assertThrows(RosterException.class,
                () -> service.updateEnrollment(4102L, updateRequest("张伟", "软件工程", 93L)));
        assertEquals("NOT_FOUND", deleted.getCode());
        verifyNoEnrollmentOrUserWrites();
    }

    @Test
    void update_overLengthNameOrMajor_is400() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        RosterException name = assertThrows(RosterException.class, () -> service.updateEnrollment(
                4102L, updateRequest("x".repeat(TeacherRosterServiceImpl.MAX_NAME + 1), "软件工程", 88L)));
        assertEquals("INVALID_PARAMETER", name.getCode());
        assertEquals("studentName", name.getFieldErrors().getFirst().field());

        RosterException major = assertThrows(RosterException.class, () -> service.updateEnrollment(
                4102L, updateRequest("张伟", "x".repeat(TeacherRosterServiceImpl.MAX_MAJOR + 1), 88L)));
        assertEquals("INVALID_PARAMETER", major.getCode());
        assertEquals("major", major.getFieldErrors().getFirst().field());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void delete_softDeletesThenSecondCallIsIdempotent_otherTeacherIs404() {
        ClassStudent enrollment = enrollment(4102L, 88L, 4021L, 0);
        when(classStudentMapper.findByIdIncludingDeleted(4102L)).thenReturn(enrollment);
        when(classMapper.selectById(88L)).thenReturn(teachingClass(88L, TEACHER_B));
        when(classStudentMapper.deleteById(4102L)).thenReturn(1);

        service.deleteEnrollment(4102L);
        verify(classStudentMapper).deleteById(4102L);

        enrollment.setIsDelete(1);
        service.deleteEnrollment(4102L);
        verify(classStudentMapper, times(1)).deleteById(4102L);

        ClassStudent foreign = enrollment(9L, 77L, 4021L, 0);
        when(classStudentMapper.findByIdIncludingDeleted(9L)).thenReturn(foreign);
        when(classMapper.selectById(77L)).thenReturn(teachingClass(77L, TEACHER_A));
        RosterException ex = assertThrows(RosterException.class, () -> service.deleteEnrollment(9L));
        assertEquals("NOT_FOUND", ex.getCode());
        verify(classStudentMapper, never()).deleteById(9L);
    }

    @Test
    void resetPassword_hashesDefaultAndOmitsPassword_deletedIs404() {
        stubActionable(4102L, 88L, TEACHER_B, "2026春");
        when(userMapper.update(isNull(), any())).thenReturn(1);

        RosterResetPasswordResponse response = service.resetPassword(4102L);
        assertEquals(4102L, response.getClassStudentId());
        assertTrue(response.isReset());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        String hash = captor.getValue().getParamNameValuePairs().values().stream()
                .map(Object::toString)
                .filter(v -> v.startsWith("$2"))
                .findFirst()
                .orElseThrow();
        assertTrue(passwordEncoder.matches(TeacherRosterServiceImpl.DEFAULT_PASSWORD, hash));
        assertTrue(captor.getValue().getSqlSet().contains("tokenVersion = tokenVersion + 1"));

        when(classStudentMapper.selectById(4102L)).thenReturn(null);
        RosterException deleted = assertThrows(RosterException.class, () -> service.resetPassword(4102L));
        assertEquals("NOT_FOUND", deleted.getCode());
    }

    @Test
    void updateAndReset_otherTeacherOrAlreadyDeleted_is404() {
        when(classStudentMapper.selectById(9L)).thenReturn(null);
        assertEquals("NOT_FOUND", assertThrows(RosterException.class,
                () -> service.updateEnrollment(9L, updateRequest("张伟", "软件工程", 88L))).getCode());
        assertEquals("NOT_FOUND", assertThrows(RosterException.class, () -> service.resetPassword(9L)).getCode());

        ClassStudent enrollment = enrollment(9L, 77L, 4021L, 0);
        when(classStudentMapper.selectById(9L)).thenReturn(enrollment);
        when(classMapper.selectById(77L)).thenReturn(teachingClass(77L, TEACHER_A));
        assertEquals("NOT_FOUND", assertThrows(RosterException.class,
                () -> service.updateEnrollment(9L, updateRequest("张伟", "软件工程", 77L))).getCode());
        assertEquals("NOT_FOUND", assertThrows(RosterException.class, () -> service.resetPassword(9L)).getCode());
    }

    private ClassStudent stubActionable(long classStudentId, long classId, long teacherId, String semester) {
        ClassStudent enrollment = enrollment(classStudentId, classId, 4021L, 0);
        TeachingClass classRow = teachingClass(classId, teacherId, "22计科3班", semester);
        User user = student(4021L, "2021001234", "张伟", "软件工程", TEACHER_A);
        when(classStudentMapper.selectById(classStudentId)).thenReturn(enrollment);
        when(classMapper.selectById(classId)).thenReturn(classRow);
        when(userMapper.selectById(4021L)).thenReturn(user);
        return enrollment;
    }

    private void verifyNoEnrollmentOrUserWrites() {
        verify(classStudentMapper, never()).deleteById(any(Long.class));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
        verify(userMapper, never()).update(any(), any());
    }

    private static RosterUpdateRequest updateRequest(String name, String major, long classId) {
        RosterUpdateRequest request = new RosterUpdateRequest();
        request.setStudentName(name);
        request.setMajor(major);
        request.setClassId(classId);
        return request;
    }

    private static RosterStudentRowVO listRow(
            long classStudentId, String studentId, long classId, String className, String semester) {
        RosterStudentRowVO row = new RosterStudentRowVO();
        row.setClassStudentId(classStudentId);
        row.setStudentId(studentId);
        row.setStudentName("张伟");
        row.setMajor("软件工程");
        row.setClassId(classId);
        row.setClassName(className);
        row.setSemester(semester);
        return row;
    }

    private void stubUserInsert(long id) {
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(id);
            return 1;
        }).when(userMapper).insert(any(User.class));
    }

    private void stubClassInsert(long id) {
        doAnswer(invocation -> {
            TeachingClass teachingClass = invocation.getArgument(0);
            teachingClass.setId(id);
            return 1;
        }).when(classMapper).insert(any(TeachingClass.class));
    }

    private static RosterCreateRequest createRequest(
            String studentId, String name, String major, String password) {
        RosterCreateRequest request = new RosterCreateRequest();
        request.setStudentId(studentId);
        request.setStudentName(name);
        request.setMajor(major);
        request.setClassName("22计科3班");
        request.setSemester("2026春");
        request.setInitialPassword(password);
        return request;
    }

    private static RosterConfirmRequest confirm(RosterRowRequest... rows) {
        RosterConfirmRequest request = new RosterConfirmRequest();
        request.setRows(List.of(rows));
        return request;
    }

    private static RosterRowRequest row(
            int rowIndex, String studentId, String name, String major, String password) {
        RosterRowRequest request = new RosterRowRequest();
        request.setRowIndex(rowIndex);
        request.setStudentId(studentId);
        request.setStudentName(name);
        request.setMajor(major);
        request.setClassName("22计科3班");
        request.setSemester("2026春");
        request.setInitialPassword(password);
        return request;
    }

    private static User student(long id, String account, String name, String major, Long createdBy) {
        User user = new User();
        user.setId(id);
        user.setUserAccount(account);
        user.setUserName(name);
        user.setMajor(major);
        user.setUserType("student");
        user.setUserRole("user");
        user.setCreatedBy(createdBy);
        return user;
    }

    private static TeachingClass teachingClass(long id, long teacherId) {
        return teachingClass(id, teacherId, "22计科3班", "2026春");
    }

    private static TeachingClass teachingClass(long id, long teacherId, String name, String semester) {
        TeachingClass teachingClass = new TeachingClass();
        teachingClass.setId(id);
        teachingClass.setTeacherId(teacherId);
        teachingClass.setName(name);
        teachingClass.setSemester(semester);
        return teachingClass;
    }

    private static ClassStudent enrollment(long classId, long studentId) {
        return enrollment(null, classId, studentId, 0);
    }

    private static ClassStudent enrollment(Long id, long classId, long studentId, int isDelete) {
        ClassStudent enrollment = new ClassStudent();
        enrollment.setId(id);
        enrollment.setClassId(classId);
        enrollment.setStudentId(studentId);
        enrollment.setIsDelete(isDelete);
        return enrollment;
    }

    private static RosterExcelRow excelRow(String studentId, String name, String major, String password) {
        RosterExcelRow row = new RosterExcelRow();
        row.setStudentId(studentId);
        row.setStudentName(name);
        row.setMajor(major);
        row.setClassName("22计科3班");
        row.setSemester("2026春");
        row.setInitialPassword(password);
        return row;
    }

    private static MockMultipartFile xlsx(RosterExcelRow... rows) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, RosterExcelRow.class).sheet().doWrite(List.of(rows));
        return new MockMultipartFile(
                "file",
                "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                out.toByteArray());
    }

    private static NumericWriteRow blankNumericRow() {
        NumericWriteRow row = new NumericWriteRow();
        row.setStudentName("");
        row.setMajor("");
        row.setClassName("");
        row.setSemester("");
        row.setInitialPassword("");
        return row;
    }

    private static NumericWriteRow numericRow(Long studentId, String name) {
        NumericWriteRow row = new NumericWriteRow();
        row.studentId = studentId;
        row.studentName = name;
        row.major = name == null ? null : "软件工程";
        row.className = name == null ? null : "22计科3班";
        row.semester = name == null ? null : "2026春";
        row.initialPassword = name == null ? null : "secret123";
        return row;
    }

    @lombok.Data
    public static class NumericWriteRow {
        @com.alibaba.excel.annotation.ExcelProperty(index = 0)
        public Long studentId;
        @com.alibaba.excel.annotation.ExcelProperty(index = 1)
        public String studentName;
        @com.alibaba.excel.annotation.ExcelProperty(index = 2)
        public String major;
        @com.alibaba.excel.annotation.ExcelProperty(index = 3)
        public String className;
        @com.alibaba.excel.annotation.ExcelProperty(index = 4)
        public String semester;
        @com.alibaba.excel.annotation.ExcelProperty(index = 5)
        public String initialPassword;
    }
}
