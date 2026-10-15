package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.dto.ExperimentCreateRequest;
import com.tfzhang.linuxback.dto.ExperimentStatusUpdateRequest;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.vo.ExperimentVO;

public interface ExperimentService {

    PageResult<ExperimentVO> listExperiments(int page, int pageSize, String title, Integer classId, Integer status);

    ExperimentVO createExperiment(ExperimentCreateRequest request);

    ExperimentVO getExperiment(long id);

    Experiment requireOwnedExperiment(long id);

    ExperimentVO updateExperiment(long id, ExperimentCreateRequest request);

    void deleteExperiment(long id);

    ExperimentVO updateExperimentStatus(long id, ExperimentStatusUpdateRequest request);

    void expirePublishedPastDeadlines();

    void persistExpiredPublished(Long experimentId, Long userId);
}
