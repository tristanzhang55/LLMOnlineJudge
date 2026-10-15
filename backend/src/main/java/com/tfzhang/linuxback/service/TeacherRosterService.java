package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.RosterConfirmRequest;
import com.tfzhang.linuxback.dto.RosterCreateRequest;
import com.tfzhang.linuxback.dto.RosterUpdateRequest;
import com.tfzhang.linuxback.vo.RosterConfirmResponse;
import com.tfzhang.linuxback.vo.RosterPreviewResponse;
import com.tfzhang.linuxback.vo.RosterResetPasswordResponse;
import com.tfzhang.linuxback.vo.RosterRowResultVO;
import com.tfzhang.linuxback.vo.RosterStudentRowVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface TeacherRosterService {

    void writeTemplate(HttpServletResponse response) throws IOException;

    RosterPreviewResponse preview(MultipartFile file);

    RosterConfirmResponse confirm(RosterConfirmRequest request);

    RosterRowResultVO create(RosterCreateRequest request);

    PageResult<RosterStudentRowVO> list(int page, int pageSize, Long classId, String semester, String studentName);

    void deleteEnrollment(long classStudentId);

    RosterResetPasswordResponse resetPassword(long classStudentId);

    RosterStudentRowVO updateEnrollment(long classStudentId, RosterUpdateRequest request);
}
