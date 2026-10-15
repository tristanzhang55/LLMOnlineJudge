package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.service.StudentDashboardService;
import com.tfzhang.linuxback.vo.StudentDashboardSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentDashboardController {

    private final StudentDashboardService studentDashboardService;

    @GetMapping("/dashboard/summary")
    public StudentDashboardSummaryVO summary() {
        return studentDashboardService.getSummary();
    }
}
