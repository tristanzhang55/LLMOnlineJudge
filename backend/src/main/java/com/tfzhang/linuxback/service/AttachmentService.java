package com.tfzhang.linuxback.service;

import com.tfzhang.linuxback.vo.AttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;

public interface AttachmentService {

    AttachmentVO upload(MultipartFile file, String ownerType);

    AttachmentVO upload(MultipartFile file, String ownerType, Long ownerId);

    void attachMarkdownImages(String ownerType, long ownerId, String... markdown);

    void reconcileMarkdownImages(List<MarkdownOwner> previousOwners, List<MarkdownOwner> desiredOwners);

    void softDeleteAggregateAttachments(long questionId, Collection<Long> subQuestionIds);
}
