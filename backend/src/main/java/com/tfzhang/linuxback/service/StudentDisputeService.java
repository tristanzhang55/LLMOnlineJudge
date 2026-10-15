package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.vo.DisputeVO;
import com.tfzhang.linuxback.vo.StudentDisputeListItemVO;

public interface StudentDisputeService {

    PageResult<StudentDisputeListItemVO> listDisputes(int page, int pageSize);

    DisputeVO withdraw(long disputeId);
}
