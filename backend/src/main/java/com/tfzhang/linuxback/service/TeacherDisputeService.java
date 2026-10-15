package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.DisputeForwardRequest;
import com.tfzhang.linuxback.dto.DisputeResolveRequest;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.TeacherDisputeForwardResultVO;

public interface TeacherDisputeService {

    DisputeVO resolve(long disputeId, DisputeResolveRequest request);

    TeacherDisputeForwardResultVO forward(DisputeForwardRequest request);
}
