package com.tfzhang.linuxback.controller;

import com.tfzhang.linuxback.service.AttachmentService;
import com.tfzhang.linuxback.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping
    public AttachmentVO upload(@RequestParam("file") MultipartFile file,
                               @RequestParam("ownerType") String ownerType,
                               @RequestParam(value = "ownerId", required = false) Long ownerId) {
        return attachmentService.upload(file, ownerType, ownerId);
    }
}
