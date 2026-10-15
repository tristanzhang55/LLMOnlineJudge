package com.tfzhang.linuxback.security;

import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterLiveUserTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userMapper);
    }

    @Test
    void acceptLiveUser_matchingRow_isTrue() {
        when(userMapper.selectById(1L)).thenReturn(activeTeacher(0));
        assertTrue(filter.acceptLiveUser(new JwtPrincipal(1L, "teacher", "user", 0)));
    }

    @Test
    void acceptLiveUser_missingRow_isFalse() {
        when(userMapper.selectById(1L)).thenReturn(null);
        assertFalse(filter.acceptLiveUser(new JwtPrincipal(1L, "teacher", "user", 0)));
    }

    @Test
    void acceptLiveUser_banned_isFalse() {
        User banned = activeTeacher(0);
        banned.setUserRole("ban");
        when(userMapper.selectById(1L)).thenReturn(banned);
        assertFalse(filter.acceptLiveUser(new JwtPrincipal(1L, "teacher", "user", 0)));
    }

    @Test
    void acceptLiveUser_userTypeMismatch_isFalse() {
        when(userMapper.selectById(1L)).thenReturn(activeTeacher(0));
        assertFalse(filter.acceptLiveUser(new JwtPrincipal(1L, "student", "user", 0)));
    }

    @Test
    void acceptLiveUser_tokenVersionMismatch_isFalse() {
        when(userMapper.selectById(1L)).thenReturn(activeTeacher(1));
        assertFalse(filter.acceptLiveUser(new JwtPrincipal(1L, "teacher", "user", 0)));
    }

    @Test
    void doFilter_staleTokenVersion_writesUnauthorized() throws Exception {
        when(jwtService.parse("stale")).thenReturn(new JwtPrincipal(1L, "teacher", "user", 0));
        when(userMapper.selectById(1L)).thenReturn(activeTeacher(1));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/classes");
        request.setServletPath("/classes");
        request.addHeader("Authorization", "Bearer stale");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(401, response.getStatus());
        String body = response.getContentAsString();
        assertTrue(body.contains("未登录或登录已过期"));
        assertFalse(body.toLowerCase().contains("ban"));
        verifyNoInteractions(filterChain);
        verify(userMapper).selectById(1L);
    }

    private static User activeTeacher(int tokenVersion) {
        User user = new User();
        user.setId(1L);
        user.setUserType("teacher");
        user.setUserRole("user");
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
