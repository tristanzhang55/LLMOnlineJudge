package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.service.StudentSubmissionHistoryService;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryItemVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student/submissions")
@RequiredArgsConstructor
@Validated
public class StudentSubmissionHistoryController {

    private final StudentSubmissionHistoryService studentSubmissionHistoryService;

    @GetMapping
    public PageResult<StudentSubmissionHistoryItemVO> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize) {
        return studentSubmissionHistoryService.listRounds(page, pageSize);
    }
}
