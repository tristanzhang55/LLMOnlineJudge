package com.tfzhang.linuxback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChangePasswordRequest {

    @ToString.Exclude
    @NotBlank(message = "{validation.oldPassword.required}")
    private String oldPassword;

    @ToString.Exclude
    @NotBlank(message = "{validation.newPassword.size}")
    @Size(min = 8, max = 72, message = "{validation.newPassword.size}")
    private String newPassword;
}
