package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.QuestionCreateRequest;
import com.tfzhang.linuxback.dto.QuestionUpdateRequest;
import com.tfzhang.linuxback.vo.QuestionVO;
import com.tfzhang.linuxback.vo.SandboxLanguageVO;

import java.util.List;

public interface QuestionService {

    PageResult<QuestionVO> listQuestions(int page, int pageSize, String title);

    List<SandboxLanguageVO> listSandboxLanguages();

    QuestionVO getQuestion(long qid);

    QuestionVO createQuestion(QuestionCreateRequest request);

    QuestionVO updateQuestion(long qid, QuestionUpdateRequest request);

    void deleteQuestion(long qid);
}
