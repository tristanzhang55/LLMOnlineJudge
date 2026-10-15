package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.service.StudentExperimentService;
import com.tfzhang.linuxback.service.StudentSubmitApplicationService;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import com.tfzhang.linuxback.vo.MySubmissionStateVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
@Validated
public class StudentSubmitController {

    private final StudentSubmitApplicationService studentSubmitApplicationService;
    private final StudentExperimentService studentExperimentService;

    @PostMapping("/{id}/submit")
    public ConfirmSubmitVO submit(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id,
            @Valid @RequestBody StudentSubmitRequest request) {
        return studentSubmitApplicationService.confirm(id, request);
    }

    @GetMapping("/{id}/my-submission-state")
    public MySubmissionStateVO mySubmissionState(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id) {
        return studentExperimentService.getMySubmissionState(id);
    }
}
