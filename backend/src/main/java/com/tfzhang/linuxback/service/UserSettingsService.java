package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.dto.ChangePasswordRequest;
import com.tfzhang.linuxback.vo.UserProfileVO;

public interface UserSettingsService {

    UserProfileVO getProfile();

    void changePassword(ChangePasswordRequest request);
}
