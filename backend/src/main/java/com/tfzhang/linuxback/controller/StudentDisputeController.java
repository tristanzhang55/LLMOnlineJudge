package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.service.StudentDisputeService;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.StudentDisputeListItemVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
@Validated
public class StudentDisputeController {

    private final StudentDisputeService studentDisputeService;

    @GetMapping("/disputes")
    public PageResult<StudentDisputeListItemVO> listDisputes(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize) {
        return studentDisputeService.listDisputes(page, pageSize);
    }

    @PostMapping("/disputes/{disputeId}/withdraw")
    public DisputeVO withdraw(
            @PathVariable @Min(value = 1, message = "{validation.disputeId.min}") long disputeId) {
        return studentDisputeService.withdraw(disputeId);
    }
}
