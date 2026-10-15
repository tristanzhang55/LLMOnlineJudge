package com.tfzhang.linuxback.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tfzhang.linuxback.common.CurrentUser;
import com.tfzhang.linuxback.common.GradeException;
import com.tfzhang.linuxback.common.i18n.Msg;
import com.tfzhang.linuxback.dto.GradeRosterRow;
import com.tfzhang.linuxback.dto.SubmissionRoundAggregateRow;
import com.tfzhang.linuxback.entity.Experiment;
import com.tfzhang.linuxback.entity.ExperimentQuestion;
import com.tfzhang.linuxback.entity.Question;
import com.tfzhang.linuxback.entity.TeachingClass;
import com.tfzhang.linuxback.excel.GradeExcelRow;
import com.tfzhang.linuxback.mapper.ClassMapper;
import com.tfzhang.linuxback.mapper.ClassStudentMapper;
import com.tfzhang.linuxback.mapper.ExperimentMapper;
import com.tfzhang.linuxback.mapper.ExperimentQuestionMapper;
import com.tfzhang.linuxback.mapper.QuestionMapper;
import com.tfzhang.linuxback.mapper.SubmissionMapper;
import com.tfzhang.linuxback.service.SubmissionRoundStatus;
import com.tfzhang.linuxback.service.TeacherGradeService;
import com.tfzhang.linuxback.vo.GradeListResponse;
import com.tfzhang.linuxback.vo.GradeStudentRowVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherGradeServiceImpl implements TeacherGradeService {

    private static final Pattern UNSAFE_FILENAME = Pattern.compile("[\\\\/:*?\"<>|]");

    private final ClassMapper classMapper;
    private final ExperimentMapper experimentMapper;
    private final ExperimentQuestionMapper experimentQuestionMapper;
    private final QuestionMapper questionMapper;
    private final ClassStudentMapper classStudentMapper;
    private final SubmissionMapper submissionMapper;

    @Override
    public GradeListResponse list(
            Long classId,
            Long experimentId,
            String semester,
            int page,
            int pageSize,
            String studentName) {
        long teacherId = CurrentUser.requireId();
        TeachingClass classRow = loadClass(teacherId, classId);
        assertSemester(classRow, semester);
        Experiment experiment = experimentId == null ? null : loadExperiment(classId, experimentId);
        boolean experimentSelected = experiment != null;
        String studentNameLike = TeacherRosterServiceImpl.toStudentNameLike(studentName);

        long total = classStudentMapper.countGradeRoster(teacherId, classId, studentNameLike);
        GradeListResponse response = new GradeListResponse();
        response.setTotal(total);
        response.setExperimentSelected(experimentSelected);
        if (total == 0) {
            return response;
        }

        int offset = (page - 1) * pageSize;
        List<GradeRosterRow> roster = classStudentMapper.findGradeRoster(
                teacherId, classId, studentNameLike, pageSize, offset);
        Map<Long, SubmissionRoundAggregateRow> scores = Map.of();
        if (experimentSelected && !roster.isEmpty()) {
            scores = latestRoundByStudent(experiment.getId(), roster);
        }
        List<GradeStudentRowVO> data = new ArrayList<>(roster.size());
        for (GradeRosterRow row : roster) {
            data.add(toListRow(row, experimentSelected, scores.get(row.getUserId())));
        }
        response.setData(data);
        return response;
    }

    @Override
    public void export(Long classId, Long experimentId, HttpServletResponse response) throws IOException {
        long teacherId = CurrentUser.requireId();
        TeachingClass classRow = loadClass(teacherId, classId);
        Experiment experiment = loadExperiment(classId, experimentId);
        List<GradeRosterRow> roster = classStudentMapper.findGradeRoster(
                teacherId, classId, null, null, null);
        Map<Long, SubmissionRoundAggregateRow> scores = Map.of();
        if (!roster.isEmpty()) {
            scores = latestRoundByStudent(experiment.getId(), roster);
        }
        int totalScore = experimentTotalScore(experiment.getId());
        List<GradeExcelRow> rows = new ArrayList<>(roster.size());
        for (GradeRosterRow row : roster) {
            rows.add(toExcelRow(row, scores.get(row.getUserId()), totalScore));
        }

        String fileName = sanitizeFilePart(classRow.getName())
                + "-"
                + sanitizeFilePart(experiment.getTitle())
                + Msg.t("export.grade.fileSuffix")
                + ".xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"grades.xlsx\"; filename*=UTF-8''" + encoded);
        List<List<String>> head = List.of(
                List.of(Msg.t("export.grade.header.studentId")),
                List.of(Msg.t("export.grade.header.studentName")),
                List.of(Msg.t("export.grade.header.major")),
                List.of(Msg.t("export.grade.header.score")),
                List.of(Msg.t("export.grade.header.totalScore")),
                List.of(Msg.t("export.grade.header.status")));
        List<List<Object>> data = new ArrayList<>(rows.size());
        for (GradeExcelRow excel : rows) {
            List<Object> line = new ArrayList<>(6);
            line.add(excel.getStudentId());
            line.add(excel.getStudentName());
            line.add(excel.getMajor());
            line.add(excel.getScore());
            line.add(excel.getTotalScore());
            line.add(excel.getStatusLabel());
            data.add(line);
        }
        EasyExcel.write(response.getOutputStream())
                .head(head)
                .sheet(Msg.t("export.grade.sheetName"))
                .doWrite(data);
    }

    private TeachingClass loadClass(long teacherId, Long classId) {
        if (classId == null) {
            throw GradeException.invalid(Msg.t("error.grade.classIdMin"), "classId");
        }
        TeachingClass row = classMapper.selectById(classId);
        if (row == null || !Objects.equals(row.getTeacherId(), teacherId)) {
            throw GradeException.notFound();
        }
        return row;
    }

    private void assertSemester(TeachingClass classRow, String semester) {
        String s = trimToNull(semester);
        if (s == null) {
            return;
        }
        if (!Objects.equals(s, classRow.getSemester())) {
            throw GradeException.invalid(Msg.t("error.grade.semesterMismatch"), "semester");
        }
    }

    private Experiment loadExperiment(Long classId, Long experimentId) {
        if (experimentId == null) {
            throw GradeException.invalid(Msg.t("error.grade.experimentIdMin"), "experimentId");
        }
        Experiment experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) {
            throw GradeException.notFound();
        }
        if (!Objects.equals(experiment.getClassId(), classId)) {
            throw GradeException.invalid(Msg.t("error.grade.experimentNotInClass"), "experimentId");
        }
        return experiment;
    }

    private Map<Long, SubmissionRoundAggregateRow> latestRoundByStudent(
            long experimentId, List<GradeRosterRow> roster) {
        List<Long> studentIds = roster.stream().map(GradeRosterRow::getUserId).toList();
        List<SubmissionRoundAggregateRow> aggregates =
                submissionMapper.findCurrentRoundAggregates(experimentId, studentIds);
        Map<Long, SubmissionRoundAggregateRow> latest = new HashMap<>();
        for (SubmissionRoundAggregateRow row : aggregates) {
            SubmissionRoundAggregateRow previous = latest.get(row.getStudentId());
            if (previous == null || isLater(row.getSubmitTime(), previous.getSubmitTime())) {
                latest.put(row.getStudentId(), row);
            }
        }
        return latest;
    }

    private static boolean isLater(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return candidate.isAfter(current);
    }

    private static GradeStudentRowVO toListRow(
            GradeRosterRow row, boolean experimentSelected, SubmissionRoundAggregateRow round) {
        GradeStudentRowVO vo = new GradeStudentRowVO();
        vo.setStudentId(row.getStudentId());
        vo.setStudentName(row.getStudentName());
        vo.setClassName(row.getClassName());
        if (experimentSelected && round != null) {
            String status = SubmissionRoundStatus.derive(round);
            vo.setStatus(status);
            vo.setScore(SubmissionRoundStatus.scoreForStatus(status, round.getScoreSum()));
        }
        return vo;
    }

    private int experimentTotalScore(long experimentId) {
        List<ExperimentQuestion> questions = experimentQuestionMapper.selectList(
                new LambdaQueryWrapper<ExperimentQuestion>()
                        .eq(ExperimentQuestion::getExperimentId, experimentId));
        if (questions.isEmpty()) {
            return 0;
        }
        List<Long> questionIds = questions.stream()
                .map(ExperimentQuestion::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Question> byId = questionIds.isEmpty()
                ? Map.of()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        int sum = 0;
        for (ExperimentQuestion eq : questions) {
            sum += ExperimentServiceImpl.computeEffectivePoints(eq.getPoints(), byId.get(eq.getQuestionId()));
        }
        return sum;
    }

    private static GradeExcelRow toExcelRow(
            GradeRosterRow row, SubmissionRoundAggregateRow round, int totalScore) {
        GradeExcelRow excel = new GradeExcelRow();
        excel.setStudentId(row.getStudentId());
        excel.setStudentName(row.getStudentName());
        excel.setMajor(row.getMajor());
        excel.setTotalScore(totalScore);
        if (round != null) {
            String status = SubmissionRoundStatus.derive(round);
            excel.setScore(SubmissionRoundStatus.scoreForStatus(status, round.getScoreSum()));
            excel.setStatusLabel(statusLabel(status));
        } else {
            excel.setStatusLabel(Msg.t("export.grade.status.notSubmitted"));
        }
        return excel;
    }

    static String statusLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SubmissionRoundStatus.PENDING,
                 SubmissionRoundStatus.GRADING_IN_PROGRESS,
                 SubmissionRoundStatus.COMPLETED,
                 SubmissionRoundStatus.GRADING_FAILED -> Msg.t("export.grade.status." + status);
            default -> null;
        };
    }

    static String sanitizeFilePart(String raw) {
        if (raw == null) {
            return "";
        }
        return UNSAFE_FILENAME.matcher(raw).replaceAll("");
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
