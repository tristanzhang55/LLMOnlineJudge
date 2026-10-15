package com.tfzhang.linuxback.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmSubmitItemVO {

    private Long experimentQuestionId;
    private Long submissionId;
}
