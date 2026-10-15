package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserProfileVO {

    private Long id;
    private String userAccount;
    private String userName;
    private String userType;
    private String userRole;
    private String major;
    private List<UserEnrollmentVO> enrollments = new ArrayList<>();
}
