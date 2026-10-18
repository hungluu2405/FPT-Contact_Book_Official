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
import vn.edu.fpt.contactbook.dto.SemesterForm;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nghiệp vụ học kỳ với repository giả lập: đặt học kỳ hiện tại (luôn đúng một) và kiểm tra ngày.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SemesterServiceTest {

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal HEAD = CatalogTestData.principal(2L,
            Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT), Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));

    @Mock SemesterRepository semesterRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock UserRepository userRepository;

    private SemesterService service;
    private Semester fall;
    private Semester spring;

    @BeforeEach
    void setUp() {
        service = new SemesterService(semesterRepository, new UserAccessPolicy(teacherRepository),
                new AuditService(auditLogRepository, userRepository));
        fall = semester(1L, "FA26", true);
        spring = semester(38L, "SP27", false);
        when(semesterRepository.findById(1L)).thenReturn(Optional.of(fall));
        when(semesterRepository.findById(38L)).thenReturn(Optional.of(spring));
    }

    @Test
    @DisplayName("Đặt học kỳ mới: gỡ cờ cũ và flush trước, rồi mới đặt cờ mới; luôn đúng một học kỳ hiện tại")
    void setCurrentClearsPreviousBeforeSettingNew() {
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.of(fall));
        List<String> flushedStates = new ArrayList<>();
        doAnswer(invocation -> {
            flushedStates.add("FA26=" + fall.isCurrent() + ",SP27=" + spring.isCurrent());
            return null;
        }).when(semesterRepository).flush();

        service.setCurrent(ADMIN, 38L, "127.0.0.1");

        assertThat(flushedStates).containsExactly("FA26=false,SP27=false", "FA26=false,SP27=true");
        assertThat(List.of(fall, spring)).filteredOn(Semester::isCurrent).containsExactly(spring);
        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(audit.capture());
        assertThat(audit.getValue().getAction()).isEqualTo(AuditActions.SET_CURRENT_SEMESTER);
        assertThat(audit.getValue().getEntityType()).isEqualTo("Semester");
        assertThat(audit.getValue().getEntityId()).isEqualTo(38L);
        assertThat(audit.getValue().getDetail()).contains("SP27").contains("previous=FA26");
    }

    @Test
    @DisplayName("Chưa có học kỳ hiện tại nào: chỉ đặt cờ học kỳ được chọn")
    void setCurrentWithoutPrevious() {
        fall.setCurrent(false);
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.empty());

        service.setCurrent(ADMIN, 38L, "127.0.0.1");

        assertThat(spring.isCurrent()).isTrue();
        verify(semesterRepository).flush();
    }

    @Test
    @DisplayName("Đặt lại chính học kỳ đang hiện tại: không đổi gì, không ghi audit")
    void setCurrentOnCurrentIsNoOp() {
        service.setCurrent(ADMIN, 1L, "127.0.0.1");

        assertThat(fall.isCurrent()).isTrue();
        verify(semesterRepository, never()).flush();
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thao tác đồng thời vi phạm ux_semesters_current → thông báo thân thiện, không ghi audit")
    void concurrentSetCurrentIsFriendlyError() {
        when(semesterRepository.findByCurrentTrue()).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            throw new DataIntegrityViolationException("x", new SQLException(
                    "Cannot insert duplicate key row in object 'dbo.semesters' with unique index 'ux_semesters_current'."));
        }).when(semesterRepository).flush();

        assertThatThrownBy(() -> service.setCurrent(ADMIN, 38L, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CatalogMessages.CURRENT_SEMESTER_CONFLICT);
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void headCannotSetCurrent() {
        assertThatThrownBy(() -> service.setCurrent(HEAD, 38L, "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(spring.isCurrent()).isFalse();
        verify(semesterRepository, never()).flush();
    }

    @ParameterizedTest(name = "{0} → {1}: lỗi = {2}")
    @CsvSource(nullValues = "null", value = {
            "2026-09-07, 2026-12-20, null",
            "2026-09-07, 2026-09-08, null",
            "2026-09-07, 2026-09-07, endDate",
            "2026-12-20, 2026-09-07, endDate",
            "null, 2026-09-07, startDate",
            "2026-09-07, null, endDate"
    })
    void checkDates(LocalDate start, LocalDate end, String errorField) {
        Map<String, String> errors = new LinkedHashMap<>();

        SemesterService.checkDates(start, end, errors);

        if (errorField == null) {
            assertThat(errors).isEmpty();
        } else {
            assertThat(errors).containsOnlyKeys(errorField);
        }
    }

    @Test
    @DisplayName("Tạo học kỳ: chuẩn hóa mã, luôn chưa là học kỳ hiện tại, báo trùng mã cạnh ô")
    void createNormalizesCodeAndChecksDuplicate() {
        SemesterForm form = form(" su27 ", "2027-05-03", "2027-08-22");
        when(semesterRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Semester saved = invocation.getArgument(0);
            saved.setId(40L);
            return saved;
        });

        service.create(ADMIN, form, "127.0.0.1");

        ArgumentCaptor<Semester> saved = ArgumentCaptor.forClass(Semester.class);
        verify(semesterRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("SU27");
        assertThat(saved.getValue().isCurrent()).isFalse();

        when(semesterRepository.existsByCode("FA26")).thenReturn(true);
        assertThatThrownBy(() -> service.create(ADMIN, form("fa26", "2027-05-03", "2027-08-22"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("code", CatalogMessages.SEMESTER_CODE_TAKEN));
    }

    @Test
    void updateRejectsEndBeforeStart() {
        assertThatThrownBy(() -> service.update(ADMIN, 38L, form("SP27", "2027-04-25", "2027-01-04"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getFieldErrors()).containsEntry("endDate", CatalogMessages.SEMESTER_DATES));
        assertThat(spring.getStartDate()).isEqualTo(LocalDate.parse("2027-01-04"));
    }

    private static SemesterForm form(String code, String start, String end) {
        SemesterForm form = new SemesterForm();
        form.setCode(code);
        form.setName("Học kỳ " + code.strip());
        form.setStartDate(LocalDate.parse(start));
        form.setEndDate(LocalDate.parse(end));
        return form;
    }

    private static Semester semester(Long id, String code, boolean current) {
        Semester semester = new Semester();
        semester.setId(id);
        semester.setCode(code);
        semester.setName(code);
        semester.setStartDate(LocalDate.parse("2027-01-04"));
        semester.setEndDate(LocalDate.parse("2027-04-25"));
        semester.setCurrent(current);
        return semester;
    }
}
