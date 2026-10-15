package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.ExperimentCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentStatusUpdateRequest;
import com.tfzhang.linuxback.service.ExperimentService;
import com.tfzhang.linuxback.vo.ExperimentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
@Validated
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping
    public PageResult<ExperimentVO> listExperiments(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @Min(value = 1, message = "{validation.classId.min}") Integer classId,
            @RequestParam(required = false) @Min(value = 0, message = "{validation.status.range}")
            @Max(value = 2, message = "{validation.status.range}") Integer status) {
        return experimentService.listExperiments(page, pageSize, title, classId, status);
    }

    @PostMapping
    public ExperimentVO createExperiment(@Valid @RequestBody ExperimentCreateRequest request) {
        return experimentService.createExperiment(request);
    }

    @GetMapping("/{id}")
    public ExperimentVO getExperiment(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id) {
        return experimentService.getExperiment(id);
    }

    @PutMapping("/{id}")
    public ExperimentVO updateExperiment(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id,
            @Valid @RequestBody ExperimentCreateRequest request) {
        return experimentService.updateExperiment(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExperiment(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id) {
        experimentService.deleteExperiment(id);
    }

    @PatchMapping("/{id}/status")
    public ExperimentVO updateExperimentStatus(
            @PathVariable @Min(value = 1, message = "{validation.id.min}") long id,
            @Valid @RequestBody ExperimentStatusUpdateRequest request) {
        return experimentService.updateExperimentStatus(id, request);
    }
}
