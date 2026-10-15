package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterConfirmResponse {

    private List<RosterRowResultVO> results = new ArrayList<>();
}
