package com.tfzhang.linuxback.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.PageResult;
import com.tfzhang.linuxback.common.RosterException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.dto.RosterConfirmRequest;
import com.tfzhang.linuxback.dto.RosterCreateRequest;
import com.tfzhang.linuxback.dto.RosterRowRequest;
import com.tfzhang.linuxback.dto.RosterUpdateRequest;
import com.tfzhang.linuxback.entity.ClassStudent;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.entity.User;
import com.tfzhang.linuxback.excel.RosterExcelReadListener;
import com.tfzhang.linuxback.excel.RosterExcelRow;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.UserMapper;
import com.tfzhang.linuxback.service.TeacherRosterService;
import com.tfzhang.linuxback.vo.RosterConfirmResponse;
import com.tfzhang.linuxback.vo.RosterPreviewResponse;
import com.tfzhang.linuxback.vo.RosterPreviewRowVO;
import com.tfzhang.linuxback.vo.RosterResetPasswordResponse;
import com.tfzhang.linuxback.vo.RosterRowResultVO;
import com.tfzhang.linuxback.vo.RosterStudentRowVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TeacherRosterServiceImpl implements TeacherRosterService {

    static final String CREATED = "created";
    static final String ENROLLED = "enrolled";
    static final String ALREADY_IMPORTED = "already_imported";
    static final String REJECTED = "rejected";
    static final String VALID = "valid";
    static final String WARNING = "warning";
    static final String ERROR = "error";

    static final String DEFAULT_PASSWORD = "123456";

    private static final Pattern DIGITS = Pattern.compile("^[0-9]+$");
    private static final int MAX_ACCOUNT = 256;
    static final int MAX_NAME = 256;
    static final int MAX_MAJOR = 128;
    private static final int MAX_CLASS_NAME = 255;
    private static final int MAX_SEMESTER = 64;
    private static final int MAX_PASSWORD = 72;

    private final UserMapper userMapper;
    private final ClassMapper classMapper;
    private final ClassStudentMapper classStudentMapper;
    private final PasswordEncoder passwordEncoder;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void writeTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("roster-import-template", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + ".xlsx\"");
        EasyExcel.write(response.getOutputStream(), RosterExcelRow.class)
                .sheet("Roster")
                .doWrite(List.of());
    }

    @Override
    public RosterPreviewResponse preview(MultipartFile file) {
        long teacherId = CurrentUser.requireId();
        List<RosterExcelReadListener.ParsedRosterExcelRow> parsed = parseXlsx(file);
        Map<String, Integer> seen = new LinkedHashMap<>();
        RosterPreviewResponse response = new RosterPreviewResponse();
        for (RosterExcelReadListener.ParsedRosterExcelRow parsedRow : parsed) {
            RosterRowRequest request = toRequest(parsedRow);
            RowValidation validation = validateRow(teacherId, request, seen);
            if (!validation.blocking && validation.duplicateKey != null) {
                seen.put(validation.duplicateKey, request.getRowIndex());
            }
            response.getRows().add(toPreview(request, validation));
        }
        return response;
    }

    @Override
    public RosterConfirmResponse confirm(RosterConfirmRequest request) {
        long teacherId = CurrentUser.requireId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Map<String, Integer> seen = new LinkedHashMap<>();
        RosterConfirmResponse response = new RosterConfirmResponse();
        for (RosterRowRequest row : request.getRows()) {
            normalize(row);
            RowValidation validation = validateRow(teacherId, row, seen);
            if (validation.blocking) {
                response.getResults().add(rejected(row, validation.messages));
                continue;
            }
            if (validation.duplicateKey != null) {
                seen.put(validation.duplicateKey, row.getRowIndex());
            }
            response.getResults().add(tx.execute(status -> persistOne(teacherId, row)));
        }
        return response;
    }

    @Override
    public RosterRowResultVO create(RosterCreateRequest request) {
        long teacherId = CurrentUser.requireId();
        RosterRowRequest row = toRequest(request);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> persistOne(teacherId, row));
    }

    @Override
    public PageResult<RosterStudentRowVO> list(
            int page, int pageSize, Long classId, String semester, String studentName) {
        long teacherId = CurrentUser.requireId();
        String semesterFilter = trimToNull(semester);
        String studentNameLike = toStudentNameLike(studentName);
        long total = classStudentMapper.countTeacherRoster(teacherId, classId, semesterFilter, studentNameLike);
        if (total == 0) {
            return PageResult.of(List.of(), 0);
        }
        int offset = (page - 1) * pageSize;
        List<RosterStudentRowVO> data = classStudentMapper.findTeacherRoster(
                teacherId, classId, semesterFilter, studentNameLike, pageSize, offset);
        return PageResult.of(data, total);
    }

    @Override
    public void deleteEnrollment(long classStudentId) {
        long teacherId = CurrentUser.requireId();
        ClassStudent enrollment = classStudentMapper.findByIdIncludingDeleted(classStudentId);
        if (enrollment == null) {
            throw RosterException.notFound();
        }
        TeachingClass classRow = classMapper.selectById(enrollment.getClassId());
        if (classRow == null || !Objects.equals(classRow.getTeacherId(), teacherId)) {
            throw RosterException.notFound();
        }
        if (enrollment.getIsDelete() != null && enrollment.getIsDelete() != 0) {
            return;
        }
        classStudentMapper.deleteById(enrollment.getId());
    }

    @Override
    public RosterResetPasswordResponse resetPassword(long classStudentId) {
        ActionableEnrollment loaded = loadActionableEnrollment(CurrentUser.requireId(), classStudentId);
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, loaded.user().getId())
                .set(User::getUserPassword, passwordEncoder.encode(DEFAULT_PASSWORD))
                .setSql("tokenVersion = tokenVersion + 1");
        userMapper.update(null, update);
        return new RosterResetPasswordResponse(classStudentId, true);
    }

    @Override
    @Transactional
    public RosterStudentRowVO updateEnrollment(long classStudentId, RosterUpdateRequest request) {
        ActionableEnrollment loaded = loadActionableEnrollment(CurrentUser.requireId(), classStudentId);
        String studentName = trimToNull(request.getStudentName());
        String major = trimToNull(request.getMajor());
        if (isBlank(studentName) || studentName.length() > MAX_NAME) {
            throw RosterException.invalid(Msg.t("error.roster.studentNameBlank"), "studentName");
        }
        if (isBlank(major) || major.length() > MAX_MAJOR) {
            throw RosterException.invalid(Msg.t("error.roster.majorBlank"), "major");
        }

        TeachingClass target = classMapper.selectById(request.getClassId());
        if (target == null || !Objects.equals(target.getTeacherId(), loaded.teacherId())) {
            throw RosterException.notFound();
        }
        if (!Objects.equals(target.getSemester(), loaded.classRow().getSemester())) {
            throw RosterException.invalid(Msg.t("error.roster.sameSemesterOnly"), "classId");
        }

        if (Objects.equals(target.getId(), loaded.enrollment().getClassId())) {
            updateUserNameAndMajor(loaded.user().getId(), studentName, major);
            loaded.user().setUserName(studentName);
            loaded.user().setMajor(major);
            return toRowVO(loaded.enrollment(), target, loaded.user());
        }

        ClassStudent duplicate = findEnrollment(target.getId(), loaded.user().getId());
        if (duplicate != null) {
            throw RosterException.conflict(Msg.t("error.roster.alreadyInClass"), "classId");
        }

        classStudentMapper.deleteById(loaded.enrollment().getId());
        ClassStudent inserted = new ClassStudent();
        inserted.setClassId(target.getId());
        inserted.setStudentId(loaded.user().getId());
        inserted.setJoinTime(LocalDateTime.now());
        try {
            classStudentMapper.insert(inserted);
        } catch (DuplicateKeyException ex) {
            throw RosterException.conflict(Msg.t("error.roster.alreadyInClass"), "classId");
        }
        updateUserNameAndMajor(loaded.user().getId(), studentName, major);
        loaded.user().setUserName(studentName);
        loaded.user().setMajor(major);
        return toRowVO(inserted, target, loaded.user());
    }

    RosterRowResultVO persistOne(long teacherId, RosterRowRequest row) {
        normalize(row);
        RowValidation validation = validateRow(teacherId, row, Map.of());
        if (validation.blocking) {
            return rejected(row, validation.messages);
        }

        User existing = findActiveUser(row.getStudentId());
        User user;
        boolean insertedUser = false;
        List<String> messages = new ArrayList<>(validation.messages);

        if (existing == null) {
            try {
                user = insertStudent(teacherId, row);
                insertedUser = true;
            } catch (DuplicateKeyException ex) {
                user = findActiveUser(row.getStudentId());
                if (user == null) {
                    return rejected(row, List.of(Msg.t("roster.row.accountDeleted", row.getStudentId())));
                }
                RowValidation afterRace = validateExistingAccount(row, user);
                if (afterRace.blocking) {
                    return rejected(row, afterRace.messages);
                }
                messages.addAll(afterRace.messages);
            }
        } else {
            user = existing;
        }

        TeachingClass classRow;
        try {
            classRow = findOrCreateClass(teacherId, row.getClassName(), row.getSemester());
        } catch (ClassConflictException ex) {
            return rejected(row, List.of(Msg.t("roster.row.classConflict")));
        }

        if (findEnrollment(classRow.getId(), user.getId()) != null) {
            return result(row, ALREADY_IMPORTED, user.getId(), classRow.getId(), messages);
        }

        ClassStudent enrollment = new ClassStudent();
        enrollment.setClassId(classRow.getId());
        enrollment.setStudentId(user.getId());
        classStudentMapper.insert(enrollment);

        return result(row, insertedUser ? CREATED : ENROLLED, user.getId(), classRow.getId(), messages);
    }

    TeachingClass findOrCreateClass(long teacherId, String className, String semester) {
        TeachingClass found = findClass(teacherId, className, semester);
        if (found != null) {
            return found;
        }
        TeachingClass created = new TeachingClass();
        created.setTeacherId(teacherId);
        created.setName(className);
        created.setSemester(semester);
        try {
            classMapper.insert(created);
            return created;
        } catch (DuplicateKeyException ex) {
            TeachingClass retried = findClass(teacherId, className, semester);
            if (retried == null) {
                throw new ClassConflictException();
            }
            return retried;
        }
    }

    private List<RosterExcelReadListener.ParsedRosterExcelRow> parseXlsx(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw RosterException.invalidFile(Msg.t("error.roster.fileRequired"));
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw RosterException.invalidFile(Msg.t("error.roster.xlsxOnly"));
        }
        RosterExcelReadListener listener = new RosterExcelReadListener();
        try {
            EasyExcel.read(file.getInputStream(), RosterExcelRow.class, listener)
                    .sheet(0)
                    .headRowNumber(1)
                    .doRead();
        } catch (RosterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw RosterException.invalidFile(Msg.t("error.roster.parseFailed"));
        }
        return listener.getRows();
    }

    private RowValidation validateRow(long teacherId, RosterRowRequest row, Map<String, Integer> seen) {
        normalize(row);
        List<String> messages = new ArrayList<>();
        boolean blocking = false;

        if (isBlank(row.getStudentId())) {
            messages.add(Msg.t("roster.row.studentIdBlank"));
            blocking = true;
        } else if (!DIGITS.matcher(row.getStudentId()).matches() || row.getStudentId().length() > MAX_ACCOUNT) {
            messages.add(Msg.t("roster.row.studentIdDigits"));
            blocking = true;
        }
        if (isBlank(row.getStudentName())) {
            messages.add(Msg.t("roster.row.studentNameBlank"));
            blocking = true;
        } else if (row.getStudentName().length() > MAX_NAME) {
            messages.add(Msg.t("roster.row.studentNameBlank"));
            blocking = true;
        }
        if (isBlank(row.getMajor())) {
            messages.add(Msg.t("roster.row.majorBlank"));
            blocking = true;
        } else if (row.getMajor().length() > MAX_MAJOR) {
            messages.add(Msg.t("roster.row.majorBlank"));
            blocking = true;
        }
        if (isBlank(row.getClassName())) {
            messages.add(Msg.t("roster.row.classNameBlank"));
            blocking = true;
        } else if (row.getClassName().length() > MAX_CLASS_NAME) {
            messages.add(Msg.t("roster.row.classNameBlank"));
            blocking = true;
        }
        if (isBlank(row.getSemester())) {
            messages.add(Msg.t("roster.row.semesterBlank"));
            blocking = true;
        } else if (row.getSemester().length() > MAX_SEMESTER) {
            messages.add(Msg.t("roster.row.semesterBlank"));
            blocking = true;
        }

        boolean studentIdOk = row.getStudentId() != null && DIGITS.matcher(row.getStudentId()).matches()
                && row.getStudentId().length() <= MAX_ACCOUNT;
        User existing = studentIdOk ? findActiveUser(row.getStudentId()) : null;

        if (existing == null && studentIdOk) {
            if (isBlank(row.getInitialPassword())) {
                messages.add(Msg.t("roster.row.initialPasswordRequired"));
                blocking = true;
            } else if (row.getInitialPassword().length() > MAX_PASSWORD) {
                messages.add(Msg.t("roster.row.initialPasswordTooLong"));
                blocking = true;
            }
        }

        String duplicateKey = null;
        if (studentIdOk && !isBlank(row.getClassName()) && !isBlank(row.getSemester())) {
            duplicateKey = row.getStudentId() + "\0" + row.getClassName() + "\0" + row.getSemester();
            Integer earlier = seen.get(duplicateKey);
            if (earlier != null) {
                messages.add(Msg.t("roster.row.duplicateOfRow", earlier));
                blocking = true;
            }
        }

        boolean alreadyImported = false;
        boolean majorWarning = false;
        if (studentIdOk && existing != null) {
            RowValidation existingChecks = validateExistingAccount(row, existing);
            messages.addAll(existingChecks.messages);
            blocking = blocking || existingChecks.blocking;
            majorWarning = existingChecks.majorWarning;
            if (!blocking) {
                alreadyImported = isAlreadyImported(
                        teacherId, existing.getId(), row.getClassName(), row.getSemester());
            }
        }

        return new RowValidation(blocking, alreadyImported, majorWarning, duplicateKey, messages, existing);
    }

    private RowValidation validateExistingAccount(RosterRowRequest row, User existing) {
        List<String> messages = new ArrayList<>();
        boolean blocking = false;
        boolean majorWarning = false;
        if (!"student".equals(existing.getUserType())) {
            messages.add(Msg.t("roster.row.wrongUserType", row.getStudentId()));
            blocking = true;
        }
        if ("ban".equals(existing.getUserRole())) {
            messages.add(Msg.t("roster.row.banned", row.getStudentId()));
            blocking = true;
        }
        String storedName = existing.getUserName();
        if (storedName == null || !storedName.equals(row.getStudentName())) {
            messages.add(Msg.t(
                    "roster.row.nameMismatch",
                    row.getStudentId(),
                    display(storedName),
                    display(row.getStudentName())));
            blocking = true;
        }
        if (!blocking && !majorsMatch(existing.getMajor(), row.getMajor())) {
            messages.add(Msg.t(
                    "roster.row.majorDiffers",
                    row.getStudentId(),
                    display(existing.getMajor())));
            majorWarning = true;
        }
        return new RowValidation(blocking, false, majorWarning, null, messages, existing);
    }

    private boolean isAlreadyImported(long teacherId, long userId, String className, String semester) {
        TeachingClass classRow = findClass(teacherId, className, semester);
        if (classRow == null) {
            return false;
        }
        return findEnrollment(classRow.getId(), userId) != null;
    }

    private User insertStudent(long teacherId, RosterRowRequest row) {
        User user = new User();
        user.setUserAccount(row.getStudentId());
        user.setUserPassword(passwordEncoder.encode(row.getInitialPassword()));
        user.setUserName(row.getStudentName());
        user.setMajor(row.getMajor());
        user.setUserType("student");
        user.setUserRole("user");
        user.setCreatedBy(teacherId);
        userMapper.insert(user);
        return user;
    }

    private User findActiveUser(String studentId) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserAccount, studentId));
    }

    private TeachingClass findClass(long teacherId, String className, String semester) {
        return classMapper.selectOne(new LambdaQueryWrapper<TeachingClass>()
                .eq(TeachingClass::getTeacherId, teacherId)
                .eq(TeachingClass::getName, className)
                .eq(TeachingClass::getSemester, semester));
    }

    private ClassStudent findEnrollment(long classId, long userId) {
        return classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                .eq(ClassStudent::getClassId, classId)
                .eq(ClassStudent::getStudentId, userId));
    }

    private ActionableEnrollment loadActionableEnrollment(long teacherId, long classStudentId) {
        ClassStudent enrollment = classStudentMapper.selectById(classStudentId);
        if (enrollment == null) {
            throw RosterException.notFound();
        }
        TeachingClass classRow = classMapper.selectById(enrollment.getClassId());
        if (classRow == null || !Objects.equals(classRow.getTeacherId(), teacherId)) {
            throw RosterException.notFound();
        }
        User user = userMapper.selectById(enrollment.getStudentId());
        if (user == null) {
            throw RosterException.notFound();
        }
        return new ActionableEnrollment(teacherId, enrollment, classRow, user);
    }

    private void updateUserNameAndMajor(long userId, String studentName, String major) {
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<>();
        update.eq(User::getId, userId)
                .set(User::getUserName, studentName)
                .set(User::getMajor, major);
        userMapper.update(null, update);
    }

    private static RosterStudentRowVO toRowVO(ClassStudent enrollment, TeachingClass classRow, User user) {
        RosterStudentRowVO vo = new RosterStudentRowVO();
        vo.setClassStudentId(enrollment.getId());
        vo.setStudentId(user.getUserAccount());
        vo.setStudentName(user.getUserName());
        vo.setMajor(user.getMajor());
        vo.setClassId(classRow.getId());
        vo.setClassName(classRow.getName());
        vo.setSemester(classRow.getSemester());
        vo.setJoinTime(enrollment.getJoinTime());
        return vo;
    }

    static String toStudentNameLike(String studentName) {
        String trimmed = trimToNull(studentName);
        if (trimmed == null) {
            return null;
        }
        return "%" + escapeLike(trimmed) + "%";
    }

    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private record ActionableEnrollment(
            long teacherId, ClassStudent enrollment, TeachingClass classRow, User user) {
    }

    private RosterPreviewRowVO toPreview(RosterRowRequest request, RowValidation validation) {
        RosterPreviewRowVO vo = new RosterPreviewRowVO();
        vo.setRowIndex(request.getRowIndex());
        vo.setStudentId(request.getStudentId());
        vo.setStudentName(request.getStudentName());
        vo.setMajor(request.getMajor());
        vo.setClassName(request.getClassName());
        vo.setSemester(request.getSemester());
        vo.setMessages(validation.messages);
        if (validation.blocking) {
            vo.setStatus(ERROR);
        } else if (validation.alreadyImported) {
            vo.setStatus(ALREADY_IMPORTED);
        } else if (validation.majorWarning) {
            vo.setStatus(WARNING);
        } else {
            vo.setStatus(VALID);
        }
        vo.setInitialPassword(validation.existing == null ? request.getInitialPassword() : null);
        return vo;
    }

    private RosterRowRequest toRequest(RosterExcelReadListener.ParsedRosterExcelRow parsed) {
        RosterRowRequest request = new RosterRowRequest();
        request.setRowIndex(parsed.rowIndex());
        RosterExcelRow data = parsed.data();
        request.setStudentId(data.getStudentId());
        request.setStudentName(data.getStudentName());
        request.setMajor(data.getMajor());
        request.setClassName(data.getClassName());
        request.setSemester(data.getSemester());
        request.setInitialPassword(data.getInitialPassword());
        normalize(request);
        return request;
    }

    private RosterRowRequest toRequest(RosterCreateRequest create) {
        RosterRowRequest request = new RosterRowRequest();
        request.setRowIndex(1);
        request.setStudentId(create.getStudentId());
        request.setStudentName(create.getStudentName());
        request.setMajor(create.getMajor());
        request.setClassName(create.getClassName());
        request.setSemester(create.getSemester());
        request.setInitialPassword(create.getInitialPassword());
        normalize(request);
        return request;
    }

    private static RosterRowResultVO rejected(RosterRowRequest row, List<String> messages) {
        return result(row, REJECTED, null, null, messages);
    }

    private static RosterRowResultVO result(
            RosterRowRequest row, String result, Long userId, Long classId, List<String> messages) {
        RosterRowResultVO vo = new RosterRowResultVO();
        vo.setRowIndex(row.getRowIndex());
        vo.setStudentId(row.getStudentId());
        vo.setResult(result);
        vo.setUserId(userId);
        vo.setClassId(classId);
        vo.setMessages(messages == null ? new ArrayList<>() : new ArrayList<>(messages));
        return vo;
    }

    private static void normalize(RosterRowRequest row) {
        row.setStudentId(trimToNull(row.getStudentId()));
        row.setStudentName(trimToNull(row.getStudentName()));
        row.setMajor(trimToNull(row.getMajor()));
        row.setClassName(trimToNull(row.getClassName()));
        row.setSemester(trimToNull(row.getSemester()));
        row.setInitialPassword(trimToNull(row.getInitialPassword()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static boolean majorsMatch(String stored, String incoming) {
        if (stored == null) {
            return incoming == null;
        }
        return stored.equals(incoming);
    }

    private static String display(String value) {
        return value == null ? Msg.t("roster.row.none") : value;
    }

    private static final class RowValidation {
        final boolean blocking;
        final boolean alreadyImported;
        final boolean majorWarning;
        final String duplicateKey;
        final List<String> messages;
        final User existing;

        RowValidation(
                boolean blocking,
                boolean alreadyImported,
                boolean majorWarning,
                String duplicateKey,
                List<String> messages,
                User existing) {
            this.blocking = blocking;
            this.alreadyImported = alreadyImported;
            this.majorWarning = majorWarning;
            this.duplicateKey = duplicateKey;
            this.messages = messages;
            this.existing = existing;
        }
    }

    static final class ClassConflictException extends RuntimeException {
    }
}
