package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.InSemester;
import vn.edu.fpt.contactbook.dto.StudentGradeRow;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ScheduleViewService.Audience;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Điểm của sinh viên (của mình) và phụ huynh (con đã liên kết) theo học kỳ (SRS FR-GRADE-05, UC-07). CHỈ đọc ô đã
 * công bố, lọc ngay trong truy vấn (GradeRepository.findPublishedByEnrollmentIds); ô chưa công bố giống ô chưa có
 * điểm và trung bình chỉ tính trên điểm đã công bố.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GradeViewService {

    private final ScheduleViewService scheduleViewService;
    private final EnrollmentRepository enrollmentRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final GradeRepository gradeRepository;

    /** Các lớp học phần của học kỳ mà sinh viên đang học hoặc đã học xong (không tính lớp đã rút). */
    public InSemester<List<StudentGradeRow>> student(AppUserPrincipal actor, Audience audience, Long studentId,
                                                     Long semesterId) {
        Long id = scheduleViewService.studentId(actor, audience, studentId);
        Semester semester = scheduleViewService.resolveSemester(semesterId);
        List<Enrollment> enrollments = enrollmentRepository.findAttendedInSemester(id, semester.getId());
        if (enrollments.isEmpty()) {
            return new InSemester<>(ScheduleViewService.toView(semester), List.of());
        }
        Map<Long, List<GradeComponent>> componentsBySubject = gradeComponentRepository
                .findBySubjectIdInOrderByIdAsc(enrollments.stream()
                        .map(e -> e.getClassSection().getSubject().getId()).distinct().toList())
                .stream().collect(Collectors.groupingBy(c -> c.getSubject().getId()));
        Map<Long, Map<Long, BigDecimal>> published = new HashMap<>();
        for (Grade grade : gradeRepository.findPublishedByEnrollmentIds(
                enrollments.stream().map(Enrollment::getId).toList())) {
            if (grade.getScore() != null) {
                published.computeIfAbsent(grade.getEnrollment().getId(), key -> new HashMap<>())
                        .put(grade.getComponent().getId(), grade.getScore());
            }
        }
        List<StudentGradeRow> rows = enrollments.stream().map(enrollment -> {
            ClassSection section = enrollment.getClassSection();
            List<GradeComponent> components = componentsBySubject.getOrDefault(section.getSubject().getId(),
                    List.of());
            Map<Long, BigDecimal> scores = published.getOrDefault(enrollment.getId(), Map.of());
            return new StudentGradeRow(section.getCode(), section.getSubject().getCode(),
                    section.getSubject().getName(),
                    components.stream().map(c -> new StudentGradeRow.Cell(c.getName(), c.getWeight(),
                            c.getMaxScore(), scores.get(c.getId()))).toList(),
                    GradeRule.average(components, scores));
        }).toList();
        return new InSemester<>(ScheduleViewService.toView(semester), rows);
    }
}
