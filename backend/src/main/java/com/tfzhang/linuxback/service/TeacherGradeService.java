package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.vo.GradeListResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface TeacherGradeService {

    GradeListResponse list(
            Long classId,
            Long experimentId,
            String semester,
            int page,
            int pageSize,
            String studentName);

    void export(Long classId, Long experimentId, HttpServletResponse response) throws IOException;
}
