package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.dto.LoginRequest;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.security.JwtService;
import com.tfzhang.linuxback.security.LoginLockout;
import com.tfzhang.linuxback.support.MybatisPlusLambda;
import com.tfzhang.linuxback.support.TestAuth;
import com.tfzhang.linuxback.vo.LoginResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;
    private LoginLockout loginLockout;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private User teacher;

    @BeforeAll
    static void initMp() {
        MybatisPlusLambda.initCommonEntities();
    }

    @BeforeEach
    void setUp() {
        loginLockout = new LoginLockout();
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtService, loginLockout);
        teacher = activeUser(1L, "teacher01", "teacher", "user");
        teacher.setUserPassword(passwordEncoder.encode("password123"));
        teacher.setTokenVersion(0);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void login_correctPassword_returnsTokenAndUserWithoutPassword() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);
        when(jwtService.createToken(teacher)).thenReturn("jwt-token");

        LoginRequest request = new LoginRequest();
        request.setUserAccount("teacher01");
        request.setUserPassword("password123");

        LoginResponse response = authService.login(request);

        assertEquals("jwt-token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(1L, response.getUser().getId());
        assertEquals("teacher01", response.getUser().getUserAccount());
        assertEquals("teacher", response.getUser().getUserType());
        assertEquals("user", response.getUser().getUserRole());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);

        LoginRequest request = new LoginRequest();
        request.setUserAccount("teacher01");
        request.setUserPassword("wrong");

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("账号或密码错误", ex.getMessage());
    }

    @Test
    void login_unknownAccount_throwsSameMessageAsWrongPassword() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUserAccount("nobody");
        request.setUserPassword("password123");

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("账号或密码错误", ex.getMessage());
    }

    @Test
    void login_bannedUser_throwsSameMessageAsWrongPassword() {
        User banned = activeUser(9L, "banned01", "teacher", "ban");
        banned.setUserPassword(passwordEncoder.encode("password123"));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(banned);

        LoginRequest request = new LoginRequest();
        request.setUserAccount("banned01");
        request.setUserPassword("password123");

        AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("UNAUTHORIZED", ex.getCode());
        assertEquals("账号或密码错误", ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().indexOf("ban") < 0);
    }

    @Test
    void login_sixthFailure_isTooManyRequests() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);
        LoginRequest request = wrongPassword("teacher01");

        for (int i = 0; i < LoginLockout.MAX_FAILURES; i++) {
            AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
            assertEquals("UNAUTHORIZED", ex.getCode());
        }

        AuthException locked = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("TOO_MANY_REQUESTS", locked.getCode());
        assertEquals("登录失败次数过多，请稍后再试", locked.getMessage());
    }

    @Test
    void login_sixthFailure_enUs_isEnglishTooManyRequests() {
        LocaleContextHolder.setLocale(Locale.US);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);
        LoginRequest request = wrongPassword("teacher01");

        for (int i = 0; i < LoginLockout.MAX_FAILURES; i++) {
            AuthException ex = assertThrows(AuthException.class, () -> authService.login(request));
            assertEquals("UNAUTHORIZED", ex.getCode());
        }

        AuthException locked = assertThrows(AuthException.class, () -> authService.login(request));
        assertEquals("TOO_MANY_REQUESTS", locked.getCode());
        assertEquals("Too many failed login attempts, please try again later", locked.getMessage());
    }

    @Test
    void login_unknownAccount_countsTowardLockout() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        LoginRequest request = wrongPassword("ghost");

        for (int i = 0; i < LoginLockout.MAX_FAILURES; i++) {
            assertEquals("UNAUTHORIZED",
                    assertThrows(AuthException.class, () -> authService.login(request)).getCode());
        }
        assertEquals("TOO_MANY_REQUESTS",
                assertThrows(AuthException.class, () -> authService.login(request)).getCode());
    }

    @Test
    void login_success_clearsFailures() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);
        when(jwtService.createToken(teacher)).thenReturn("jwt-token");

        LoginRequest wrong = wrongPassword("teacher01");
        assertThrows(AuthException.class, () -> authService.login(wrong));

        LoginRequest ok = new LoginRequest();
        ok.setUserAccount("teacher01");
        ok.setUserPassword("password123");
        authService.login(ok);

        AuthException next = assertThrows(AuthException.class, () -> authService.login(wrong));
        assertEquals("UNAUTHORIZED", next.getCode());
    }

    @Test
    void login_whenLocked_doesNotCallPasswordEncoder() {
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        AuthServiceImpl lockedService =
                new AuthServiceImpl(userMapper, encoder, jwtService, loginLockout);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(teacher);
        when(encoder.matches(any(), any())).thenReturn(false);

        LoginRequest request = wrongPassword("teacher01");
        for (int i = 0; i < LoginLockout.MAX_FAILURES; i++) {
            assertThrows(AuthException.class, () -> lockedService.login(request));
        }
        verify(encoder, times(LoginLockout.MAX_FAILURES)).matches(any(), any());

        AuthException locked = assertThrows(AuthException.class, () -> lockedService.login(request));
        assertEquals("TOO_MANY_REQUESTS", locked.getCode());
        verify(encoder, times(LoginLockout.MAX_FAILURES)).matches(any(), any());
        verify(userMapper, times(LoginLockout.MAX_FAILURES)).selectOne(any());
    }

    @Test
    void logout_bumpsOnlyTokenVersion() {
        TestAuth.setTeacher(1L);
        when(userMapper.update(isNull(), any())).thenReturn(1);

        authService.logout();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertTrue(sqlSet.contains("tokenVersion = tokenVersion + 1"));
        assertTrue(!sqlSet.contains("userPassword"));
    }

    private static LoginRequest wrongPassword(String account) {
        LoginRequest request = new LoginRequest();
        request.setUserAccount(account);
        request.setUserPassword("wrong");
        return request;
    }

    private static User activeUser(long id, String account, String userType, String userRole) {
        User user = new User();
        user.setId(id);
        user.setUserAccount(account);
        user.setUserName("Test User");
        user.setUserType(userType);
        user.setUserRole(userRole);
        user.setIsDelete(0);
        return user;
    }
}
