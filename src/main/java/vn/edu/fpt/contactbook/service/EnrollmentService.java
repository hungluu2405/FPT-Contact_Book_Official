package vn.edu.fpt.contactbook.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.EnrollClassResult;
import vn.edu.fpt.contactbook.dto.EnrollmentRow;
import vn.edu.fpt.contactbook.dto.StudentCandidate;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.repository.StudentRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.LikePatterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ghi danh sinh viên vào lớp học phần (SRS FR-ORG-06), chỉ ADMIN; trưởng bộ môn chỉ xem danh sách.
 * Mọi thao tác ghi danh khóa dòng lớp học phần trước rồi mới đếm số ENROLLED, nên hai người ghi danh
 * cùng lúc không vượt sĩ số. Ghi danh lại SV đã DROPPED đổi dòng cũ về ENROLLED (uq_enrollments);
 * gỡ SV đổi sang DROPPED, không xóa dòng.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    static final int CANDIDATE_LIMIT = 20;

    private final ClassSectionRepository classSectionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentRepository studentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ClassSectionService classSectionService;
    private final UserAccessPolicy userAccessPolicy;
    private final AuditService auditService;

    /** Danh sách sinh viên của lớp; xem được bởi người xem được lớp (ADMIN, trưởng bộ môn của môn). */
    @Transactional(readOnly = true)
    public Page<EnrollmentRow> enrollments(AppUserPrincipal actor, Long sectionId, EnrollmentStatus status, int page) {
        classSectionService.findInScope(actor, sectionId);
        return enrollmentRepository.findPageBySection(sectionId, status, CatalogSupport.page(page))
                .map(EnrollmentService::toRow);
    }

    /** Sinh viên đang học có thể ghi danh, tìm theo mã hoặc họ tên; tối đa CANDIDATE_LIMIT kết quả. */
    @Transactional(readOnly = true)
    public List<StudentCandidate> candidates(AppUserPrincipal actor, Long sectionId, String keyword) {
        userAccessPolicy.requireAdmin(actor);
        classSectionService.findInScope(actor, sectionId);
        return studentRepository.findCandidates(LikePatterns.containsIgnoreCase(keyword), sectionId,
                        PageRequest.of(0, CANDIDATE_LIMIT)).stream()
                .map(student -> new StudentCandidate(student.getId(), student.getStudentCode(),
                        student.getUser().getFullName(),
                        student.getSchoolClass() == null ? null : student.getSchoolClass().getCode()))
                .toList();
    }

    /** Ghi danh một sinh viên; trả true nếu là ghi danh lại (dòng cũ DROPPED). */
    @Transactional
    public boolean enroll(AppUserPrincipal actor, Long sectionId, Long studentId, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        ClassSection section = lockOpenSection(sectionId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new BusinessException(SectionMessages.STUDENT_NOT_FOUND));
        if (student.getStatus() != StudentStatus.STUDYING) {
            throw new BusinessException(SectionMessages.STUDENT_NOT_STUDYING);
        }
        Enrollment existing = enrollmentRepository.findByClassSectionIdAndStudentId(sectionId, studentId).orElse(null);
        if (existing != null && existing.getStatus() == EnrollmentStatus.ENROLLED) {
            throw new BusinessException(SectionMessages.ALREADY_ENROLLED);
        }
        if (existing != null && existing.getStatus() == EnrollmentStatus.COMPLETED) {
            throw new BusinessException(SectionMessages.ALREADY_COMPLETED);
        }
        long enrolled = enrollmentRepository.countByClassSectionIdAndStatus(sectionId, EnrollmentStatus.ENROLLED);
        if (section.getMaxStudents() != null && enrolled >= section.getMaxStudents()) {
            throw new BusinessException(SectionMessages.SECTION_FULL.formatted(enrolled, section.getMaxStudents()));
        }
        boolean reEnrolled = existing != null;
        apply(section, List.of(student), existing == null ? Map.of() : Map.of(studentId, existing));
        auditService.log(actor.getUserId(), AuditActions.ENROLL_STUDENT, AuditActions.ENTITY_CLASS_SECTION, sectionId,
                "student=" + student.getStudentCode() + (reEnrolled ? ", reEnrolled" : ""), ipAddress);
        return reEnrolled;
    }

    /** Gỡ sinh viên khỏi lớp: đổi dòng ENROLLED sang DROPPED, không xóa. */
    @Transactional
    public void drop(AppUserPrincipal actor, Long sectionId, Long studentId, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        lockSection(sectionId);
        Enrollment enrollment = enrollmentRepository.findByClassSectionIdAndStudentId(sectionId, studentId)
                .filter(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
                .orElseThrow(() -> new BusinessException(SectionMessages.NOT_ENROLLED));
        enrollment.setStatus(EnrollmentStatus.DROPPED);
        enrollmentRepository.flush();
        auditService.log(actor.getUserId(), AuditActions.DROP_STUDENT, AuditActions.ENTITY_CLASS_SECTION, sectionId,
                "student=" + enrollment.getStudent().getStudentCode(), ipAddress);
    }

    /**
     * Ghi danh cả lớp hành chính: bỏ qua SV đã có trong lớp (ENROLLED hoặc COMPLETED) và SV không STUDYING;
     * số SV cần thêm vượt số chỗ còn lại thì từ chối cả lô, không ghi ai.
     */
    @Transactional
    public EnrollClassResult enrollClass(AppUserPrincipal actor, Long sectionId, Long classId, String ipAddress) {
        userAccessPolicy.requireAdmin(actor);
        ClassSection section = lockOpenSection(sectionId);
        SchoolClass schoolClass = classId == null ? null : schoolClassRepository.findById(classId).orElse(null);
        if (schoolClass == null) {
            throw new BusinessException(SectionMessages.CLASS_REQUIRED);
        }
        List<Student> students = studentRepository.findBySchoolClassIdOrderByStudentCodeAsc(classId);
        Map<Long, Enrollment> existing = existingByStudent(sectionId, students);

        List<Student> toEnroll = new ArrayList<>();
        int skippedEnrolled = 0;
        int skippedNotStudying = 0;
        int reEnrolled = 0;
        for (Student student : students) {
            Enrollment enrollment = existing.get(student.getId());
            if (enrollment != null && enrollment.getStatus() != EnrollmentStatus.DROPPED) {
                skippedEnrolled++;
            } else if (student.getStatus() != StudentStatus.STUDYING) {
                skippedNotStudying++;
            } else {
                toEnroll.add(student);
                if (enrollment != null) {
                    reEnrolled++;
                }
            }
        }
        requireCapacity(section, toEnroll.size());
        apply(section, toEnroll, existing);
        EnrollClassResult result = new EnrollClassResult(toEnroll.size() - reEnrolled, reEnrolled, skippedEnrolled,
                skippedNotStudying);
        auditService.log(actor.getUserId(), AuditActions.ENROLL_CLASS, AuditActions.ENTITY_CLASS_SECTION, sectionId,
                "class=" + schoolClass.getCode() + ", added=" + result.added() + ", reEnrolled=" + reEnrolled
                        + ", skipped=" + (skippedEnrolled + skippedNotStudying), ipAddress);
        return result;
    }

    /** Khóa dòng lớp học phần (UPDLOCK đến hết transaction) và yêu cầu lớp đang OPEN. */
    ClassSection lockOpenSection(Long sectionId) {
        ClassSection section = lockSection(sectionId);
        if (section.getStatus() != SectionStatus.OPEN) {
            throw new BusinessException(SectionMessages.SECTION_NOT_OPEN);
        }
        return section;
    }

    private ClassSection lockSection(Long sectionId) {
        return classSectionRepository.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new NotFoundException(SectionMessages.SECTION_NOT_FOUND));
    }

    /** Lớp đang khóa: số ENROLLED hiện tại + needed không được vượt sĩ số (NULL là không giới hạn). */
    void requireCapacity(ClassSection section, int needed) {
        if (section.getMaxStudents() == null || needed == 0) {
            return;
        }
        long enrolled = enrollmentRepository.countByClassSectionIdAndStatus(section.getId(),
                EnrollmentStatus.ENROLLED);
        long remaining = Math.max(0, section.getMaxStudents() - enrolled);
        if (needed > remaining) {
            throw new BusinessException(SectionMessages.BATCH_OVER_CAPACITY.formatted(needed, remaining));
        }
    }

    Map<Long, Enrollment> existingByStudent(Long sectionId, List<Student> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        return enrollmentRepository.findByClassSectionIdAndStudentIdIn(sectionId,
                        students.stream().map(Student::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getStudent().getId(), Function.identity()));
    }

    /** Ghi danh các SV đã kiểm tra: dòng cũ (DROPPED) đổi về ENROLLED, còn lại thêm dòng mới. */
    void apply(ClassSection section, List<Student> students, Map<Long, Enrollment> existing) {
        try {
            for (Student student : students) {
                Enrollment enrollment = existing.get(student.getId());
                if (enrollment == null) {
                    enrollment = new Enrollment();
                    enrollment.setClassSection(section);
                    enrollment.setStudent(student);
                    enrollmentRepository.save(enrollment);
                } else {
                    enrollment.setStatus(EnrollmentStatus.ENROLLED);
                    enrollment.setEnrolledAt(AppTime.now());
                }
            }
            enrollmentRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ConstraintViolationMapper.map(e);
        }
    }

    private static EnrollmentRow toRow(Enrollment enrollment) {
        Student student = enrollment.getStudent();
        return new EnrollmentRow(enrollment.getId(), student.getId(), student.getStudentCode(),
                student.getUser().getFullName(),
                student.getSchoolClass() == null ? null : student.getSchoolClass().getCode(), enrollment.getStatus(),
                enrollment.getEnrolledAt());
    }
}
