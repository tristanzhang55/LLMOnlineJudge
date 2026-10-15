package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.service.ClassService;
import com.tfzhang.linuxback.vo.ClassVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @GetMapping
    public List<ClassVO> listClasses() {
        return classService.listClasses();
    }
}
