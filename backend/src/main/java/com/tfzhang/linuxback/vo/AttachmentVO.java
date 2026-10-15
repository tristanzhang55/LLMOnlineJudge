package com.tfzhang.linuxback.vo;

import lombok.Data;

@Data
public class AttachmentVO {

    private Long id;
    private String url;
    private String storageKey;
    private String mimeType;
    private Long fileSize;
}
