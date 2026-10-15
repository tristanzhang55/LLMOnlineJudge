package com.tfzhang.linuxback.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RosterResetPasswordResponse {

    private Long classStudentId;
    private boolean reset;
}
