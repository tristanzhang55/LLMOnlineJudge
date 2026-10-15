package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.SettingsException;
import com.tfzhang.linuxback.dto.ChangePasswordRequest;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.UserEnrollmentVO;
import com.tfzhang.linuxback.vo.UserProfileVO;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceImplTest {

    private static final long STUDENT_ID = 12L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserSettingsServiceImpl service;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        TestAuth.setStudent(STUDENT_ID);
        service = new UserSettingsServiceImpl(userMapper, classStudentMapper, passwordEncoder);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void profile_studentReturnsMajorAndEnrollments() {
        User student = student();
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student);
        UserEnrollmentVO row = enrollment(7L, "22计科3班", "2026春");
        when(classStudentMapper.findStudentEnrollments(STUDENT_ID)).thenReturn(List.of(row));

        UserProfileVO vo = service.getProfile();
        assertEquals(STUDENT_ID, vo.getId());
        assertEquals("2021001234", vo.getUserAccount());
        assertEquals("张伟", vo.getUserName());
        assertEquals("student", vo.getUserType());
        assertEquals("user", vo.getUserRole());
        assertEquals("软件工程", vo.getMajor());
        assertEquals(1, vo.getEnrollments().size());
        assertEquals(7L, vo.getEnrollments().getFirst().getClassId());
        assertEquals("22计科3班", vo.getEnrollments().getFirst().getClassName());
    }

    @Test
    void profile_retakeStudentReturnsTwoRows() {
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(classStudentMapper.findStudentEnrollments(STUDENT_ID)).thenReturn(List.of(
                enrollment(7L, "22计科3班", "2026春"),
                enrollment(9L, "重修班", "2026秋")));

        UserProfileVO vo = service.getProfile();
        assertEquals(2, vo.getEnrollments().size());
        assertEquals(7L, vo.getEnrollments().get(0).getClassId());
        assertEquals(9L, vo.getEnrollments().get(1).getClassId());
    }

    @Test
    void findStudentEnrollments_ordersByIdAscAndHidesDeleted() throws Exception {
        Select select = ClassStudentMapper.class
                .getMethod("findStudentEnrollments", long.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());
        int orderBy = sql.indexOf("ORDER BY");
        assertTrue(orderBy >= 0);
        String order = sql.substring(orderBy);
        assertTrue(order.contains("cs.id ASC"));
        assertFalse(order.contains("joinTime"));
        assertTrue(sql.contains("cs.isDelete = 0"));
        assertTrue(sql.contains("c.isDelete = 0"));
        assertTrue(sql.contains("u.isDelete = 0"));
    }

    @Test
    void profile_studentWithNoEnrollments_returnsEmptyList() {
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(classStudentMapper.findStudentEnrollments(STUDENT_ID)).thenReturn(List.of());

        UserProfileVO vo = service.getProfile();
        assertEquals(List.of(), vo.getEnrollments());
    }

    @Test
    void profile_nonStudentSkipsEnrollmentQuery() {
        TestAuth.setTeacher(STUDENT_ID);
        User teacher = student();
        teacher.setUserType("teacher");
        teacher.setMajor(null);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(teacher);

        UserProfileVO vo = service.getProfile();
        assertEquals(List.of(), vo.getEnrollments());
        verify(classStudentMapper, never()).findStudentEnrollments(STUDENT_ID);
    }

    @Test
    void profile_deletedCaller_isUnauthorized() {
        when(userMapper.selectById(STUDENT_ID)).thenReturn(null);
        AuthException ex = assertThrows(AuthException.class, () -> service.getProfile());
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("未登录或登录已过期", ex.getMessage());
        assertFalse(ex.getMessage().toLowerCase().contains("ban"));
    }

    @Test
    void profile_bannedCaller_isUnauthorized() {
        User banned = student();
        banned.setUserRole("ban");
        when(userMapper.selectById(STUDENT_ID)).thenReturn(banned);
        AuthException ex = assertThrows(AuthException.class, () -> service.getProfile());
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("未登录或登录已过期", ex.getMessage());
        assertFalse(ex.getMessage().contains("ban"));
    }

    @Test
    void profileVo_hasNoSecretFields() {
        Set<String> names = Arrays.stream(UserProfileVO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(names.contains("userPassword"));
        assertFalse(names.contains("userAvatar"));
        assertFalse(names.contains("userEmail"));
        assertFalse(names.contains("userProfile"));
        assertFalse(names.contains("createdBy"));
        assertFalse(names.contains("isDelete"));
    }

    @Test
    void changePassword_correctOld_writesOnlyPasswordHash() {
        User student = student();
        student.setUserPassword(passwordEncoder.encode("123456"));
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        service.changePassword(request("123456", "abcdefgh"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("userPassword"));
        assertTrue(sqlSet.contains("tokenVersion = tokenVersion + 1"));
        assertFalse(sqlSet.contains("userName"));
        String hash = captor.getValue().getParamNameValuePairs().values().stream()
                .map(Object::toString)
                .filter(v -> v.startsWith("$2"))
                .findFirst()
                .orElseThrow();
        assertTrue(passwordEncoder.matches("abcdefgh", hash));
        assertFalse(passwordEncoder.matches("123456", hash));
    }

    @Test
    void changePassword_wrongOld_is400Not401() {
        User student = student();
        student.setUserPassword(passwordEncoder.encode("123456"));
        when(userMapper.selectById(STUDENT_ID)).thenReturn(student);

        SettingsException ex = assertThrows(
                SettingsException.class,
                () -> service.changePassword(request("wrongpass", "abcdefgh")));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("oldPassword", ex.getFieldErrors().getFirst().field());
        assertEquals("当前密码不正确", ex.getFieldErrors().getFirst().message());
        assertNotEquals(AuthException.class, ex.getClass());
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void changePassword_equalToOld_is400WithoutWrite() {
        SettingsException ex = assertThrows(
                SettingsException.class,
                () -> service.changePassword(request("abcdefgh", "abcdefgh")));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertEquals("newPassword", ex.getFieldErrors().getFirst().field());
        assertEquals("新密码不能与当前密码相同", ex.getMessage());
        verify(userMapper, never()).selectById(STUDENT_ID);
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void changePassword_knownDefaults_is400WithoutWrite() {
        SettingsException reset = assertThrows(
                SettingsException.class,
                () -> service.changePassword(request("oldpass1", "123456")));
        assertEquals("newPassword", reset.getFieldErrors().getFirst().field());
        assertEquals("新密码不能使用默认密码", reset.getMessage());

        SettingsException seed = assertThrows(
                SettingsException.class,
                () -> service.changePassword(request("oldpass1", "password123")));
        assertEquals("newPassword", seed.getFieldErrors().getFirst().field());
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void changePassword_bothDefault_prefersEqualToOldMessage() {
        SettingsException ex = assertThrows(
                SettingsException.class,
                () -> service.changePassword(request("123456", "123456")));
        assertEquals("新密码不能与当前密码相同", ex.getMessage());
        assertNotEquals("新密码不能使用默认密码", ex.getMessage());
        verify(userMapper, never()).update(isNull(), any());
    }

    @Test
    void changePasswordRequest_toStringOmitsPasswords() {
        ChangePasswordRequest request = request("secret-old", "secret-new");
        String text = request.toString();
        assertFalse(text.contains("secret-old"));
        assertFalse(text.contains("secret-new"));
    }

    @Test
    void noPrincipal_isUnauthorized() {
        TestAuth.clear();
        AuthException profile = assertThrows(AuthException.class, () -> service.getProfile());
        assertEquals("UNAUTHORIZED", profile.getCode());

        AuthException password = assertThrows(
                AuthException.class,
                () -> service.changePassword(request("oldpass1", "abcdefgh")));
        assertEquals("UNAUTHORIZED", password.getCode());
        assertInstanceOf(AuthException.class, password);
    }

    private static User student() {
        User user = new User();
        user.setId(STUDENT_ID);
        user.setUserAccount("2021001234");
        user.setUserName("张伟");
        user.setUserType("student");
        user.setUserRole("user");
        user.setMajor("软件工程");
        return user;
    }

    private static UserEnrollmentVO enrollment(long classId, String className, String semester) {
        UserEnrollmentVO row = new UserEnrollmentVO();
        row.setClassId(classId);
        row.setClassName(className);
        row.setSemester(semester);
        row.setJoinTime(LocalDateTime.of(2026, 9, 15, 10, 3));
        return row;
    }

    private static ChangePasswordRequest request(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
