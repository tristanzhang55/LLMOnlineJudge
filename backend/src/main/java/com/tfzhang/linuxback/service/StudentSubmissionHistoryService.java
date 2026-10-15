package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.vo.StudentSubmissionHistoryItemVO;

public interface StudentSubmissionHistoryService {

    PageResult<StudentSubmissionHistoryItemVO> listRounds(int page, int pageSize);
}
