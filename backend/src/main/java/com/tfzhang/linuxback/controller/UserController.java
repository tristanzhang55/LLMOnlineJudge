package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.dto.ChangePasswordRequest;
import com.tfzhang.linuxback.service.UserSettingsService;
import com.tfzhang.linuxback.vo.UserProfileVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserSettingsService userSettingsService;

    @GetMapping("/profile")
    public UserProfileVO profile() {
        return userSettingsService.getProfile();
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userSettingsService.changePassword(request);
    }
}
