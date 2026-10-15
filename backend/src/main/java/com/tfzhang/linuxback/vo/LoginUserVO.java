package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class LoginUserVO {

    private Long id;
    private String userAccount;
    private String userName;
    private String userType;
    private String userRole;
}
