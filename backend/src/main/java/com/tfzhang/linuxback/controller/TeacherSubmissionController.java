package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.service.TeacherSubmissionService;
import com.tfzhang.linuxback.vo.TeacherDisputeInboxItemVO;
import com.tfzhang.linuxback.vo.TeacherStudentSubmissionsVO;
import com.tfzhang.linuxback.vo.TeacherSubmissionRoundItemVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
@Validated
public class TeacherSubmissionController {

    private final TeacherSubmissionService teacherSubmissionService;

    @GetMapping("/disputes")
    public PageResult<TeacherDisputeInboxItemVO> listDisputes(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.classId.min}") Integer classId,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.experimentId.min}") Integer experimentId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.disputeStatus.range}")
            @Max(value = 2, message = "{validation.disputeStatus.range}") Integer disputeStatus) {
        return teacherSubmissionService.listDisputes(
                page, pageSize, studentName, classId, experimentId, disputeStatus);
    }

    @GetMapping("/submissions")
    public PageResult<TeacherSubmissionRoundItemVO> listSubmissions(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.classId.min}") Integer classId) {
        return teacherSubmissionService.listRounds(page, pageSize, studentName, classId);
    }

    @GetMapping("/{experimentId}/students/{studentId}/submissions")
    public TeacherStudentSubmissionsVO getStudentSubmissions(
            @PathVariable @Min(value = 1, message = "{validation.experimentId.min}") long experimentId,
            @PathVariable @Min(value = 1, message = "{validation.studentId.min}") long studentId) {
        return teacherSubmissionService.getStudentSubmissions(experimentId, studentId);
    }
}
