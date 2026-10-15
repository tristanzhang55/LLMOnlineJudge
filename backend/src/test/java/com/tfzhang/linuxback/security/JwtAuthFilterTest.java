package com.tfzhang.linuxback.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    @Test
    void isStudentOnlyPath_matchesStudentExperiments() {
        assertTrue(JwtAuthFilter.isStudentOnlyPath("/student/experiments"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("/student/experiments/5/submit"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("GET", "/student/experiments/5/my-scores"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("GET", "/student/experiments/5/my-scores"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("POST", "/student/experiments/5/submissions/900/disputes"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/student/experiments/5/submissions/900/disputes"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("/student/submissions"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("/student/submissions"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("GET", "/student/disputes"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("GET", "/student/disputes"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("GET", "/student/dashboard/summary"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("GET", "/student/dashboard/summary"));
        MockHttpServletRequest dashboard = new MockHttpServletRequest("GET", "/student/dashboard/summary");
        dashboard.setServletPath("/student/dashboard/summary");
        assertFalse(JwtAuthFilter.isPublicRequest(dashboard));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("POST", "/student/disputes/501/withdraw"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/student/disputes/501/withdraw"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("/experiments"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("/submissions/draft"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("PUT", "/submissions/draft"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("POST", "/experiments/5/submit"));
        assertTrue(JwtAuthFilter.isStudentOnlyPath("GET", "/experiments/5/my-submission-state"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/experiments/5/submit"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("GET", "/experiments/5/my-submission-state"));
    }

    @Test
    void isTeacherOnlyPath_stillMatchesTeacherModules() {
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("/experiments/5"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/experiments/5"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/experiments/submissions"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/experiments/submissions"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/experiments/disputes"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/experiments/disputes"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/experiments/disputes/501/resolve"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/experiments/disputes/501/resolve"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/experiments/disputes/forward"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/experiments/disputes/forward"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/experiments/18/students/4021/submissions"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/experiments/18/students/4021/submissions"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/questions/sandbox-languages"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/questions/sandbox-languages"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("/student/experiments"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("/attachments"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/attachments"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/teacher/students/import-template"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/teacher/students/import-template"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/teacher/students/import/preview"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/teacher/students/import/preview"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/teacher/students/import/confirm"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/teacher/students/import/confirm"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/teacher/students"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/teacher/students"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/teacher/students"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/teacher/students"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("DELETE", "/teacher/students/4102"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("DELETE", "/teacher/students/4102"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("POST", "/teacher/students/4102/reset-password"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/teacher/students/4102/reset-password"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("PUT", "/teacher/students/4102"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("PUT", "/teacher/students/4102"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/teacher/grades"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/teacher/grades"));
        assertTrue(JwtAuthFilter.isTeacherOnlyPath("GET", "/teacher/grades/export"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/teacher/grades/export"));
    }

    @Test
    void userSettingsPaths_areAuthenticatedAnyRole_notPublic() {
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("GET", "/user/profile"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("GET", "/user/profile"));
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/user/password"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/user/password"));

        MockHttpServletRequest profile = new MockHttpServletRequest("GET", "/user/profile");
        profile.setServletPath("/user/profile");
        assertFalse(JwtAuthFilter.isPublicRequest(profile));

        MockHttpServletRequest password = new MockHttpServletRequest("POST", "/user/password");
        password.setServletPath("/user/password");
        assertFalse(JwtAuthFilter.isPublicRequest(password));
    }

    @Test
    void logout_isAuthenticatedAnyRole_notPublic() {
        assertFalse(JwtAuthFilter.isTeacherOnlyPath("POST", "/auth/logout"));
        assertFalse(JwtAuthFilter.isStudentOnlyPath("POST", "/auth/logout"));

        MockHttpServletRequest logout = new MockHttpServletRequest("POST", "/auth/logout");
        logout.setServletPath("/auth/logout");
        assertFalse(JwtAuthFilter.isPublicRequest(logout));
    }
}
