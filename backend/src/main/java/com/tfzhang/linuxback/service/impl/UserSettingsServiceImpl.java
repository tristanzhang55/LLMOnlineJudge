package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.SettingsException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.dto.ChangePasswordRequest;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.service.UserSettingsService;
import com.tfzhang.linuxback.vo.UserEnrollmentVO;
import com.tfzhang.linuxback.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    static final String REJECTED_RESET_PASSWORD = "123456";
    static final String REJECTED_SEED_PASSWORD = "password123";

    private final UserMapper userMapper;
    private final ClassStudentMapper classStudentMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileVO getProfile() {
        User user = loadCaller();
        UserProfileVO vo = toProfile(user);
        if ("student".equals(user.getUserType())) {
            List<UserEnrollmentVO> enrollments = classStudentMapper.findStudentEnrollments(user.getId());
            vo.setEnrollments(enrollments != null ? enrollments : List.of());
        } else {
            vo.setEnrollments(List.of());
        }
        return vo;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        if (newPassword.equals(oldPassword)) {
            throw SettingsException.invalid(Msg.t("error.settings.sameAsOld"), "newPassword");
        }
        if (REJECTED_RESET_PASSWORD.equals(newPassword) || REJECTED_SEED_PASSWORD.equals(newPassword)) {
            throw SettingsException.invalid(Msg.t("error.settings.defaultPassword"), "newPassword");
        }

        User user = loadCaller();
        if (!passwordEncoder.matches(oldPassword, user.getUserPassword())) {
            throw SettingsException.invalid(Msg.t("error.settings.wrongOld"), "oldPassword");
        }

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, user.getId())
                .set(User::getUserPassword, passwordEncoder.encode(newPassword))
                .setSql("tokenVersion = tokenVersion + 1");
        userMapper.update(null, update);
    }

    private User loadCaller() {
        User user = userMapper.selectById(CurrentUser.requireId());
        if (user == null || "ban".equals(user.getUserRole())) {
            throw AuthException.invalidToken();
        }
        return user;
    }

    private static UserProfileVO toProfile(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserType(user.getUserType());
        vo.setUserRole(user.getUserRole());
        vo.setMajor(user.getMajor());
        return vo;
    }
}
