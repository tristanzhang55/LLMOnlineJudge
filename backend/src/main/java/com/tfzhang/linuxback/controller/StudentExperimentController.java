package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.DisputeCreateRequest;
import com.tfzhang.linuxback.service.StudentExperimentService;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.MyExperimentScoresVO;
import com.tfzhang.linuxback.vo.StudentExperimentDetailVO;
import com.tfzhang.linuxback.vo.StudentExperimentListItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student/experiments")
@RequiredArgsConstructor
@Validated
public class StudentExperimentController {

    private final StudentExperimentService studentExperimentService;

    @GetMapping
    public PageResult<StudentExperimentListItem> listExperiments(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize) {
        return studentExperimentService.listExperiments(page, pageSize);
    }

    @GetMapping("/{id}")
    public StudentExperimentDetailVO getExperiment(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id) {
        return studentExperimentService.getExperiment(id);
    }

    @GetMapping("/{id}/my-scores")
    public MyExperimentScoresVO getMyScores(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id) {
        return studentExperimentService.getMyScores(id);
    }

    @PostMapping("/{experimentId}/submissions/{submissionId}/disputes")
    public DisputeVO fileDispute(
            @PathVariable @Min(value = 1, message = "{validation.experimentId.min}") long experimentId,
            @PathVariable @Min(value = 1, message = "{validation.submissionId.min}") long submissionId,
            @Valid @RequestBody DisputeCreateRequest request) {
        return studentExperimentService.fileDispute(experimentId, submissionId, request);
    }
}
