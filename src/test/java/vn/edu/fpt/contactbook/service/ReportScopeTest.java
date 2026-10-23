package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.fpt.contactbook.dto.ReportFilter;
import vn.edu.fpt.contactbook.exception.NotFoundException;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.repository.ClassSectionRepository;
import vn.edu.fpt.contactbook.repository.SubjectRepository;
import vn.edu.fpt.contactbook.repository.TeacherRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.service.ReportScope.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phạm vi báo cáo (CLAUDE.md mục 5), là hợp của mọi vai trò: ADMIN mọi lớp; TEACHER lớp mình dạy; HEAD lớp có môn
 * thuộc bộ môn mình. Tham số lọc sửa tay (lớp, môn ngoài phạm vi hoặc không tồn tại) bị chặn 403 với người không
 * phải ADMIN; ADMIN nhận 404 khi id không tồn tại.
 */
class ReportScopeTest {

    static final Department SE = CatalogTestData.department(1L, "SE");
    static final Department IA = CatalogTestData.department(38L, "IA");
    static final Semester HK1 = CatalogTestData.semester(1L, "HK1-2627");
    static final Semester HK2 = CatalogTestData.semester(38L, "HK2-2627");
    static final Subject PRJ = CatalogTestData.subject(1L, "TOAN10", SE);
    static final Subject SEC = CatalogTestData.subject(50L, "SEC101", IA);
    static final Teacher ME = CatalogTestData.teacher(2L, "GV002", SE);
    static final Teacher OTHER = CatalogTestData.teacher(8L, "GV003", IA);
    static final ClassSection MINE = CatalogTestData.section(1L, "TOAN10-01", PRJ, HK1, ME, 40, SectionStatus.OPEN);
    static final ClassSection SE_OTHER = CatalogTestData.section(27L, "TOAN10-02", PRJ, HK2, OTHER, 40,
            SectionStatus.OPEN);
    static final ClassSection IA_OTHER = CatalogTestData.section(60L, "SEC101-01", SEC, HK1, OTHER, 40,
            SectionStatus.OPEN);

    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());
    static final AppUserPrincipal TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER),
            Map.of(RoleCodes.TEACHER, Set.of(1L)));
    static final AppUserPrincipal HEAD = CatalogTestData.principal(2L, Set.of(RoleCodes.HEAD_OF_DEPARTMENT),
            Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(1L)));
    static final AppUserPrincipal STUDENT = CatalogTestData.principal(4L, Set.of(RoleCodes.STUDENT), Map.of());

    TeacherRepository teacherRepository;
    ClassSectionRepository classSectionRepository;
    SubjectRepository subjectRepository;
    ScheduleViewService scheduleViewService;
    ReportScope scope;

    @BeforeEach
    void setUp() {
        teacherRepository = mock(TeacherRepository.class);
        classSectionRepository = mock(ClassSectionRepository.class);
        subjectRepository = mock(SubjectRepository.class);
        scheduleViewService = mock(ScheduleViewService.class);
        scope = new ReportScope(new UserAccessPolicy(teacherRepository), teacherRepository, classSectionRepository,
                subjectRepository, scheduleViewService);
        when(teacherRepository.findByUserId(TEACHER.getUserId())).thenReturn(Optional.of(ME));
        when(classSectionRepository.findById(MINE.getId())).thenReturn(Optional.of(MINE));
        when(classSectionRepository.findById(SE_OTHER.getId())).thenReturn(Optional.of(SE_OTHER));
        when(classSectionRepository.findById(IA_OTHER.getId())).thenReturn(Optional.of(IA_OTHER));
        when(subjectRepository.findById(PRJ.getId())).thenReturn(Optional.of(PRJ));
        when(subjectRepository.findById(SEC.getId())).thenReturn(Optional.of(SEC));
        when(scheduleViewService.resolveSemester(any())).thenReturn(HK1);
    }

    @Nested
    @DisplayName("Lớp môn học trên tham số sectionId")
    class Section {

        @Test
        @DisplayName("Giáo viên xem lớp mình dạy; lớp người khác hoặc id không tồn tại → 403")
        void teacherOnlyOwnSections() {
            assertThat(scope.resolve(TEACHER, new ReportFilter(null, null, MINE.getId())).section()).isSameAs(MINE);

            for (Long id : List.of(SE_OTHER.getId(), IA_OTHER.getId(), 999L)) {
                assertThatThrownBy(() -> scope.resolve(TEACHER, new ReportFilter(null, null, id)))
                        .isInstanceOf(AccessDeniedException.class);
            }
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn xem mọi lớp của tổ chuyên môn mình, kể cả lớp người khác dạy; tổ chuyên môn khác → 403")
        void headOnlyOwnDepartment() {
            assertThat(scope.resolve(HEAD, new ReportFilter(null, null, SE_OTHER.getId())).section())
                    .isSameAs(SE_OTHER);

            assertThatThrownBy(() -> scope.resolve(HEAD, new ReportFilter(null, null, IA_OTHER.getId())))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("GV kiêm TBM: hợp của hai phạm vi")
        void teacherAndHeadUnion() {
            AppUserPrincipal both = CatalogTestData.principal(3L,
                    Set.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT),
                    Map.of(RoleCodes.HEAD_OF_DEPARTMENT, Set.of(38L)));

            assertThat(scope.resolve(both, new ReportFilter(null, null, MINE.getId())).section()).isSameAs(MINE);
            assertThat(scope.resolve(both, new ReportFilter(null, null, IA_OTHER.getId())).section())
                    .isSameAs(IA_OTHER);
            assertThatThrownBy(() -> scope.resolve(both, new ReportFilter(null, null, SE_OTHER.getId())))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("ADMIN xem mọi lớp; id không tồn tại → 404")
        void adminAll() {
            assertThat(scope.resolve(ADMIN, new ReportFilter(null, null, IA_OTHER.getId())).section())
                    .isSameAs(IA_OTHER);

            assertThatThrownBy(() -> scope.resolve(ADMIN, new ReportFilter(null, null, 999L)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Có sectionId thì học kỳ và môn lấy theo lớp, bỏ qua semesterId/subjectId trên URL")
        void sectionWins() {
            Context context = scope.resolve(HEAD, new ReportFilter(HK1.getId(), 77L, SE_OTHER.getId()));

            assertThat(context.semester()).isSameAs(HK2);
            assertThat(context.subject()).isSameAs(PRJ);
            assertThat(context.sections()).containsExactly(SE_OTHER);
            verify(scheduleViewService, never()).resolveSemester(any());
            verify(subjectRepository, never()).findById(77L);
        }

        @Test
        @DisplayName("Vai trò không có phạm vi báo cáo (HỌC SINH) → 403 kể cả với lớp có thật")
        void otherRoles() {
            assertThatThrownBy(() -> scope.resolve(STUDENT, new ReportFilter(null, null, MINE.getId())))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> scope.resolve(STUDENT, new ReportFilter(null, null, null)))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Môn học trên tham số subjectId")
    class SubjectFilter {

        @Test
        @DisplayName("Giáo viên chọn môn mình có dạy (ở bất kỳ học kỳ nào); môn khác hoặc không tồn tại → 403")
        void teacherSubjects() {
            when(classSectionRepository.existsBySubjectIdAndTeacherId(PRJ.getId(), ME.getId())).thenReturn(true);

            assertThat(scope.resolve(TEACHER, new ReportFilter(null, PRJ.getId(), null)).subject()).isSameAs(PRJ);
            for (Long id : List.of(SEC.getId(), 999L)) {
                assertThatThrownBy(() -> scope.resolve(TEACHER, new ReportFilter(null, id, null)))
                        .isInstanceOf(AccessDeniedException.class);
            }
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn chọn môn của tổ chuyên môn mình; môn tổ chuyên môn khác → 403")
        void headSubjects() {
            assertThat(scope.resolve(HEAD, new ReportFilter(null, PRJ.getId(), null)).subject()).isSameAs(PRJ);
            assertThatThrownBy(() -> scope.resolve(HEAD, new ReportFilter(null, SEC.getId(), null)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("ADMIN chọn môn không tồn tại → 404")
        void adminMissingSubject() {
            assertThatThrownBy(() -> scope.resolve(ADMIN, new ReportFilter(null, 999L, null)))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("Có môn thì chỉ giữ lớp của môn đó; ô chọn lớp cũng chỉ còn lớp của môn")
        void filtersSections() {
            when(classSectionRepository.findForReport(eq(HK1.getId()), eq(true), any(), any()))
                    .thenReturn(List.of(MINE, IA_OTHER));

            Context context = scope.resolve(ADMIN, new ReportFilter(HK1.getId(), SEC.getId(), null));

            assertThat(context.sections()).containsExactly(IA_OTHER);
            assertThat(context.selection().sections()).extracting("id").containsExactly(IA_OTHER.getId());
            assertThat(context.selection().subjects()).extracting("id").containsExactly(SEC.getId(), PRJ.getId());
        }
    }

    @Nested
    @DisplayName("Danh sách lớp trong học kỳ")
    class Sections {

        @Test
        @DisplayName("Giáo viên: truy vấn theo hồ sơ giáo viên, không mở theo tổ chuyên môn")
        void teacherQuery() {
            scope.resolve(TEACHER, new ReportFilter(HK1.getId(), null, null));

            verify(classSectionRepository).findForReport(eq(HK1.getId()), eq(false), eq(ME.getId()),
                    eq(CatalogSupport.NO_IDS));
        }

        @Test
        @DisplayName("Tổ trưởng chuyên môn chưa có hồ sơ giáo viên: truy vấn theo tổ chuyên môn, teacherId null")
        void headQuery() {
            scope.resolve(HEAD, new ReportFilter(HK1.getId(), null, null));

            verify(classSectionRepository).findForReport(eq(HK1.getId()), eq(false), eq(null), eq(Set.of(1L)));
        }

        @Test
        @DisplayName("Giáo viên chưa có hồ sơ và không là tổ trưởng chuyên môn: danh sách rỗng, không truy vấn")
        void noProfile() {
            AppUserPrincipal teacherWithoutProfile = CatalogTestData.principal(15L, Set.of(RoleCodes.TEACHER),
                    Map.of());

            Context context = scope.resolve(teacherWithoutProfile, new ReportFilter(null, null, null));

            assertThat(context.sections()).isEmpty();
            verify(classSectionRepository, never()).findForReport(anyLong(), anyBoolean(), any(), any());
        }
    }
}
