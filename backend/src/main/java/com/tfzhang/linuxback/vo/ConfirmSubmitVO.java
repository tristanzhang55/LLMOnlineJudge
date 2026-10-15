package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConfirmSubmitVO {

    private LocalDateTime submitTime;
    private List<ConfirmSubmitItemVO> submissions;
}
