package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.QuestionCreateRequest;
import com.tfzhang.linuxback.dto.QuestionUpdateRequest;
import com.tfzhang.linuxback.service.QuestionService;
import com.tfzhang.linuxback.vo.QuestionVO;
import com.tfzhang.linuxback.vo.SandboxLanguageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Validated
public class QuestionController {

    private final QuestionService questionService;

    @GetMapping("/sandbox-languages")
    public List<SandboxLanguageVO> listSandboxLanguages() {
        return questionService.listSandboxLanguages();
    }

    @GetMapping
    public PageResult<QuestionVO> listQuestions(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "{validation.page.min}") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "{validation.pageSize.range}")
            @Max(value = 100, message = "{validation.pageSize.range}") int pageSize,
            @RequestParam(required = false) String title) {
        return questionService.listQuestions(page, pageSize, title);
    }

    @GetMapping("/{qid}")
    public QuestionVO getQuestion(
            @PathVariable @Min(value = 1, message = "{validation.qid.min}") long qid) {
        return questionService.getQuestion(qid);
    }

    @PostMapping
    public QuestionVO createQuestion(@Valid @RequestBody QuestionCreateRequest request) {
        return questionService.createQuestion(request);
    }

    @PutMapping("/{qid}")
    public QuestionVO updateQuestion(
            @PathVariable @Min(value = 1, message = "{validation.qid.min}") long qid,
            @Valid @RequestBody QuestionUpdateRequest request) {
        return questionService.updateQuestion(qid, request);
    }

    @DeleteMapping("/{qid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuestion(
            @PathVariable @Min(value = 1, message = "{validation.qid.min}") long qid) {
        questionService.deleteQuestion(qid);
    }
}
