package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.DisputeCreateRequest;
import com.tfzhang.linuxback.dto.StudentSubmitRequest;
import com.tfzhang.linuxback.vo.ConfirmSubmitVO;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.MySubmissionStateVO;
import com.tfzhang.linuxback.vo.StudentExperimentDetailVO;
import com.tfzhang.linuxback.vo.StudentExperimentListItem;
import com.tfzhang.linuxback.vo.MyExperimentScoresVO;

public interface StudentExperimentService {

    PageResult<StudentExperimentListItem> listExperiments(int page, int pageSize);

    StudentExperimentDetailVO getExperiment(long id);

    ConfirmSubmitVO finalizeSubmit(long id, StudentSubmitRequest request);

    MySubmissionStateVO getMySubmissionState(long id);

    MyExperimentScoresVO getMyScores(long id);

    DisputeVO fileDispute(long experimentId, long submissionId, DisputeCreateRequest request);
}
