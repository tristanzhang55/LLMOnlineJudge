package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private LoginUserVO user;
}
