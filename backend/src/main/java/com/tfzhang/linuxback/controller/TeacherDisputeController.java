package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.dto.DisputeForwardRequest;
import com.tfzhang.linuxback.dto.DisputeResolveRequest;
import com.tfzhang.linuxback.service.TeacherDisputeService;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/experiments")
@RequiredArgsConstructor
@Validated
public class TeacherDisputeController {

    private final TeacherDisputeService teacherDisputeService;

    @PostMapping("/disputes/{disputeId}/resolve")
    public DisputeVO resolve(
            @PathVariable @Min(value = 1, message = "{validation.disputeId.min}") long disputeId,
            @Valid @RequestBody DisputeResolveRequest request) {
        return teacherDisputeService.resolve(disputeId, request);
    }

    @PostMapping("/disputes/forward")
    public TeacherDisputeForwardResultVO forward(@Valid @RequestBody DisputeForwardRequest request) {
        return teacherDisputeService.forward(request);
    }
}
