package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Teacher;
import vn.edu.fpt.contactbook.model.UserRequest;
import vn.edu.fpt.contactbook.model.enums.RequestType;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Người duyệt: lớp học phần → giảng viên lớp; không có lớp → GVCN; không có GVCN → ADMIN; đơn khác → ADMIN.
 */
class RequestReviewerResolverTest {

    static final AppUserPrincipal SECTION_TEACHER = CatalogTestData.principal(3L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal HOMEROOM = CatalogTestData.principal(16L, Set.of(RoleCodes.TEACHER), Map.of());
    static final AppUserPrincipal ADMIN = CatalogTestData.principal(1L, Set.of(RoleCodes.ADMIN), Map.of());

    private final RequestReviewerResolver resolver = new RequestReviewerResolver();
    private Teacher sectionTeacher;
    private Teacher homeroomTeacher;
    private ClassSection section;
    private Student student;

    @BeforeEach
    void setUp() {
        Department se = CatalogTestData.department(1L, "SE");
        sectionTeacher = CatalogTestData.teacher(2L, "GV002", se);
        sectionTeacher.getUser().setId(3L);
        homeroomTeacher = CatalogTestData.teacher(8L, "GV003", se);
        homeroomTeacher.getUser().setId(16L);
        section = CatalogTestData.section(1L, "TOAN10-HK1-2627-01", CatalogTestData.subject(1L, "TOAN10", se),
                CatalogTestData.semester(1L, "HK1-2627"), sectionTeacher, 40, SectionStatus.OPEN);
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setHomeroomTeacher(homeroomTeacher);
        student = CatalogTestData.student(1L, "HS260001", StudentStatus.STUDYING);
        student.setSchoolClass(schoolClass);
    }

    private UserRequest leave(ClassSection classSection) {
        UserRequest request = new UserRequest();
        request.setRequestType(RequestType.LEAVE);
        request.setStudent(student);
        request.setClassSection(classSection);
        return request;
    }

    @Test
    @DisplayName("Đơn nghỉ có lớp môn học: giáo viên lớp đó duyệt, GVCN và ADMIN không")
    void sectionTeacher() {
        UserRequest request = leave(section);

        assertThat(resolver.resolve(request).teacher()).isSameAs(sectionTeacher);
        assertThat(resolver.isReviewer(SECTION_TEACHER, request)).isTrue();
        assertThat(resolver.isReviewer(HOMEROOM, request)).isFalse();
        assertThat(resolver.isReviewer(ADMIN, request)).isFalse();
    }

    @Test
    @DisplayName("Đơn nghỉ không chọn lớp: giáo viên chủ nhiệm duyệt")
    void homeroomTeacher() {
        UserRequest request = leave(null);

        assertThat(resolver.resolve(request).teacher()).isSameAs(homeroomTeacher);
        assertThat(resolver.isReviewer(HOMEROOM, request)).isTrue();
        assertThat(resolver.isReviewer(SECTION_TEACHER, request)).isFalse();
    }

    @Test
    @DisplayName("Không chọn lớp và lớp hành chính không có GVCN (hoặc không có lớp): ADMIN duyệt")
    void adminWhenNoHomeroom() {
        student.getSchoolClass().setHomeroomTeacher(null);
        UserRequest request = leave(null);

        assertThat(resolver.resolve(request).admin()).isTrue();
        assertThat(resolver.isReviewer(ADMIN, request)).isTrue();
        assertThat(resolver.isReviewer(HOMEROOM, request)).isFalse();

        student.setSchoolClass(null);
        assertThat(resolver.resolve(leave(null)).admin()).isTrue();
    }

    @Test
    @DisplayName("Đơn khác luôn do ADMIN xử lý, kể cả khi có GVCN")
    void otherTypesGoToAdmin() {
        for (RequestType type : new RequestType[]{RequestType.TRANSCRIPT, RequestType.CERTIFICATE,
                RequestType.OTHER}) {
            UserRequest request = leave(null);
            request.setRequestType(type);
            assertThat(resolver.resolve(request).admin()).isTrue();
            assertThat(resolver.isReviewer(ADMIN, request)).isTrue();
            assertThat(resolver.isReviewer(HOMEROOM, request)).isFalse();
        }
    }
}
