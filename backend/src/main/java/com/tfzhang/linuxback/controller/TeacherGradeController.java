package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.service.TeacherGradeService;
import com.tfzhang.linuxback.vo.GradeListResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/teacher/grades")
@RequiredArgsConstructor
@Validated
public class TeacherGradeController {

    private final TeacherGradeService teacherGradeService;

    @GetMapping
    public GradeListResponse list(
            @RequestParam @Min(value = 1, message = "{validation.classId.min}") Long classId,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.experimentId.min}") Long experimentId,
            @RequestParam(required = false) String semester,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) String studentName) {
        return teacherGradeService.list(classId, experimentId, semester, page, pageSize, studentName);
    }

    @GetMapping("/export")
    public void export(
            @RequestParam @Min(value = 1, message = "{validation.classId.min}") Long classId,
            @RequestParam @Min(value = 1, message = "{validation.experimentId.min}") Long experimentId,
            HttpServletResponse response) throws IOException {
        teacherGradeService.export(classId, experimentId, response);
    }
}
