package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.GradeAdjustForm;
import vn.edu.fpt.contactbook.dto.GradeEntryForm;
import vn.edu.fpt.contactbook.dto.GradeSheet;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Grade;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.EnrollmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.AttendanceScope.Access;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bảng điểm: nhập ô chưa công bố, kiểm tra điểm, chặn khi tổng trọng số khác 100%, công bố, điều chỉnh ô đã công
 * bố, phạm vi giảng viên / trưởng bộ môn / ADMIN.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeServiceTest {

    /** users.id 3 là giảng viên GV002 (teachers.id 2), dạy lớp 10. */
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal OTHER_TEACHER = CatalogTestData.principal(16L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal HEAD_IA = CatalogTestData.principal(15L,
            Set.of(RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(38L)));

    @Mock ClassSectionRepository classSectionRepository;
    @Mock EnrollmentRepository enrollmentRepository;
    @Mock GradeComponentRepository gradeComponentRepository;
    @Mock GradeRepository gradeRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SemesterRepository semesterRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogRepository auditLogRepository;

    private GradeService service;
    private ClassSection section;
    private Enrollment e1;
    private Enrollment e2;
    private List<GradeComponent> components;
    private final List<Grade> grades = new ArrayList<>();

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        UserAccessPolicy userAccessPolicy = new UserAccessPolicy(teacherRepository);
        SubjectAccessPolicy policy = new SubjectAccessPolicy(userAccessPolicy);
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, policy, auditService);
        ClassSectionService classSectionService = new ClassSectionService(classSectionRepository,
                enrollmentRepository, subjectRepository, semesterRepository, teacherRepository, subjectService, policy,
                auditService);
        service = new GradeService(new AttendanceScope(teacherRepository, classSectionRepository, classSectionService,
                userAccessPolicy), classSectionRepository, enrollmentRepository, gradeComponentRepository,
                gradeRepository, userRepository, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        Teacher gv002 = CatalogTestData.teacher(2L, "GV002", se);
        section = CatalogTestData.section(10L, "PRJ301-FA26-01", CatalogTestData.subject(1L, "PRJ301", se),
                CatalogTestData.semester(1L, "FA26"), gv002, 40, SectionStatus.OPEN);
        e1 = CatalogTestData.enrollment(100L, section, CatalogTestData.student(1L, "SE190001", StudentStatus.STUDYING),
                EnrollmentStatus.ENROLLED);
        e2 = CatalogTestData.enrollment(101L, section, CatalogTestData.student(2L, "SE190002", StudentStatus.STUDYING),
                EnrollmentStatus.ENROLLED);
        components = List.of(GradeRuleTest.component(1L, "20", "10"), GradeRuleTest.component(2L, "30", "10"),
                GradeRuleTest.component(3L, "50", "10"));

        when(teacherRepository.findByUserId(3L)).thenReturn(Optional.of(gv002));
        when(teacherRepository.findByUserId(16L)).thenReturn(Optional.of(CatalogTestData.teacher(8L, "GV003", se)));
        when(classSectionRepository.findById(10L)).thenReturn(Optional.of(section));
        when(classSectionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(section));
        when(gradeComponentRepository.findBySubjectIdOrderByIdAsc(1L)).thenAnswer(invocation -> components);
        when(enrollmentRepository.findByClassSectionIdAndStatusOrderByStudentStudentCodeAsc(10L,
                EnrollmentStatus.ENROLLED)).thenReturn(List.of(e1, e2));
        when(gradeRepository.findBySectionId(10L)).thenReturn(grades);
        when(userRepository.getReferenceById(anyLong())).thenAnswer(invocation -> {
            User user = new User();
            user.setId(invocation.getArgument(0));
            return user;
        });
    }

    @Nested
    @DisplayName("Giảng viên nhập điểm")
    class Entry {

        @Test
        @DisplayName("Bảng điểm có mọi SV đang học × đầu điểm, trung bình chỉ khi đủ điểm")
        void sheetShowsAverages() {
            grades.addAll(List.of(grade(e1, 0, "8", false), grade(e1, 1, "7", true), grade(e1, 2, "9", false),
                    grade(e2, 0, "6", false)));
            when(gradeRepository.countPublishable(10L)).thenReturn(3L);

            GradeSheet sheet = service.sheet(TEACHER, Access.TEACHER, 10L);

            assertThat(sheet.weightsComplete()).isTrue();
            assertThat(sheet.publishable()).isEqualTo(3);
            assertThat(sheet.rows()).extracting(GradeSheet.Row::studentCode, GradeSheet.Row::average)
                    .containsExactly(tuple("SE190001", new BigDecimal("8.2")), tuple("SE190002", null));
            assertThat(sheet.rows().getFirst().cells().get(1).published()).isTrue();
        }

        @Test
        @DisplayName("Lưu ô mới: tạo dòng với graded_by, graded_at, điểm DECIMAL(4,2), audit ENTER_GRADES")
        void savesNewCells() {
            int changed = service.save(TEACHER, 10L, form(Map.of("100_1", "8,5", "100_2", "", "101_3", "10")),
                    "127.0.0.1");

            assertThat(changed).isEqualTo(2);
            ArgumentCaptor<List<Grade>> saved = ArgumentCaptor.forClass(List.class);
            verify(gradeRepository).saveAll(saved.capture());
            assertThat(saved.getValue())
                    .extracting(g -> g.getEnrollment().getId(), g -> g.getComponent().getId(), Grade::getScore,
                            g -> g.getGradedBy().getId(), Grade::isPublished)
                    .containsExactlyInAnyOrder(tuple(100L, 1L, new BigDecimal("8.50"), 3L, false),
                            tuple(101L, 3L, new BigDecimal("10.00"), 3L, false));
            assertThat(saved.getValue()).allSatisfy(g -> assertThat(g.getGradedAt()).isNotNull());
            AuditLog audit = singleAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ENTER_GRADES);
            assertThat(audit.getDetail()).contains("SE190001/Đầu điểm 1: - -> 8.5", "SE190002/Đầu điểm 3: - -> 10");
        }

        @Test
        @DisplayName("Điểm vượt max_score, âm, quá 2 chữ số, chữ: lỗi theo từng ô, không lưu ô nào")
        void invalidScoresRejectAll() {
            assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of("100_1", "8", "100_2", "10.5",
                    "100_3", "-1", "101_1", "7.125", "101_2", "abc")), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getFieldErrors())
                            .containsOnlyKeys("scores[100_2]", "scores[100_3]", "scores[101_1]", "scores[101_2]")
                            .containsEntry("scores[100_2]", "Điểm phải từ 0 đến 10")
                            .containsEntry("scores[101_1]", GradeMessages.SCORE_SCALE)
                            .containsEntry("scores[101_2]", GradeMessages.SCORE_INVALID));
            verify(gradeRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Điểm vượt max_score nhỏ hơn 10 (max 5) bị từ chối")
        void scoreAboveSmallMax() {
            components = List.of(GradeRuleTest.component(1L, "100", "5"));

            assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of("100_1", "6")), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getFieldErrors())
                            .containsEntry("scores[100_1]", "Điểm phải từ 0 đến 5"));
        }

        @Test
        @DisplayName("Gửi giá trị cho ô đã công bố → từ chối, không lưu gì")
        void publishedCellRejected() {
            grades.add(grade(e1, 0, "8", true));

            assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of("100_1", "9", "100_2", "7")),
                    "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.CELL_PUBLISHED);
            verify(gradeRepository, never()).saveAll(anyList());
            assertThat(grades.getFirst().getScore()).isEqualByComparingTo("8");
        }

        @Test
        @DisplayName("Ô không thuộc lớp (sinh viên hoặc đầu điểm khác, khóa sai dạng) → danh sách đã đổi")
        void unknownCell() {
            for (String key : List.of("999_1", "100_99", "abc")) {
                assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of(key, "8")), "127.0.0.1"))
                        .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.STUDENTS_CHANGED);
            }
        }

        @Test
        @DisplayName("Xóa trống ô chưa công bố → điểm NULL; ô không đổi thì không ghi")
        void clearAndUnchanged() {
            Grade existing = grade(e1, 0, "8", false);
            grades.addAll(List.of(existing, grade(e1, 1, "7", false)));

            int changed = service.save(TEACHER, 10L, form(Map.of("100_1", " ", "100_2", "7.00")), "127.0.0.1");

            assertThat(changed).isEqualTo(1);
            assertThat(existing.getScore()).isNull();
            assertThat(singleAudit().getDetail()).contains("SE190001/Đầu điểm 1: 8 -> -");
        }

        @Test
        @DisplayName("Không có thay đổi → 0, không audit")
        void nothingChanged() {
            grades.add(grade(e1, 0, "8", false));

            assertThat(service.save(TEACHER, 10L, form(Map.of("100_1", "8")), "127.0.0.1")).isZero();
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tổng trọng số khác 100% → chặn nhập điểm")
        void weightsBlockEntry() {
            components = List.of(GradeRuleTest.component(1L, "20", "10"), GradeRuleTest.component(2L, "30", "10"));

            assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of("100_1", "8")), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("đang là 50%");
            verify(gradeRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Lớp học phần đã hủy → chặn nhập điểm")
        void cancelledSectionBlocksEntry() {
            section.setStatus(SectionStatus.CANCELLED);

            assertThatThrownBy(() -> service.save(TEACHER, 10L, form(Map.of("100_1", "8")), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.SECTION_CANCELLED);
            verify(gradeRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Giảng viên khác (gõ URL) → 403 khi xem và khi lưu")
        void otherTeacherDenied() {
            assertThatThrownBy(() -> service.sheet(OTHER_TEACHER, Access.TEACHER, 10L))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.save(OTHER_TEACHER, 10L, form(Map.of("100_1", "8")), "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Trưởng bộ môn khác xem bảng điểm lớp ngoài bộ môn → 403")
        void headOfOtherDepartmentDenied() {
            assertThatThrownBy(() -> service.sheet(HEAD_IA, Access.MANAGER, 10L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Công bố")
    class Publish {

        @Test
        @DisplayName("Trưởng bộ môn công bố mọi ô đã có điểm chưa công bố, lưu người và thời điểm, audit số ô")
        void headPublishes() {
            Grade a = grade(e1, 0, "8", false);
            Grade b = grade(e2, 2, "6", false);
            when(gradeRepository.findPublishable(10L)).thenReturn(List.of(a, b));

            assertThat(service.publish(HEAD_SE, 10L, "127.0.0.1")).isEqualTo(2);

            assertThat(List.of(a, b)).allSatisfy(g -> {
                assertThat(g.isPublished()).isTrue();
                assertThat(g.getPublishedBy().getId()).isEqualTo(2L);
                assertThat(g.getPublishedAt()).isNotNull();
            });
            AuditLog audit = singleAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.PUBLISH_GRADE);
            assertThat(audit.getDetail()).contains("count=2");
        }

        @Test
        @DisplayName("Tổng trọng số khác 100% → chặn công bố, không đọc ô nào")
        void weightsBlockPublish() {
            components = List.of(GradeRuleTest.component(1L, "20", "10"), GradeRuleTest.component(2L, "30", "10"),
                    GradeRuleTest.component(3L, "40", "10"));

            assertThatThrownBy(() -> service.publish(ADMIN, 10L, "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("đang là 90%");
            verify(gradeRepository, never()).findPublishable(anyLong());
        }

        @Test
        @DisplayName("Không có ô nào để công bố → báo lỗi, không audit")
        void nothingToPublish() {
            when(gradeRepository.findPublishable(10L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.publish(ADMIN, 10L, "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.NOTHING_TO_PUBLISH);
            verify(auditLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("TEACHER và trưởng bộ môn khác không công bố được (403)")
        void publishScope() {
            assertThatThrownBy(() -> service.publish(TEACHER, 10L, "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> service.publish(HEAD_IA, 10L, "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Điều chỉnh ô đã công bố")
    class Adjust {

        private Grade published;

        @BeforeEach
        void publishedGrade() {
            published = grade(e1, 2, "6", true);
            published.setId(900L);
            published.setNote("Chấm đợt 1");
            when(gradeRepository.findWithDetailsById(900L)).thenReturn(Optional.of(published));
        }

        @Test
        @DisplayName("Có lý do: đổi điểm, lý do vào note, graded_by là người sửa, vẫn công bố, audit cũ → mới")
        void adjusts() {
            service.adjust(HEAD_SE, 10L, 900L, adjustForm("7,5", " Phúc khảo "), "127.0.0.1");

            assertThat(published.getScore()).isEqualByComparingTo("7.5");
            assertThat(published.getNote()).isEqualTo("Phúc khảo");
            assertThat(published.getGradedBy().getId()).isEqualTo(2L);
            assertThat(published.isPublished()).isTrue();
            AuditLog audit = singleAudit();
            assertThat(audit.getAction()).isEqualTo(AuditActions.ADJUST_GRADE);
            assertThat(audit.getEntityId()).isEqualTo(900L);
            assertThat(audit.getDetail()).contains("student=SE190001", "score=6 -> 7.5", "oldNote=Chấm đợt 1",
                    "reason=Phúc khảo");
        }

        @Test
        @DisplayName("Thiếu lý do → lỗi ở ô lý do, điểm không đổi")
        void reasonRequired() {
            assertThatThrownBy(() -> service.adjust(ADMIN, 10L, 900L, adjustForm("7", "  "), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.REASON_REQUIRED)
                    .extracting(e -> ((BusinessException) e).getField()).isEqualTo("reason");
            assertThat(published.getScore()).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("Điểm mới trùng điểm cũ hoặc vượt max_score → lỗi ở ô điểm")
        void invalidNewScore() {
            assertThatThrownBy(() -> service.adjust(ADMIN, 10L, 900L, adjustForm("6.00", "x"), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.SCORE_UNCHANGED);
            assertThatThrownBy(() -> service.adjust(ADMIN, 10L, 900L, adjustForm("11", "x"), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage("Điểm phải từ 0 đến 10");
        }

        @Test
        @DisplayName("Ô chưa công bố không điều chỉnh ở đây (do giảng viên sửa)")
        void notPublished() {
            published.setPublished(false);

            assertThatThrownBy(() -> service.adjust(ADMIN, 10L, 900L, adjustForm("7", "x"), "127.0.0.1"))
                    .isInstanceOf(BusinessException.class).hasMessage(GradeMessages.NOT_PUBLISHED);
        }

        @Test
        @DisplayName("Giảng viên không điều chỉnh được ô đã công bố (403)")
        void teacherCannotAdjust() {
            assertThatThrownBy(() -> service.adjust(TEACHER, 10L, 900L, adjustForm("7", "x"), "127.0.0.1"))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(published.getScore()).isEqualByComparingTo("6");
        }

        @Test
        @DisplayName("Ô điểm của lớp khác → không tìm thấy")
        void gradeOfAnotherSection() {
            ClassSection other = CatalogTestData.section(11L, "OTHER", section.getSubject(), section.getSemester(),
                    section.getTeacher(), 40, SectionStatus.OPEN);
            published.setEnrollment(CatalogTestData.enrollment(200L, other, e1.getStudent(),
                    EnrollmentStatus.ENROLLED));

            assertThatThrownBy(() -> service.adjustView(ADMIN, 10L, 900L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    private AuditLog singleAudit() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private Grade grade(Enrollment enrollment, int componentIndex, String score, boolean isPublished) {
        Grade grade = new Grade();
        grade.setEnrollment(enrollment);
        grade.setComponent(components.get(componentIndex));
        grade.setScore(new BigDecimal(score));
        grade.setPublished(isPublished);
        return grade;
    }

    private static GradeEntryForm form(Map<String, String> scores) {
        GradeEntryForm form = new GradeEntryForm();
        form.setScores(new LinkedHashMap<>(scores));
        return form;
    }

    private static GradeAdjustForm adjustForm(String score, String reason) {
        GradeAdjustForm form = new GradeAdjustForm();
        form.setScore(score);
        form.setReason(reason);
        return form;
    }
}
