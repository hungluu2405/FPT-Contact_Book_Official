package vn.edu.fpt.contactbook.repository;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.fpt.contactbook.dto.UserListRow;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;
import vn.edu.fpt.contactbook.util.LikePatterns;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Truy vấn tự viết của Giai đoạn 1, chạy trên dữ liệu mẫu của script (CLAUDE.md mục 10).
 */
@ExtendWith(TestDatabaseExtension.class)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryQueryTest {

    private static final String ADMIN_PHONE = "0900000001";

    @Nested
    @DisplayName("UserRepository")
    class UserQueries {

        @Autowired
        private UserRepository userRepository;

        @Test
        void findByPhoneReturnsUser() {
            assertThat(userRepository.findByPhone(ADMIN_PHONE))
                    .get()
                    .extracting(User::getEmail)
                    .isEqualTo("admin@fpt.edu.vn");
        }

        @Test
        void findByPhoneReturnsEmptyForUnknownPhone() {
            assertThat(userRepository.findByPhone("0999999999")).isEmpty();
        }

        @Test
        void existsByPhone() {
            assertThat(userRepository.existsByPhone(ADMIN_PHONE)).isTrue();
            assertThat(userRepository.existsByPhone("0999999999")).isFalse();
        }

        @Test
        void existsByEmailIgnoresCase() {
            assertThat(userRepository.existsByEmailIgnoreCase("ADMIN@FPT.EDU.VN")).isTrue();
            assertThat(userRepository.existsByEmailIgnoreCase("nobody@fpt.edu.vn")).isFalse();
        }

        @Test
        void searchByPhoneFragmentIsPaged() {
            Page<UserListRow> page = search("090000000", null, null, null, null, PageRequest.of(0, 2));

            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getTotalPages()).isEqualTo(3);
            assertThat(page.getContent()).hasSize(2).allMatch(row -> row.phone().startsWith("090000000"));
        }

        @Test
        void searchByNameIgnoresCase() {
            assertThat(phones(search("TRƯỞNG"))).containsExactly("0900000002");
        }

        @Test
        void searchByStudentAndTeacherCode() {
            assertThat(phones(search("se190001"))).containsExactly("0900000004");
            assertThat(phones(search("gv002"))).containsExactly("0900000003");
        }

        @Test
        void searchByPhoneInOtherFormats() {
            assertThat(phones(search("+84900000001"))).containsExactly(ADMIN_PHONE);
        }

        @Test
        @DisplayName("Ký tự đại diện LIKE (_ % [) trong từ khóa được coi là ký tự thường")
        void wildcardsInKeywordAreLiteral() {
            assertThat(search("_").getContent()).isEmpty();
            assertThat(search("%").getContent()).isEmpty();
            assertThat(search("[0]").getContent()).isEmpty();
        }

        @Test
        void searchWithBlankOrNullKeywordReturnsAll() {
            long total = userRepository.count();

            assertThat(search("").getTotalElements()).isEqualTo(total);
            assertThat(search(null).getTotalElements()).isEqualTo(total);
        }

        @Test
        void searchWithoutMatchReturnsEmptyPage() {
            assertThat(search("khong-ton-tai")).isEmpty();
        }

        @Test
        @DisplayName("Lọc theo vai trò, bộ môn giảng viên, lớp, trạng thái")
        void filters() {
            assertThat(search(null, "TEACHER", null, null, null, PageRequest.of(0, 50)).getTotalElements())
                    .isEqualTo(5);
            assertThat(phones(search(null, null, 1L, null, null, PageRequest.of(0, 50))))
                    .containsExactlyInAnyOrder("0900000002", "0900000003", "0922334455", "0922334456");
            assertThat(phones(search(null, null, null, 1L, null, PageRequest.of(0, 50))))
                    .containsExactly("0900000004");
            assertThat(search(null, null, null, null, false, PageRequest.of(0, 50))).isEmpty();
        }

        @Test
        @DisplayName("Phạm vi trưởng bộ môn: chỉ giảng viên thuộc các bộ môn được giao")
        void scopedToDepartments() {
            Page<UserListRow> se = userRepository.search(null, null, null, null, null, null,
                    true, List.of(1L), PageRequest.of(0, 50));
            Page<UserListRow> ia = userRepository.search(null, null, null, null, null, null,
                    true, List.of(38L), PageRequest.of(0, 50));

            assertThat(se.getContent()).extracting(UserListRow::teacherCode)
                    .containsExactlyInAnyOrder("GV001", "GV002", "GV003", "GV004");
            assertThat(ia).isEmpty();
        }

        private Page<UserListRow> search(String keyword) {
            return search(keyword, null, null, null, null, PageRequest.of(0, 50));
        }

        private Page<UserListRow> search(String keyword, String role, Long departmentId, Long classId,
                                         Boolean active, PageRequest page) {
            return userRepository.search(LikePatterns.containsIgnoreCase(keyword),
                    PhoneUtils.normalize(keyword).orElse(null), role, departmentId, classId, active,
                    false, List.of(-1L), page);
        }

        private List<String> phones(Page<UserListRow> page) {
            return page.getContent().stream().map(UserListRow::phone).toList();
        }
    }

    @Nested
    @DisplayName("UserRoleRepository")
    class UserRoleQueries {

        @Autowired
        private UserRoleRepository userRoleRepository;

        @Test
        void findByUserIdLoadsRoleAndDepartment() {
            List<UserRole> roles = userRoleRepository.findByUserId(2L);

            assertThat(roles).hasSize(2);
            assertThat(roles).allSatisfy(userRole -> {
                assertThat(Hibernate.isInitialized(userRole.getRole())).isTrue();
                assertThat(Hibernate.isInitialized(userRole.getDepartment())).isTrue();
                assertThat(userRole.getDepartment().getCode()).isEqualTo("SE");
            });
            assertThat(roles).extracting(userRole -> userRole.getRole().getCode())
                    .containsExactlyInAnyOrder(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT);
        }

        @Test
        void findByUserIdKeepsNullDepartment() {
            List<UserRole> roles = userRoleRepository.findByUserId(1L);

            assertThat(roles).singleElement().satisfies(userRole -> {
                assertThat(userRole.getRole().getCode()).isEqualTo(RoleCodes.ADMIN);
                assertThat(userRole.getDepartment()).isNull();
            });
        }

        @Test
        void findByUserIdReturnsEmptyForUnknownUser() {
            assertThat(userRoleRepository.findByUserId(999_999L)).isEmpty();
        }

        @Test
        void existsByUserIdAndRoleId() {
            assertThat(userRoleRepository.existsByUserIdAndRoleId(2L, 2L)).isTrue();
            assertThat(userRoleRepository.existsByUserIdAndRoleId(4L, 1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("ParentStudentRepository")
    class ParentStudentQueries {

        @Autowired
        private ParentStudentRepository parentStudentRepository;

        @Test
        void childrenOfParent() {
            List<ParentStudent> links = parentStudentRepository.findByIdParentId(1L);

            assertThat(links).singleElement().satisfies(link -> {
                assertThat(link.getRelationship()).isEqualTo(ParentRelationship.FATHER);
                assertThat(Hibernate.isInitialized(link.getStudent().getUser())).isTrue();
                assertThat(link.getStudent().getStudentCode()).isEqualTo("SE190001");
                assertThat(link.getStudent().getUser().getPhone()).isEqualTo("0900000004");
            });
        }

        @Test
        void parentsOfStudent() {
            List<ParentStudent> links = parentStudentRepository.findByIdStudentId(1L);

            assertThat(links).singleElement().satisfies(link -> {
                assertThat(Hibernate.isInitialized(link.getParent().getUser())).isTrue();
                assertThat(link.getParent().getUser().getPhone()).isEqualTo("0900000005");
            });
        }

        @Test
        void unknownIdsReturnEmpty() {
            assertThat(parentStudentRepository.findByIdParentId(999_999L)).isEmpty();
            assertThat(parentStudentRepository.findByIdStudentId(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("EnrollmentRepository")
    class EnrollmentQueries {

        @Autowired
        private EnrollmentRepository enrollmentRepository;

        @Test
        void byClassSection() {
            List<Enrollment> enrollments = enrollmentRepository.findByClassSectionId(27L);

            assertThat(enrollments).extracting(enrollment -> enrollment.getStudent().getStudentCode())
                    .containsExactlyInAnyOrder("SE180101", "SE180102");
            assertThat(enrollments).allMatch(enrollment -> Hibernate.isInitialized(enrollment.getStudent().getUser()));
        }

        @Test
        void byStudent() {
            List<Enrollment> enrollments = enrollmentRepository.findByStudentId(2L);

            assertThat(enrollments).extracting(enrollment -> enrollment.getClassSection().getCode())
                    .containsExactlyInAnyOrder("PRN211_FA26_01", "SWP391_FA26_01", "SWP391-01-SP27");
            assertThat(enrollments)
                    .allMatch(enrollment -> Hibernate.isInitialized(enrollment.getClassSection().getSubject()));
        }

        @Test
        void unknownIdsReturnEmpty() {
            assertThat(enrollmentRepository.findByClassSectionId(999_999L)).isEmpty();
            assertThat(enrollmentRepository.findByStudentId(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ClassSessionRepository")
    class ClassSessionQueries {

        @Autowired
        private ClassSessionRepository classSessionRepository;

        @Test
        void byClassSectionOrderedBySessionNo() {
            assertThat(classSessionRepository.findByClassSectionIdOrderBySessionNoAsc(27L))
                    .extracting(ClassSession::getSessionNo)
                    .containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6);
        }

        @Test
        void byDateRangeIncludesBothEnds() {
            List<ClassSession> sessions = classSessionRepository
                    .findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                            LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 14));

            assertThat(sessions).extracting(ClassSession::getSessionDate).containsExactly(
                    LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14));
        }

        @Test
        void byDateRangeWithoutSessionsReturnsEmpty() {
            assertThat(classSessionRepository.findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                    LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Danh mục (Giai đoạn 4a)")
    class CatalogQueries {

        @Autowired
        private FacultyRepository facultyRepository;
        @Autowired
        private DepartmentRepository departmentRepository;
        @Autowired
        private SchoolClassRepository schoolClassRepository;
        @Autowired
        private SubjectRepository subjectRepository;
        @Autowired
        private SemesterRepository semesterRepository;
        @Autowired
        private RoomRepository roomRepository;
        @Autowired
        private GradeRepository gradeRepository;
        @Autowired
        private StudentRepository studentRepository;
        @Autowired
        private ClassSectionRepository classSectionRepository;
        @Autowired
        private TeacherRepository teacherRepository;

        @Test
        void searchByKeywordOnCodeOrName() {
            assertThat(facultyRepository.search(LikePatterns.containsIgnoreCase("it"), PageRequest.of(0, 20)))
                    .extracting(Faculty::getCode).contains("IT");
            assertThat(facultyRepository.search(null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
            assertThat(roomRepository.search(LikePatterns.containsIgnoreCase("lab"), PageRequest.of(0, 20)))
                    .extracting(Room::getCode).containsExactly("LAB-401");
        }

        @Test
        @DisplayName("Ký tự đại diện trong từ khóa được coi là ký tự thường")
        void wildcardsAreLiteral() {
            assertThat(roomRepository.search(LikePatterns.containsIgnoreCase("_"), PageRequest.of(0, 20))).isEmpty();
            assertThat(roomRepository.search(LikePatterns.containsIgnoreCase("-"), PageRequest.of(0, 20)))
                    .extracting(Room::getCode).contains("BE-301", "LAB-401").doesNotContain("R101");
        }

        @Test
        void filterByGroup() {
            Page<Department> itDepartments = departmentRepository.search(null, 1L, PageRequest.of(0, 20));
            assertThat(itDepartments).extracting(Department::getCode).containsExactly("AI", "IA", "SE");
            assertThat(itDepartments.getContent().getFirst().getFaculty().getCode()).isEqualTo("IT");

            assertThat(schoolClassRepository.search(null, 1L, PageRequest.of(0, 20)))
                    .extracting(SchoolClass::getCode).containsExactly("SE1801", "SE1802", "SE1901");
        }

        @Test
        @DisplayName("Môn học: lọc theo phạm vi bộ môn của trưởng bộ môn")
        void subjectSearchScoped() {
            assertThat(subjectRepository.search(null, null, true, List.of(1L), PageRequest.of(0, 20)))
                    .extracting(Subject::getCode).containsExactly("PRJ301", "PRN211", "SWP391");
            assertThat(subjectRepository.search(null, null, true, List.of(38L), PageRequest.of(0, 20))).isEmpty();
            assertThat(subjectRepository.search(LikePatterns.containsIgnoreCase("swp"), null, false, List.of(-1L),
                    PageRequest.of(0, 20))).extracting(Subject::getCode).containsExactly("SWP391");
        }

        @Test
        void currentSemesterAndOrdering() {
            assertThat(semesterRepository.findByCurrentTrue()).get().extracting(Semester::getCode).isEqualTo("FA26");
            assertThat(semesterRepository.search(null, PageRequest.of(0, 20)))
                    .extracting(Semester::getCode).containsExactly("SP27", "FA26", "SU26");
        }

        @Test
        void usageChecks() {
            assertThat(gradeRepository.findComponentIdsWithGrades(31L)).containsExactlyInAnyOrder(10L, 14L);
            assertThat(gradeRepository.findComponentIdsWithGrades(1L)).isEmpty();
            assertThat(gradeRepository.existsByComponentId(10L)).isTrue();
            assertThat(gradeRepository.existsByComponentId(11L)).isFalse();
            assertThat(studentRepository.existsBySchoolClassId(1L)).isTrue();
            assertThat(classSectionRepository.existsBySubjectId(1L)).isTrue();
            assertThat(teacherRepository.findByTeacherCode("GV001")).get()
                    .extracting(teacher -> teacher.getUser().getId()).isEqualTo(2L);
        }

        @Test
        void codeExistence() {
            assertThat(subjectRepository.existsByCode("PRJ301")).isTrue();
            assertThat(subjectRepository.existsByCodeAndIdNot("PRJ301", 1L)).isFalse();
            assertThat(subjectRepository.existsByCodeAndIdNot("PRJ301", 31L)).isTrue();
        }
    }
}
