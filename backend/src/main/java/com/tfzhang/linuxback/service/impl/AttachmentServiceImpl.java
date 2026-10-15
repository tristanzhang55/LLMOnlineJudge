package com.tfzhang.linuxback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.AttachmentException;
import com.tfzhang.linuxback.common.AuthException;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.ExperimentException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.entity.Attachment;
import com.tfzhang.linuxback.entity.Submission;
import com.tfzhang.linuxback.mapper.AttachmentMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.AttachmentService;
import com.tfzhang.linuxback.service.AttachmentStorageService;
import com.tfzhang.linuxback.service.MarkdownOwner;
import com.tfzhang.linuxback.vo.AttachmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private static final long UNATTACHED_OWNER_ID = 0L;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private static final Pattern MARKDOWN_IMAGE = Pattern.compile(
            "!\\[[^\\]]*\\]\\(\\s*<?([^)\\s>]+)>?[^)]*\\)");

    private static final Set<String> TEACHER_OWNER_TYPES = Set.of(
            "question",
            "question_answer",
            "sub_question",
            "sub_question_answer"
    );

    private static final Set<String> ALLOWED_OWNER_TYPES = Set.of(
            "question",
            "question_answer",
            "sub_question",
            "sub_question_answer",
            "submission"
    );

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp"
    );

    private final AttachmentMapper attachmentMapper;
    private final AttachmentStorageService storageService;
    private final SubmissionMapper submissionMapper;

    @Override
    public AttachmentVO upload(MultipartFile file, String ownerType) {
        return upload(file, ownerType, null);
    }

    @Override
    public AttachmentVO upload(MultipartFile file, String ownerType, Long ownerId) {
        validate(file, ownerType);

        String userType = CurrentUser.require().userType();
        long resolvedOwnerId = UNATTACHED_OWNER_ID;
        if ("student".equals(userType)) {
            if (!"submission".equals(ownerType)) {
                throw AuthException.forbidden();
            }
            if (ownerId == null || ownerId < 1) {
                throw new AttachmentException(Msg.t("error.attachment.ownerIdRequired"), "INVALID_PARAMETER");
            }
            Submission submission = submissionMapper.selectById(ownerId);
            if (submission == null || submission.getStudentId() == null
                    || submission.getStudentId() != CurrentUser.requireId()) {
                throw ExperimentException.notFound();
            }
            resolvedOwnerId = ownerId;
        } else if ("teacher".equals(userType)) {
            if ("submission".equals(ownerType) || !TEACHER_OWNER_TYPES.contains(ownerType)) {
                throw AuthException.forbidden();
            }
        } else {
            throw AuthException.forbidden();
        }

        String mimeType = file.getContentType();
        String storageKey = storageService.upload(file, ownerType);

        Attachment attachment = new Attachment();
        attachment.setOwnerType(ownerType);
        attachment.setOwnerId(resolvedOwnerId);
        attachment.setStorageKey(storageKey);
        attachment.setMimeType(mimeType);
        attachment.setFileSize(file.getSize());
        attachment.setOrderIndex(0);
        attachment.setUploaderId(CurrentUser.requireId());
        attachmentMapper.insert(attachment);

        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setStorageKey(storageKey);
        vo.setUrl(storageService.resolveUrl(storageKey));
        vo.setMimeType(mimeType);
        vo.setFileSize(file.getSize());
        return vo;
    }

    @Override
    public void attachMarkdownImages(String ownerType, long ownerId, String... markdown) {
        if (markdown == null || markdown.length == 0) {
            return;
        }

        Set<String> storageKeys = extractStorageKeys(markdown);
        if (storageKeys.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<Attachment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(Attachment::getOwnerId, ownerId)
                .in(Attachment::getStorageKey, storageKeys)
                .eq(Attachment::getOwnerId, UNATTACHED_OWNER_ID)
                .eq(Attachment::getOwnerType, ownerType);
        attachmentMapper.update(null, wrapper);
    }

    @Override
    public void reconcileMarkdownImages(List<MarkdownOwner> previousOwners, List<MarkdownOwner> desiredOwners) {
        if (previousOwners == null || previousOwners.isEmpty()) {
            return;
        }

        long questionId = previousOwners.stream()
                .filter(owner -> "question".equals(owner.ownerType()))
                .mapToLong(MarkdownOwner::ownerId)
                .findFirst()
                .orElseThrow();

        Set<Long> subQuestionIds = previousOwners.stream()
                .filter(owner -> "sub_question".equals(owner.ownerType())
                        || "sub_question_answer".equals(owner.ownerType()))
                .map(MarkdownOwner::ownerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        LinkedHashMap<String, MarkdownOwner> canonicalByKey = buildCanonicalOwners(desiredOwners);
        if (canonicalByKey.isEmpty()) {
            softDeleteAggregateOwnedAttachments(questionId, subQuestionIds, Set.of());
            return;
        }

        Set<String> desiredKeys = canonicalByKey.keySet();
        List<Attachment> relevantRows = loadRelevantAttachments(questionId, subQuestionIds, desiredKeys);
        Map<String, Attachment> rowsByKey = relevantRows.stream()
                .collect(Collectors.toMap(Attachment::getStorageKey, Function.identity(), (left, right) -> left));

        for (Map.Entry<String, MarkdownOwner> entry : canonicalByKey.entrySet()) {
            Attachment row = rowsByKey.get(entry.getKey());
            if (row == null) {
                continue;
            }
            MarkdownOwner canonical = entry.getValue();
            if (matchesOwner(row, canonical)) {
                continue;
            }
            if (row.getOwnerId() == UNATTACHED_OWNER_ID
                    && canonical.ownerType().equals(row.getOwnerType())) {
                transferAttachment(row, canonical);
                continue;
            }
            if (isOwnedByAggregate(row, questionId, subQuestionIds)) {
                transferAttachment(row, canonical);
            }
        }

        softDeleteAggregateOwnedAttachments(questionId, subQuestionIds, desiredKeys);
    }

    @Override
    public void softDeleteAggregateAttachments(long questionId, Collection<Long> subQuestionIds) {
        Set<Long> ids = subQuestionIds == null || subQuestionIds.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(subQuestionIds);
        softDeleteAggregateOwnedAttachments(questionId, ids, Set.of());
    }

    private LinkedHashMap<String, MarkdownOwner> buildCanonicalOwners(List<MarkdownOwner> desiredOwners) {
        LinkedHashMap<String, MarkdownOwner> canonicalByKey = new LinkedHashMap<>();
        if (desiredOwners == null) {
            return canonicalByKey;
        }
        for (MarkdownOwner owner : desiredOwners) {
            if (owner.markdown() == null) {
                continue;
            }
            for (String text : owner.markdown()) {
                for (String key : extractStorageKeys(text)) {
                    canonicalByKey.putIfAbsent(key, owner);
                }
            }
        }
        return canonicalByKey;
    }

    private List<Attachment> loadRelevantAttachments(
            long questionId, Set<Long> subQuestionIds, Set<String> desiredKeys) {
        LambdaQueryWrapper<Attachment> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(aggregateWrapper -> {
            aggregateWrapper.eq(Attachment::getOwnerType, "question")
                    .eq(Attachment::getOwnerId, questionId);
            if (!subQuestionIds.isEmpty()) {
                aggregateWrapper.or(subWrapper -> subWrapper
                        .in(Attachment::getOwnerType, List.of("sub_question", "sub_question_answer"))
                        .in(Attachment::getOwnerId, subQuestionIds));
            }
        });
        if (!desiredKeys.isEmpty()) {
            wrapper.or(unattachedWrapper -> unattachedWrapper
                    .eq(Attachment::getOwnerId, UNATTACHED_OWNER_ID)
                    .in(Attachment::getStorageKey, desiredKeys));
        }
        return attachmentMapper.selectList(wrapper);
    }

    private void softDeleteAggregateOwnedAttachments(
            long questionId, Set<Long> subQuestionIds, Set<String> desiredKeys) {
        LambdaQueryWrapper<Attachment> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(aggregateWrapper -> {
            aggregateWrapper.eq(Attachment::getOwnerType, "question")
                    .eq(Attachment::getOwnerId, questionId);
            if (!subQuestionIds.isEmpty()) {
                aggregateWrapper.or(subWrapper -> subWrapper
                        .in(Attachment::getOwnerType, List.of("sub_question", "sub_question_answer"))
                        .in(Attachment::getOwnerId, subQuestionIds));
            }
        });
        if (!desiredKeys.isEmpty()) {
            wrapper.notIn(Attachment::getStorageKey, desiredKeys);
        }
        List<Attachment> toDelete = attachmentMapper.selectList(wrapper);
        for (Attachment attachment : toDelete) {
            attachmentMapper.deleteById(attachment.getId());
        }
    }

    private void transferAttachment(Attachment row, MarkdownOwner canonical) {
        LambdaUpdateWrapper<Attachment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Attachment::getId, row.getId())
                .set(Attachment::getOwnerType, canonical.ownerType())
                .set(Attachment::getOwnerId, canonical.ownerId());
        attachmentMapper.update(null, wrapper);
    }

    private static boolean matchesOwner(Attachment row, MarkdownOwner owner) {
        return Objects.equals(row.getOwnerType(), owner.ownerType())
                && row.getOwnerId() != null
                && row.getOwnerId() == owner.ownerId();
    }

    private static boolean isOwnedByAggregate(Attachment row, long questionId, Set<Long> subQuestionIds) {
        if ("question".equals(row.getOwnerType()) && row.getOwnerId() == questionId) {
            return true;
        }
        return subQuestionIds.contains(row.getOwnerId())
                && ("sub_question".equals(row.getOwnerType())
                || "sub_question_answer".equals(row.getOwnerType()));
    }

    private Set<String> extractStorageKeys(String... markdown) {
        LinkedHashSet<String> storageKeys = new LinkedHashSet<>();
        for (String text : markdown) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher matcher = MARKDOWN_IMAGE.matcher(text);
            while (matcher.find()) {
                storageService.extractStorageKey(matcher.group(1)).ifPresent(storageKeys::add);
            }
        }
        return storageKeys;
    }

    private void validate(MultipartFile file, String ownerType) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentException(Msg.t("error.fileRequired"), "INVALID_PARAMETER");
        }

        if (ownerType == null || ownerType.isBlank() || !ALLOWED_OWNER_TYPES.contains(ownerType)) {
            throw new AttachmentException(Msg.t("error.invalidOwnerType"), "INVALID_PARAMETER");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AttachmentException(Msg.t("error.fileTooLarge"), "FILE_TOO_LARGE");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_TYPES.containsKey(mimeType)) {
            throw new AttachmentException(
                    Msg.t("error.attachment.unsupportedType"), "UNSUPPORTED_FILE_TYPE");
        }
    }
}
