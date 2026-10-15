package com.tfzhang.linuxback.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RosterPreviewResponse {

    private List<RosterPreviewRowVO> rows = new ArrayList<>();
}
