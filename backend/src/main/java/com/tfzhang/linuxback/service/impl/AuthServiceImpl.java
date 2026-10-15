package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.dto.LoginRequest;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.security.JwtService;
import com.tfzhang.linuxback.security.LoginLockout;
import com.tfzhang.linuxback.service.AuthService;
import com.tfzhang.linuxback.vo.LoginResponse;
import com.tfzhang.linuxback.vo.LoginUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginLockout loginLockout;

    @Override
    public LoginResponse login(LoginRequest request) {
        String account = request.getUserAccount();
        if (loginLockout.isLocked(account)) {
            throw AuthException.tooManyRequests();
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUserAccount, account));
        if (user == null || "ban".equals(user.getUserRole())) {
            loginLockout.recordFailure(account);
            throw AuthException.badCredentials();
        }
        if (!passwordEncoder.matches(request.getUserPassword(), user.getUserPassword())) {
            loginLockout.recordFailure(account);
            throw AuthException.badCredentials();
        }

        loginLockout.clear(account);
        LoginResponse response = new LoginResponse();
        response.setToken(jwtService.createToken(user));
        response.setUser(toLoginUserVO(user));
        return response;
    }

    @Override
    public void logout() {
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, CurrentUser.requireId())
                .setSql("tokenVersion = tokenVersion + 1");
        userMapper.update(null, update);
    }

    private LoginUserVO toLoginUserVO(User user) {
        LoginUserVO vo = new LoginUserVO();
        vo.setId(user.getId());
        vo.setUserAccount(user.getUserAccount());
        vo.setUserName(user.getUserName());
        vo.setUserType(user.getUserType());
        vo.setUserRole(user.getUserRole());
        return vo;
    }
}
