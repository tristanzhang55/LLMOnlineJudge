package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.dto.SubmissionDraftRequest;
import com.tfzhang.linuxback.service.SubmissionDraftService;
import com.tfzhang.linuxback.vo.SubmissionDraftVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionDraftService submissionDraftService;

    @PutMapping("/draft")
    public SubmissionDraftVO saveDraft(@Valid @RequestBody SubmissionDraftRequest request) {
        return submissionDraftService.saveDraft(request.getExperimentQuestionId(), request.getAnswerBlocks());
    }
}
