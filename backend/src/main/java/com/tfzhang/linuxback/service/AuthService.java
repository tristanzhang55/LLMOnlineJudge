package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.LoginRequest;
import com.tfzhang.linuxback.vo.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout();
}
