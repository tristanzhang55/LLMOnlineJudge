package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.RosterConfirmRequest;
import com.tfzhang.linuxback.dto.RosterCreateRequest;
import com.tfzhang.linuxback.dto.RosterUpdateRequest;
import com.tfzhang.linuxback.service.TeacherRosterService;
import com.tfzhang.linuxback.vo.RosterConfirmResponse;
import com.tfzhang.linuxback.vo.RosterPreviewResponse;
import com.tfzhang.linuxback.vo.RosterResetPasswordResponse;
import com.tfzhang.linuxback.vo.RosterRowResultVO;
import com.tfzhang.linuxback.vo.RosterStudentRowVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/teacher/students")
@RequiredArgsConstructor
@Validated
public class TeacherStudentController {

    private final TeacherRosterService teacherRosterService;

    @GetMapping
    public PageResult<RosterStudentRowVO> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.classId.min}") Long classId,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String studentName) {
        return teacherRosterService.list(page, pageSize, classId, semester, studentName);
    }

    @GetMapping("/import-template")
    public void importTemplate(HttpServletResponse response) throws IOException {
        teacherRosterService.writeTemplate(response);
    }

    @PostMapping("/import/preview")
    public RosterPreviewResponse preview(@RequestParam("file") MultipartFile file) {
        return teacherRosterService.preview(file);
    }

    @PostMapping("/import/confirm")
    public RosterConfirmResponse confirm(@Valid @RequestBody RosterConfirmRequest request) {
        return teacherRosterService.confirm(request);
    }

    @PostMapping
    public RosterRowResultVO create(@RequestBody RosterCreateRequest request) {
        return teacherRosterService.create(request);
    }

    @PutMapping("/{classStudentId}")
    public RosterStudentRowVO update(
            @PathVariable @Min(value = 1, message = "{validation.classStudentId.min}") long classStudentId,
            @Valid @RequestBody RosterUpdateRequest request) {
        return teacherRosterService.updateEnrollment(classStudentId, request);
    }

    @DeleteMapping("/{classStudentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEnrollment(
            @PathVariable @Min(value = 1, message = "{validation.classStudentId.min}") long classStudentId) {
        teacherRosterService.deleteEnrollment(classStudentId);
    }

    @PostMapping("/{classStudentId}/reset-password")
    public RosterResetPasswordResponse resetPassword(
            @PathVariable @Min(value = 1, message = "{validation.classStudentId.min}") long classStudentId) {
        return teacherRosterService.resetPassword(classStudentId);
    }
}
