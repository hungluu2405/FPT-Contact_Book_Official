package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.GradeComponentForm;
import vn.edu.fpt.contactbook.dto.GradeComponentSummary;
import vn.edu.fpt.contactbook.dto.GradeComponentView;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.GradeComponent;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.GradeComponentRepository;
import vn.edu.fpt.contactbook.repository.GradeRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đầu điểm: tổng trọng số và cảnh báo, biên trọng số/điểm tối đa, trùng tên, chặn xóa khi đã có điểm,
 * phạm vi trưởng bộ môn.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GradeComponentServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD_SE = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock SubjectRepository subjectRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock ClassSectionRepository classSectionRepository;
    @Mock GradeComponentRepository gradeComponentRepository;
    @Mock GradeRepository gradeRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private GradeComponentService service;
    private Subject seSubject;
    private GradeComponent assignment;

    @BeforeEach
    void setUp() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        SubjectService subjectService = new SubjectService(subjectRepository, departmentRepository,
                classSectionRepository, new SubjectAccessPolicy(new UserAccessPolicy(teacherRepository)), auditService);
        service = new GradeComponentService(subjectService, gradeComponentRepository, gradeRepository, auditService);

        Department se = CatalogTestData.department(1L, "SE");
        seSubject = CatalogTestData.subject(1L, "TOAN10", se);
        Subject iaSubject = CatalogTestData.subject(50L, "IAO201", CatalogTestData.department(38L, "IA"));
        when(subjectRepository.findById(1L)).thenReturn(Optional.of(seSubject));
        when(subjectRepository.findById(50L)).thenReturn(Optional.of(iaSubject));

        assignment = component(1L, seSubject, "Assignment", "20");
        when(gradeComponentRepository.findById(1L)).thenReturn(Optional.of(assignment));
        when(gradeComponentRepository.findById(70L)).thenReturn(Optional.of(component(70L, iaSubject, "Lab", "30")));
        when(gradeComponentRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            GradeComponent saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
    }

    @Test
    @DisplayName("Tổng đúng 100 (kể cả 100.00) là đủ; khác 100 hoặc chưa có đầu điểm thì cảnh báo")
    void summarizeTotals() {
        GradeComponentSummary full = GradeComponentService.summarize(List.of(view("20"), view("30.00"), view("50")));
        assertThat(full.totalWeight()).isEqualByComparingTo("100");
        assertThat(full.complete()).isTrue();

        GradeComponentSummary partial = GradeComponentService.summarize(List.of(view("33.33"), view("33.33"),
                view("33.33")));
        assertThat(partial.totalWeight()).isEqualByComparingTo("99.99");
        assertThat(partial.complete()).isFalse();

        GradeComponentSummary over = GradeComponentService.summarize(List.of(view("60"), view("50")));
        assertThat(over.complete()).isFalse();

        GradeComponentSummary empty = GradeComponentService.summarize(List.of());
        assertThat(empty.totalWeight()).isEqualByComparingTo("0");
        assertThat(empty.complete()).isFalse();
    }

    @Test
    void summaryMarksComponentsWithGrades() {
        when(gradeComponentRepository.findBySubjectIdOrderByIdAsc(1L))
                .thenReturn(List.of(assignment, component(2L, seSubject, "Final", "80")));
        when(gradeRepository.findComponentIdsWithGrades(1L)).thenReturn(Set.of(1L));

        GradeComponentSummary summary = service.summary(ADMIN, 1L);

        assertThat(summary.components()).extracting(GradeComponentView::hasGrades).containsExactly(true, false);
        assertThat(summary.complete()).isTrue();
    }

    @ParameterizedTest(name = "trọng số {0} → hợp lệ = {1}")
    @CsvSource({"0, false", "-1, false", "0.01, true", "50.5, true", "100, true", "100.00, true",
            "100.01, false", "12.345, false", "12.340, true"})
    void weightBoundaries(String weight, boolean valid) {
        Map<String, String> errors = new LinkedHashMap<>();

        GradeComponentService.checkWeight(new BigDecimal(weight), errors);

        assertThat(errors.isEmpty()).isEqualTo(valid);
    }

    @ParameterizedTest(name = "điểm tối đa {0} → hợp lệ = {1}")
    @CsvSource({"0, false", "0.01, true", "10, true", "10.00, true", "10.01, false", "9.999, false"})
    void maxScoreBoundaries(String maxScore, boolean valid) {
        Map<String, String> errors = new LinkedHashMap<>();

        GradeComponentService.checkMaxScore(new BigDecimal(maxScore), errors);

        assertThat(errors.isEmpty()).isEqualTo(valid);
    }

    @Test
    void missingWeightAndMaxScoreAreFieldErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        GradeComponentService.checkWeight(null, errors);
        GradeComponentService.checkMaxScore(null, errors);
        assertThat(errors).containsOnlyKeys("weight", "maxScore");
    }

    @Test
    @DisplayName("Thêm đầu điểm: điểm tối đa mặc định 10, tên được trim, ghi audit")
    void createUsesDefaultMaxScore() {
        GradeComponentForm form = form("  Progress Test ", "10");

        service.create(ADMIN, 1L, form, "127.0.0.1");

        ArgumentCaptor<GradeComponent> saved = ArgumentCaptor.forClass(GradeComponent.class);
        verify(gradeComponentRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Progress Test");
        assertThat(saved.getValue().getMaxScore()).isEqualByComparingTo("10");
        assertThat(saved.getValue().getSubject()).isSameAs(seSubject);
        verify(auditLogRepository).save(any());
    }

    @Test
    void duplicateNameInSubjectIsFieldError() {
        when(gradeComponentRepository.existsBySubjectIdAndNameIgnoreCase(1L, "assignment")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ADMIN, 1L, form("assignment", "10"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("name", CatalogMessages.COMPONENT_NAME_TAKEN));
        verify(gradeComponentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Xóa đầu điểm đã có điểm → chặn, không xóa")
    void deleteBlockedWhenGradesExist() {
        when(gradeRepository.existsByComponentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(ADMIN, 1L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CatalogMessages.COMPONENT_HAS_GRADES);
        verify(gradeComponentRepository, never()).delete(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void deleteWithoutGradesRemovesAndAudits() {
        service.delete(ADMIN, 1L, 1L, "127.0.0.1");

        verify(gradeComponentRepository).delete(assignment);
        verify(auditLogRepository).save(any());
    }

    @Test
    @DisplayName("Điểm được nhập chen vào lúc xóa: lỗi fk_grades_component đổi thành thông báo")
    void deleteRaceIsFriendlyError() {
        doThrow(new DataIntegrityViolationException("x", new SQLException(
                "The DELETE statement conflicted with the REFERENCE constraint \"fk_grades_component\".")))
                .when(gradeComponentRepository).flush();

        assertThatThrownBy(() -> service.delete(ADMIN, 1L, 1L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CatalogMessages.COMPONENT_HAS_GRADES);
    }

    @Test
    @DisplayName("Đầu điểm của môn khác trên URL → 404; HEAD với môn tổ chuyên môn khác → 403 trước khi tra đầu điểm")
    void componentMustBelongToSubjectInScope() {
        assertThatThrownBy(() -> service.editForm(ADMIN, 1L, 70L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.editForm(HEAD_SE, 50L, 70L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.create(HEAD_SE, 50L, form("Quiz", "10"), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(HEAD_SE, 50L, 70L, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.summary(HEAD_SE, 50L)).isInstanceOf(AccessDeniedException.class);
        verify(gradeComponentRepository, never()).delete(any());

        assertThat(service.editForm(HEAD_SE, 1L, 1L).getName()).isEqualTo("Assignment");
    }

    private static GradeComponentForm form(String name, String weight) {
        GradeComponentForm form = new GradeComponentForm();
        form.setName(name);
        form.setWeight(new BigDecimal(weight));
        return form;
    }

    private static GradeComponentView view(String weight) {
        return new GradeComponentView(null, "x", new BigDecimal(weight), BigDecimal.TEN, false);
    }

    private static GradeComponent component(Long id, Subject subject, String name, String weight) {
        GradeComponent component = new GradeComponent();
        component.setId(id);
        component.setSubject(subject);
        component.setName(name);
        component.setWeight(new BigDecimal(weight));
        return component;
    }
}
