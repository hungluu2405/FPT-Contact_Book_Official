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
import vn.edu.fpt.contactbook.model.ClassSection;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;
import vn.edu.fpt.contactbook.model.Conversation;
import vn.edu.fpt.contactbook.model.ConversationMember;
import vn.edu.fpt.contactbook.model.ConversationMemberId;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.Faculty;
import vn.edu.fpt.contactbook.model.Message;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.Room;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.model.Student;
import vn.edu.fpt.contactbook.model.Subject;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.ConversationType;
import vn.edu.fpt.contactbook.model.enums.EnrollmentStatus;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.model.enums.SectionStatus;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;
import vn.edu.fpt.contactbook.util.AppTime;
import vn.edu.fpt.contactbook.util.LikePatterns;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    @Nested
    @DisplayName("Lớp học phần và ghi danh (Giai đoạn 4b)")
    class SectionQueries {

        @Autowired
        private ClassSectionRepository classSectionRepository;
        @Autowired
        private EnrollmentRepository enrollmentRepository;
        @Autowired
        private StudentRepository studentRepository;

        @Test
        void searchScopedAndFiltered() {
            assertThat(classSectionRepository.search(null, null, null, true, List.of(1L), PageRequest.of(0, 20)))
                    .extracting(ClassSection::getCode)
                    .containsExactly("SWP391-01-SP27", "PRJ301-SE1901-FA26", "PRN211_FA26_01", "SWP391_FA26_01");
            assertThat(classSectionRepository.search(null, null, null, true, List.of(38L), PageRequest.of(0, 20)))
                    .isEmpty();
            assertThat(classSectionRepository.search(LikePatterns.containsIgnoreCase("java web"), 1L,
                    SectionStatus.OPEN, false, List.of(-1L), PageRequest.of(0, 20)))
                    .extracting(ClassSection::getCode).containsExactly("PRJ301-SE1901-FA26");
        }

        @Test
        void codesByPrefix() {
            assertThat(classSectionRepository.findCodesLike(LikePatterns.startsWith("SWP391_")))
                    .containsExactly("SWP391_FA26_01");
            assertThat(classSectionRepository.findCodesLike(LikePatterns.startsWith("SWP391-"))).hasSize(1);
        }

        @Test
        void lockAndCounts() {
            assertThat(classSectionRepository.findByIdForUpdate(27L)).isPresent();
            assertThat(enrollmentRepository.countByClassSectionIdAndStatus(27L, EnrollmentStatus.ENROLLED))
                    .isEqualTo(2);
            assertThat(enrollmentRepository.countBySections(List.of(1L, 27L, 28L), EnrollmentStatus.ENROLLED))
                    .extracting(row -> row[0] + "=" + row[1]).containsExactlyInAnyOrder("1=1", "27=2", "28=2");
            assertThat(enrollmentRepository.findPageBySection(27L, null, PageRequest.of(0, 20)))
                    .extracting(e -> e.getStudent().getStudentCode()).containsExactly("SE180101", "SE180102");
        }

        @Test
        @DisplayName("Ứng viên ghi danh: SV đang học chưa có trong lớp; SV đã DROPPED vẫn hiện")
        void candidates() {
            assertThat(studentRepository.findCandidates(null, 27L, PageRequest.of(0, 20)))
                    .extracting(Student::getStudentCode).containsExactly("SE180201", "SE190001");

            Enrollment enrollment = enrollmentRepository.findByClassSectionIdAndStudentId(27L, 2L).orElseThrow();
            enrollment.setStatus(EnrollmentStatus.DROPPED);
            enrollmentRepository.flush();
            assertThat(studentRepository.findCandidates(LikePatterns.containsIgnoreCase("se1801"), 27L,
                    PageRequest.of(0, 20))).extracting(Student::getStudentCode).containsExactly("SE180101");
        }
    }

    @Nested
    @DisplayName("Buổi học, lịch thi, xem lịch (Giai đoạn 5a)")
    class ScheduleQueries {

        @Autowired
        private ClassSessionRepository classSessionRepository;
        @Autowired
        private ExamRepository examRepository;
        @Autowired
        private EnrollmentRepository enrollmentRepository;
        @Autowired
        private ClassSectionRepository classSectionRepository;
        @Autowired
        private AttendanceRepository attendanceRepository;

        private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
        private static final LocalDate TO = LocalDate.of(2026, 10, 21);

        @Test
        @DisplayName("Xung đột theo phòng, theo giảng viên, theo lớp chung sinh viên; tham số null bị bỏ qua")
        void possibleConflicts() {
            assertThat(classSessionRepository.findPossibleConflicts(1L, FROM, TO, 14L, null, List.of(-1L)))
                    .extracting(ClassSession::getId).containsExactlyInAnyOrder(41L, 42L, 43L, 44L, 45L, 46L);
            assertThat(classSessionRepository.findPossibleConflicts(1L, FROM, TO, null, 8L, List.of(-1L)))
                    .hasSize(6);
            assertThat(classSessionRepository.findPossibleConflicts(1L, FROM, TO, null, null, List.of(27L)))
                    .hasSize(6);
            assertThat(classSessionRepository.findPossibleConflicts(1L, FROM, TO, null, null, List.of(-1L)))
                    .isEmpty();
            assertThat(classSessionRepository.findPossibleConflicts(27L, FROM, TO, 14L, 8L, List.of(27L)))
                    .as("chính lớp không tính ở truy vấn này").isEmpty();
        }

        @Test
        void sharedStudentsAndSectionIds() {
            assertThat(enrollmentRepository.countSharedStudents(27L))
                    .extracting(row -> row[0] + "=" + row[1]).containsExactlyInAnyOrder("28=1", "37=2");
            assertThat(enrollmentRepository.findEnrolledSectionIds(1L, 1L)).containsExactly(1L);
            assertThat(classSectionRepository.findIdsByTeacherAndSemester(8L, 1L)).containsExactly(27L);
        }

        @Test
        @DisplayName("Buổi của lớp học phần đã hủy không còn chặn phòng, giảng viên, sinh viên (finding 2)")
        void cancelledSectionDoesNotBlock() {
            ClassSection prn211 = classSectionRepository.findById(27L).orElseThrow();
            prn211.setStatus(SectionStatus.CANCELLED);
            classSectionRepository.flush();

            assertThat(classSessionRepository.findPossibleConflicts(1L, FROM, TO, 14L, 8L, List.of(27L))).isEmpty();
        }

        @Test
        void sessionHelpers() {
            assertThat(classSessionRepository.findMaxSessionNo(27L)).isEqualTo((short) 6);
            assertThat(classSessionRepository.findMaxSessionNo(37L)).isNull();
            assertThat(attendanceRepository.findSessionIdsWithAttendance(27L)).containsExactlyInAnyOrder(41L, 42L,
                    43L, 45L);
            assertThat(classSessionRepository.findForSections(List.of(27L), LocalDate.of(2026, 10, 12),
                    LocalDate.of(2026, 10, 18))).extracting(ClassSession::getId).containsExactly(43L, 44L);
            assertThat(examRepository.findPossibleConflicts(FROM, TO, null, null, List.of(-1L))).isEmpty();
        }
    }

    /**
     * Tin nhắn. Dữ liệu mẫu (FA26 là học kỳ hiện tại): lớp 1 do users.id 3 dạy SE190001 (users.id 4, phụ huynh
     * users.id 5); lớp 27 do users.id 16 dạy users.id 18, 19 (phụ huynh 21, 22); lớp 28 do users.id 17 dạy 18, 20.
     */
    @Nested
    @DisplayName("Tin nhắn")
    class MessagingQueries {

        @Autowired
        private MessagingContactRepository contactRepository;
        @Autowired
        private ConversationRepository conversationRepository;
        @Autowired
        private ConversationMemberRepository memberRepository;
        @Autowired
        private MessageRepository messageRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private SemesterRepository semesterRepository;
        @Autowired
        private ClassSectionRepository classSectionRepository;
        @Autowired
        private EnrollmentRepository enrollmentRepository;

        @Test
        @DisplayName("Đang dạy: SV ENROLLED ở lớp học kỳ hiện tại; phụ huynh qua con đã liên kết")
        void teachingNow() {
            assertThat(contactRepository.teachesStudentNow(3L, 4L)).isTrue();
            assertThat(contactRepository.teachesStudentNow(16L, 18L)).isTrue();
            assertThat(contactRepository.teachesStudentNow(2L, 4L)).isFalse();
            assertThat(contactRepository.teachesStudentNow(17L, 19L)).isFalse();
            assertThat(contactRepository.teachesChildNow(3L, 5L)).isTrue();
            assertThat(contactRepository.teachesChildNow(16L, 21L)).isTrue();
            assertThat(contactRepository.teachesChildNow(2L, 5L)).isFalse();
            assertThat(contactRepository.teachesChildNow(17L, 22L)).isFalse();
        }

        @Test
        @DisplayName("Hết học kỳ (học kỳ không còn là hiện tại): không còn quan hệ")
        void semesterEnded() {
            Semester fa26 = semesterRepository.findById(1L).orElseThrow();
            fa26.setCurrent(false);
            semesterRepository.flush();

            assertThat(contactRepository.teachesStudentNow(3L, 4L)).isFalse();
            assertThat(contactRepository.teachesChildNow(3L, 5L)).isFalse();
            assertThat(contactRepository.findContacts(5L, false, false, true, null, PageRequest.of(0, 20))).isEmpty();
        }

        @Test
        @DisplayName("Lớp CANCELLED hoặc sinh viên DROPPED: không còn quan hệ")
        void cancelledOrDropped() {
            Enrollment enrollment = enrollmentRepository.findById(1L).orElseThrow();
            enrollment.setStatus(EnrollmentStatus.DROPPED);
            enrollmentRepository.flush();
            assertThat(contactRepository.teachesStudentNow(3L, 4L)).isFalse();

            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            ClassSection section = classSectionRepository.findById(1L).orElseThrow();
            section.setStatus(SectionStatus.CANCELLED);
            classSectionRepository.flush();
            assertThat(contactRepository.teachesStudentNow(3L, 4L)).isFalse();
            assertThat(contactRepository.teachesChildNow(3L, 5L)).isFalse();
        }

        @Test
        @DisplayName("Danh bạ theo vai trò: PH, SV chỉ thấy GV đang dạy; GV thấy GV khác, SV và PH lớp mình")
        void contacts() {
            PageRequest page = PageRequest.of(0, 20);
            assertThat(contactRepository.findContacts(5L, false, false, true, null, page))
                    .extracting(User::getId).containsExactly(3L);
            assertThat(contactRepository.findContacts(4L, false, true, false, null, page))
                    .extracting(User::getId).containsExactly(3L);
            assertThat(contactRepository.findContacts(3L, true, false, false, null, page))
                    .extracting(User::getId).containsExactlyInAnyOrder(2L, 4L, 5L, 15L, 16L, 17L);
            assertThat(contactRepository.findContacts(1L, false, false, false, null, page)).isEmpty();
            assertThat(contactRepository.findContacts(3L, true, false, false,
                    LikePatterns.containsIgnoreCase("0900000004"), page)).extracting(User::getId).containsExactly(4L);
        }

        @Test
        @DisplayName("Danh bạ bỏ tài khoản bị khóa")
        void contactsSkipInactive() {
            User teacher = userRepository.findById(3L).orElseThrow();
            teacher.setActive(false);
            userRepository.flush();

            assertThat(contactRepository.findContacts(5L, false, false, true, null, PageRequest.of(0, 20))).isEmpty();
        }

        @Test
        @DisplayName("Hội thoại DIRECT, trang hội thoại, đếm chưa đọc theo last_read_at (bỏ tin đã xóa, tin của mình)")
        void conversationsAndUnread() {
            LocalDateTime now = AppTime.now().truncatedTo(ChronoUnit.SECONDS);
            Conversation conversation = new Conversation();
            conversation.setType(ConversationType.DIRECT);
            conversation.setCreatedBy(userRepository.getReferenceById(5L));
            conversationRepository.save(conversation);
            ConversationMember parent = member(conversation, 5L);
            memberRepository.saveAll(List.of(parent, member(conversation, 3L)));
            message(conversation, 3L, now.minusMinutes(10), false);
            message(conversation, 3L, now.minusMinutes(5), true);
            message(conversation, 3L, now.minusMinutes(1), false);
            Message last = message(conversation, 5L, now, false);
            messageRepository.flush();

            assertThat(conversationRepository.findDirectIds(5L, 3L)).containsExactly(conversation.getId());
            assertThat(conversationRepository.findDirectIds(3L, 5L)).containsExactly(conversation.getId());
            assertThat(conversationRepository.findDirectIds(5L, 2L)).isEmpty();
            Page<Object[]> rows = conversationRepository.findPageWithLastMessage(5L, PageRequest.of(0, 20));
            assertThat(rows.getTotalElements()).isEqualTo(1);
            assertThat(rows.getContent().getFirst()).containsExactly(conversation.getId(), last.getId());
            assertThat(conversationRepository.findPageWithLastMessage(2L, PageRequest.of(0, 20))).isEmpty();

            assertThat(messageRepository.countUnread(5L)).isEqualTo(2);
            assertThat(messageRepository.countUnread(3L)).isEqualTo(1);
            ConversationMember managed = memberRepository.findById(parent.getId()).orElseThrow();
            managed.setLastReadAt(now.minusMinutes(3));
            memberRepository.flush();
            assertThat(messageRepository.countUnread(5L)).isEqualTo(1);
            assertThat(messageRepository.countUnreadByConversation(5L, List.of(conversation.getId())))
                    .extracting(r -> r[0] + "=" + r[1]).containsExactly(conversation.getId() + "=1");
            managed.setLastReadAt(now);
            memberRepository.flush();
            assertThat(messageRepository.countUnread(5L)).isZero();
            assertThat(messageRepository.countUnreadByConversation(5L, List.of(conversation.getId()))).isEmpty();
        }

        private ConversationMember member(Conversation conversation, Long userId) {
            ConversationMember member = new ConversationMember();
            member.setId(new ConversationMemberId(conversation.getId(), userId));
            member.setConversation(conversation);
            member.setUser(userRepository.getReferenceById(userId));
            return member;
        }

        private Message message(Conversation conversation, Long senderId, LocalDateTime sentAt, boolean deleted) {
            Message message = new Message();
            message.setConversation(conversation);
            message.setSender(userRepository.getReferenceById(senderId));
            message.setContent("Tin " + sentAt);
            message.setSentAt(sentAt);
            message.setDeleted(deleted);
            return messageRepository.save(message);
        }
    }

    /**
     * Câu lạc bộ. Dữ liệu mẫu: CLB 1 F-CODE (chủ nhiệm users.id 18, thành viên 19, 20 đang chờ duyệt), CLB 2 F-GUITAR
     * (chủ nhiệm 20). Chưa có hoạt động nào; các hoạt động dưới đây tạo trong test và rollback.
     */
    @Nested
    @DisplayName("Câu lạc bộ")
    class ClubQueries {

        @Autowired
        private ClubRepository clubRepository;
        @Autowired
        private ClubMemberRepository memberRepository;
        @Autowired
        private ClubActivityRepository activityRepository;
        @Autowired
        private ClubActivityParticipantRepository participantRepository;
        @Autowired
        private UserRepository userRepository;
        @Autowired
        private UserRoleRepository userRoleRepository;

        @Test
        @DisplayName("Tìm CLB theo từ khóa và trạng thái; số thành viên ACTIVE và chủ nhiệm của nhiều CLB")
        void clubsAndCounts() {
            PageRequest page = PageRequest.of(0, 20);
            assertThat(clubRepository.search(null, ClubStatus.ACTIVE, page)).extracting(Club::getCode)
                    .containsExactlyInAnyOrder("F-CODE", "F-GUITAR");
            assertThat(clubRepository.search(LikePatterns.containsIgnoreCase("guitar"), null, page))
                    .extracting(Club::getCode).containsExactly("F-GUITAR");
            assertThat(clubRepository.search(null, ClubStatus.INACTIVE, page)).isEmpty();
            assertThat(memberRepository.countActiveByClubs(List.of(1L, 2L)))
                    .extracting(r -> r[0] + "=" + r[1]).containsExactlyInAnyOrder("1=2", "2=1");
            assertThat(memberRepository.findPresidentsOf(List.of(1L, 2L)))
                    .extracting(m -> m.getUser().getId()).containsExactlyInAnyOrder(18L, 20L);
        }

        @Test
        @DisplayName("Thành viên ACTIVE: chủ nhiệm trước; CLB của một người theo trạng thái")
        void membersAndMyClubs() {
            assertThat(memberRepository.findPageByClub(1L, ClubMemberStatus.ACTIVE, PageRequest.of(0, 20)))
                    .extracting(m -> m.getUser().getId()).containsExactly(18L, 19L);
            assertThat(memberRepository.findPageByClub(1L, ClubMemberStatus.PENDING, PageRequest.of(0, 20)))
                    .extracting(m -> m.getUser().getId()).containsExactly(20L);
            assertThat(memberRepository.findByUser(20L, List.of(ClubMemberStatus.PENDING, ClubMemberStatus.ACTIVE)))
                    .extracting(m -> m.getClub().getCode() + ":" + m.getStatus())
                    .containsExactlyInAnyOrder("F-CODE:PENDING", "F-GUITAR:ACTIVE");
        }

        @Test
        @DisplayName("Giảng viên phụ trách chọn được: TEACHER / HEAD đang hoạt động")
        void advisors() {
            List<String> roles = List.of(RoleCodes.TEACHER, RoleCodes.HEAD_OF_DEPARTMENT);
            assertThat(userRoleRepository.findActiveUsersWithRole(roles)).extracting(User::getId)
                    .containsExactlyInAnyOrder(2L, 3L, 15L, 16L, 17L);
            assertThat(userRoleRepository.isActiveWithRole(16L, roles)).isTrue();
            assertThat(userRoleRepository.isActiveWithRole(4L, roles)).isFalse();
            User teacher = userRepository.findById(16L).orElseThrow();
            teacher.setActive(false);
            userRepository.flush();
            assertThat(userRoleRepository.isActiveWithRole(16L, roles)).isFalse();
        }

        @Test
        @DisplayName("Sĩ số đếm REGISTERED + ATTENDED; hoạt động sắp tới bỏ đã hủy và đã kết thúc")
        void activities() {
            LocalDateTime now = AppTime.now().truncatedTo(ChronoUnit.SECONDS);
            ClubActivity future = activity(now.plusDays(1), ClubActivityStatus.PLANNED);
            ClubActivity ongoing = activity(now.minusHours(1), ClubActivityStatus.PLANNED);
            activity(now.plusDays(2), ClubActivityStatus.CANCELLED);
            activity(now.minusDays(2), ClubActivityStatus.PLANNED);
            participant(future, 18L, ParticipantStatus.REGISTERED);
            participant(future, 19L, ParticipantStatus.CANCELLED);
            participant(ongoing, 19L, ParticipantStatus.ATTENDED);
            participantRepository.flush();

            assertThat(activityRepository.findUpcoming(1L, now)).extracting(ClubActivity::getId)
                    .containsExactly(ongoing.getId(), future.getId());
            assertThat(participantRepository.countTaken(future.getId())).isEqualTo(1);
            assertThat(participantRepository.countTakenByActivities(List.of(future.getId(), ongoing.getId())))
                    .extracting(r -> r[0] + "=" + r[1])
                    .containsExactlyInAnyOrder(future.getId() + "=1", ongoing.getId() + "=1");
            assertThat(participantRepository.findTaken(future.getId())).extracting(p -> p.getUser().getId())
                    .containsExactly(18L);
        }

        @Test
        @DisplayName("Rời CLB chỉ hủy đăng ký REGISTERED của hoạt động chưa bắt đầu, của đúng CLB đó")
        void cancelFutureRegistrations() {
            LocalDateTime now = AppTime.now().truncatedTo(ChronoUnit.SECONDS);
            ClubActivity future = activity(now.plusDays(1), ClubActivityStatus.PLANNED);
            ClubActivity started = activity(now.minusHours(1), ClubActivityStatus.PLANNED);
            participant(future, 19L, ParticipantStatus.REGISTERED);
            participant(started, 19L, ParticipantStatus.REGISTERED);
            participant(future, 18L, ParticipantStatus.REGISTERED);
            participantRepository.flush();

            assertThat(participantRepository.cancelFutureRegistrations(1L, 19L, now)).isEqualTo(1);
            assertThat(participantRepository.cancelFutureRegistrations(2L, 18L, now)).isZero();
            assertThat(status(future, 19L)).isEqualTo(ParticipantStatus.CANCELLED);
            assertThat(status(started, 19L)).isEqualTo(ParticipantStatus.REGISTERED);
            assertThat(status(future, 18L)).isEqualTo(ParticipantStatus.REGISTERED);
        }

        private ClubActivity activity(LocalDateTime start, ClubActivityStatus status) {
            ClubActivity activity = new ClubActivity();
            activity.setClub(clubRepository.getReferenceById(1L));
            activity.setTitle("Hoạt động " + start);
            activity.setStartTime(start);
            activity.setEndTime(start.plusHours(3));
            activity.setStatus(status);
            activity.setCreatedBy(userRepository.getReferenceById(18L));
            return activityRepository.saveAndFlush(activity);
        }

        private void participant(ClubActivity activity, Long userId, ParticipantStatus status) {
            ClubActivityParticipant participant = new ClubActivityParticipant();
            participant.setId(new ClubActivityParticipantId(activity.getId(), userId));
            participant.setActivity(activity);
            participant.setUser(userRepository.getReferenceById(userId));
            participant.setStatus(status);
            participantRepository.save(participant);
        }

        private ParticipantStatus status(ClubActivity activity, Long userId) {
            return participantRepository.findById(new ClubActivityParticipantId(activity.getId(), userId))
                    .map(ClubActivityParticipant::getStatus).orElseThrow();
        }
    }
}
