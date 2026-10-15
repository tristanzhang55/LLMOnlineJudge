package com.tfzhang.linuxback.service.impl;

import com.tfzhang.linuxback.entity.Dispute;
import com.tfzhang.linuxback.vo.DisputeVO;

final class DisputeStatus {

    private DisputeStatus() {
    }

    static int derive(Dispute d) {
        if (d == null) {
            return 0;
        }
        if (d.getResolveTime() != null) {
            return 2;
        }
        if (d.getWithdrawnTime() != null) {
            return 3;
        }
        return 1;
    }

    static DisputeVO toVo(Dispute d) {
        DisputeVO vo = new DisputeVO();
        vo.setId(d.getId());
        vo.setSubmissionId(d.getSubmissionId());
        vo.setDisputeStatus(derive(d));
        vo.setDisputeReason(d.getDisputeReason());
        vo.setDisputeTime(d.getDisputeTime());
        vo.setManualComment(d.getManualComment());
        vo.setResolveTime(d.getResolveTime());
        vo.setWithdrawnTime(d.getWithdrawnTime());
        return vo;
    }
}
