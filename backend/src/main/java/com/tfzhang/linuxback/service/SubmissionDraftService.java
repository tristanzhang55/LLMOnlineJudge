package com.tfzhang.linuxback.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tfzhang.linuxback.vo.SubmissionDraftVO;

public interface SubmissionDraftService {

    SubmissionDraftVO saveDraft(long experimentQuestionId, JsonNode answerBlocks);

    long persistDraftRow(long experimentQuestionId, long studentId, String answerBlocksJson);
}
