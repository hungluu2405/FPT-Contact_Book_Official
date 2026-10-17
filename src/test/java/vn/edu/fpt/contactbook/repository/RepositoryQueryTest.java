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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.fpt.contactbook.model.ClassSession;
import vn.edu.fpt.contactbook.model.Enrollment;
import vn.edu.fpt.contactbook.model.ParentStudent;
import vn.edu.fpt.contactbook.model.User;
import vn.edu.fpt.contactbook.model.UserRole;
import vn.edu.fpt.contactbook.model.enums.ParentRelationship;
import vn.edu.fpt.contactbook.model.enums.RoleCodes;
import vn.edu.fpt.contactbook.support.TestDatabaseExtension;

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
            Page<User> page = userRepository.search("090000000", PageRequest.of(0, 2, Sort.by("phone")));

            assertThat(page.getTotalElements()).isEqualTo(5);
            assertThat(page.getTotalPages()).isEqualTo(3);
            assertThat(page.getContent()).extracting(User::getPhone).containsExactly(ADMIN_PHONE, "0900000002");
        }

        @Test
        void searchByNameAndEmailIgnoresCase() {
            assertThat(userRepository.search("TRƯỞNG", PageRequest.of(0, 10)).getContent())
                    .extracting(User::getPhone)
                    .containsExactly("0900000002");
            assertThat(userRepository.search("Student@FPT", PageRequest.of(0, 10)).getContent())
                    .extracting(User::getPhone)
                    .containsExactly("0900000004");
        }

        @Test
        void searchWithBlankOrNullKeywordReturnsAll() {
            long total = userRepository.count();

            assertThat(userRepository.search("", PageRequest.of(0, 1)).getTotalElements()).isEqualTo(total);
            assertThat(userRepository.search(null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(total);
        }

        @Test
        void searchWithoutMatchReturnsEmptyPage() {
            assertThat(userRepository.search("khong-ton-tai", PageRequest.of(0, 10))).isEmpty();
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
}
