package com.tfzhang.linuxback.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "{validation.notBlank}")
    private String userAccount;

    @NotBlank(message = "{validation.notBlank}")
    private String userPassword;
}
