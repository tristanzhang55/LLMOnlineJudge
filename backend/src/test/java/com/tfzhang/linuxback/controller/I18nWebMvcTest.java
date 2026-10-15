package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.GlobalExceptionHandler;
import com.tfzhang.linuxback.common.i18n.MsgInitializer;
import com.tfzhang.linuxback.config.JwtProperties;
import com.tfzhang.linuxback.config.LocaleConfig;
import com.tfzhang.linuxback.config.LocaleContextFilter;
import com.tfzhang.linuxback.config.SecurityConfig;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.security.JwtAuthFilter;
import com.tfzhang.linuxback.security.JwtService;
import com.tfzhang.linuxback.service.AuthService;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.service.QuestionService;
import com.tfzhang.linuxback.service.TeacherDisputeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AuthController.class,
        QuestionController.class,
        ExperimentController.class,
        TeacherDisputeController.class
})
@Import({
        LocaleConfig.class,
        LocaleContextFilter.class,
        SecurityConfig.class,
        JwtAuthFilter.class,
        JwtService.class,
        MsgInitializer.class,
        GlobalExceptionHandler.class
})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-at-least-32-bytes-long!!")
class I18nWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private QuestionService questionService;

    @MockitoBean
    private ExperimentService experimentService;

    @MockitoBean
    private TeacherDisputeService teacherDisputeService;

    @BeforeEach
    void stubLiveUsers() {
        when(userMapper.selectById(1L)).thenReturn(user(1L, "teacher"));
        when(userMapper.selectById(2L)).thenReturn(user(2L, "student"));
    }

    @Test
    void disputeAlreadyHandled_noHeader_isZh() throws Exception {
        when(teacherDisputeService.resolve(anyLong(), any()))
                .thenAnswer(inv -> {
                    throw ExperimentException.disputeAlreadyHandled();
                });

        mockMvc.perform(post("/experiments/disputes/1/resolve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manualComment\":\"ok\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("该申诉已被处理"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("disputeStatus"));
    }

    @Test
    void disputeAlreadyHandled_enUs_isEnglishWithSameCodeAndField() throws Exception {
        when(teacherDisputeService.resolve(anyLong(), any()))
                .thenAnswer(inv -> {
                    throw ExperimentException.disputeAlreadyHandled();
                });

        mockMvc.perform(post("/experiments/disputes/1/resolve")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manualComment\":\"ok\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("This dispute has already been handled"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("disputeStatus"));
    }

    @Test
    void beanValidation_titleRequired_enUs() throws Exception {
        mockMvc.perform(post("/questions")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","content":"c","type":"qa","difficulty":"easy",
                                "subQuestions":[{"content":"x","answerFormat":"text","score":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("title"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("title is required"));
    }

    @Test
    void requestParamValidation_pageZero_enUs() throws Exception {
        mockMvc.perform(get("/questions")
                        .param("page", "0")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teacherToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be >= 1"));
    }

    @Test
    void filterChain_unauthenticated_enUs() throws Exception {
        mockMvc.perform(get("/questions")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Not signed in or session expired"));
    }

    @Test
    void filterChain_studentOnTeacherPath_enUs() throws Exception {
        mockMvc.perform(get("/questions")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentToken()))
                        .with(request -> {
                            request.setServletPath("/questions");
                            return request;
                        }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void login_badCredentials_enUs() throws Exception {
        when(authService.login(any())).thenAnswer(inv -> {
            throw AuthException.badCredentials();
        });

        mockMvc.perform(post("/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccount\":\"a\",\"userPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Incorrect account or password"));
    }

    @Test
    void login_tooManyRequests_enUs() throws Exception {
        when(authService.login(any())).thenAnswer(inv -> {
            throw AuthException.tooManyRequests();
        });

        mockMvc.perform(post("/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAccount\":\"a\",\"userPassword\":\"b\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value(
                        "Too many failed login attempts, please try again later"));
    }

    private String teacherToken() {
        return jwtService.createToken(user(1L, "teacher"));
    }

    private String studentToken() {
        return jwtService.createToken(user(2L, "student"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static User user(long id, String userType) {
        User user = new User();
        user.setId(id);
        user.setUserType(userType);
        user.setUserRole("user");
        user.setTokenVersion(0);
        return user;
    }
}
