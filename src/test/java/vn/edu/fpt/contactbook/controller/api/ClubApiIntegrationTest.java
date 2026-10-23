package vn.edu.fpt.contactbook.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.UserRepository;
import vn.edu.fpt.contactbook.support.ApiClient;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static vn.edu.fpt.contactbook.support.ApiClient.fieldNames;

/**
 * API câu lạc bộ chỉ đọc (giai đoạn 10b-4). Dữ liệu mẫu: CLB 1 F-CODE (chủ nhiệm HS250101 = users.id 18, thành viên
 * users.id 19, chờ duyệt users.id 20), CLB 2 F-GUITAR (chủ nhiệm users.id 20); HS260001 (0900000004) chưa tham gia
 * CLB nào, phụ huynh 0900000005 chỉ liên kết với students.id 1.
 */
@IntegrationTest
class ClubApiIntegrationTest {

    /** Chủ nhiệm CLB F-CODE (HS250101). */
    static final String PRESIDENT = "0933112233";
    /** Học sinh HS240201 (users.id 20): chờ duyệt ở F-CODE, chủ nhiệm F-GUITAR. */
    static final String PENDING_STUDENT = "0933112235";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired ClubRepository clubRepository;
    @Autowired ClubMemberRepository memberRepository;
    @Autowired ClubActivityRepository activityRepository;
    @Autowired UserRepository userRepository;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mockMvc, jsonMapper);
    }

    @Nested
    @DisplayName("Phân quyền")
    class Access {

        @Test
        @DisplayName("Không có token: 401")
        void anonymousIsUnauthorized() throws Exception {
            for (String url : List.of("/api/v1/clubs", "/api/v1/clubs/1", "/api/v1/clubs/1/members",
                    "/api/v1/clubs/1/activities", "/api/v1/student/clubs", "/api/v1/parent/children/1/clubs")) {
                mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            }
        }

        @Test
        @DisplayName("Danh sách và chi tiết CLB mở cho mọi vai trò")
        void everyRoleReadsClubs() throws Exception {
            for (String phone : List.of(TestAccounts.ADMIN, TestAccounts.TEACHER, TestAccounts.STUDENT,
                    TestAccounts.PARENT)) {
                api.perform(get("/api/v1/clubs"), phone).andExpect(status().isOk());
                api.perform(get("/api/v1/clubs/1"), phone).andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("CLB của tôi chỉ cho STUDENT, CLB của con chỉ cho PARENT")
        void myClubsByRole() throws Exception {
            for (String phone : List.of(TestAccounts.PARENT, TestAccounts.TEACHER, TestAccounts.ADMIN)) {
                api.perform(get("/api/v1/student/clubs"), phone).andExpect(status().isForbidden());
            }
            for (String phone : List.of(TestAccounts.STUDENT, TestAccounts.TEACHER)) {
                api.perform(get("/api/v1/parent/children/1/clubs"), phone).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Phụ huynh xem con chưa liên kết: 403 FORBIDDEN")
        void parentCannotReadOtherStudent() throws Exception {
            JsonNode error = api.json(api.perform(get("/api/v1/parent/children/2/clubs"), TestAccounts.PARENT)
                    .andExpect(status().isForbidden()));

            assertThat(error.get("code").asString()).isEqualTo("FORBIDDEN");
        }

        @Test
        @DisplayName("CLB không tồn tại: 404 NOT_FOUND")
        void unknownClub() throws Exception {
            for (String url : List.of("/api/v1/clubs/999999", "/api/v1/clubs/999999/members",
                    "/api/v1/clubs/999999/activities")) {
                JsonNode error = api.json(api.perform(get(url), TestAccounts.STUDENT)
                        .andExpect(status().isNotFound()));
                assertThat(error.get("code").asString()).isEqualTo("NOT_FOUND");
            }
        }
    }

    @Nested
    @DisplayName("Dữ liệu")
    class Data {

        @Test
        @DisplayName("Danh sách CLB đang hoạt động, tìm theo tên; không lộ logo và id người phụ trách")
        void listAndSearch() throws Exception {
            JsonNode page = api.json(api.perform(get("/api/v1/clubs"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));

            assertThat(page.get("totalElements").asInt()).isEqualTo(2);
            JsonNode code = page.get("items").get(0);
            assertThat(fieldNames(code)).containsExactlyInAnyOrder("id", "code", "name", "description",
                    "foundedDate", "advisorName", "status", "memberCount", "presidentName");

            JsonNode found = api.json(api.perform(get("/api/v1/clubs").param("q", "guitar"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(found.get("items")).hasSize(1);
            assertThat(found.get("items").get(0).get("code").asString()).isEqualTo("F-GUITAR");
        }

        @Test
        @DisplayName("CLB ngừng hoạt động không có trong danh sách nhưng chi tiết vẫn xem được")
        void inactiveClub() throws Exception {
            clubRepository.findById(2L).orElseThrow().setStatus(ClubStatus.INACTIVE);
            clubRepository.flush();

            JsonNode page = api.json(api.perform(get("/api/v1/clubs"), TestAccounts.STUDENT));
            JsonNode detail = api.json(api.perform(get("/api/v1/clubs/2"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));

            assertThat(page.get("totalElements").asInt()).isEqualTo(1);
            assertThat(detail.get("club").get("status").asString()).isEqualTo("INACTIVE");
        }

        @Test
        @DisplayName("Chi tiết kèm quan hệ của người xem; người ngoài CLB thì null")
        void detailWithMyStatus() throws Exception {
            JsonNode president = api.json(api.perform(get("/api/v1/clubs/1"), PRESIDENT)
                    .andExpect(status().isOk()));
            JsonNode outsider = api.json(api.perform(get("/api/v1/clubs/1"), TestAccounts.STUDENT));

            assertThat(fieldNames(president)).containsExactlyInAnyOrder("club", "myStatus", "myRole");
            assertThat(president.get("myStatus").asString()).isEqualTo("ACTIVE");
            assertThat(president.get("myRole").asString()).isEqualTo("PRESIDENT");
            assertThat(president.get("club").get("memberCount").asInt()).isEqualTo(2);
            assertThat(outsider.get("myStatus").isNull()).isTrue();
        }

        @Test
        @DisplayName("Thành viên: chỉ người đang tham gia, chủ nhiệm trước, chỉ họ tên và vai trò")
        void members() throws Exception {
            JsonNode page = api.json(api.perform(get("/api/v1/clubs/1/members"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));

            assertThat(page.get("totalElements").asInt()).isEqualTo(2);
            JsonNode first = page.get("items").get(0);
            assertThat(fieldNames(first)).containsExactlyInAnyOrder("fullName", "role", "joinedAt");
            assertThat(first.get("role").asString()).isEqualTo("PRESIDENT");
        }

        @Test
        @DisplayName("Hoạt động sắp tới kèm số chỗ đã đăng ký; không lộ cờ quản lý")
        void upcomingActivities() throws Exception {
            ClubActivity activity = new ClubActivity();
            activity.setClub(clubRepository.findById(1L).orElseThrow());
            activity.setTitle("Luyện thuật toán");
            activity.setLocation("LAB-401");
            activity.setStartTime(AppTime.now().plusDays(3));
            activity.setEndTime(AppTime.now().plusDays(3).plusHours(2));
            activity.setMaxParticipants((short) 30);
            activity.setCreatedBy(userRepository.getReferenceById(18L));
            activityRepository.saveAndFlush(activity);

            JsonNode list = api.json(api.perform(get("/api/v1/clubs/1/activities"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));

            assertThat(list).hasSize(1);
            JsonNode item = list.get(0);
            assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "title", "description", "location",
                    "startTime", "endTime", "maxParticipants", "taken", "state", "myStatus");
            assertThat(item.get("taken").asInt()).isZero();
            assertThat(item.get("state").asString()).isEqualTo("PLANNED");
        }

        @Test
        @DisplayName("CLB của tôi: chờ duyệt và đang tham gia; HS chưa tham gia thì rỗng")
        void myClubs() throws Exception {
            JsonNode mine = api.json(api.perform(get("/api/v1/student/clubs"), PENDING_STUDENT)
                    .andExpect(status().isOk()));
            JsonNode none = api.json(api.perform(get("/api/v1/student/clubs"), TestAccounts.STUDENT)
                    .andExpect(status().isOk()));

            assertThat(mine).hasSize(2);
            assertThat(fieldNames(mine.get(0))).containsExactlyInAnyOrder("clubId", "code", "name", "clubStatus",
                    "role", "status");
            assertThat(mine.valueStream().map(row -> row.get("status").asString()))
                    .containsExactlyInAnyOrder("PENDING", "ACTIVE");
            assertThat(none).isEmpty();
        }

        @Test
        @DisplayName("CLB của con: phụ huynh thấy đúng CLB của học sinh đã liên kết")
        void childClubs() throws Exception {
            JsonNode before = api.json(api.perform(get("/api/v1/parent/children/1/clubs"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(before).isEmpty();

            ClubMember row = new ClubMember();
            row.setClub(clubRepository.findById(2L).orElseThrow());
            row.setUser(userRepository.getReferenceById(TestAccounts.STUDENT_ID));
            row.setStatus(ClubMemberStatus.PENDING);
            memberRepository.saveAndFlush(row);

            JsonNode after = api.json(api.perform(get("/api/v1/parent/children/1/clubs"), TestAccounts.PARENT)
                    .andExpect(status().isOk()));
            assertThat(after).hasSize(1);
            assertThat(after.get(0).get("code").asString()).isEqualTo("F-GUITAR");
            assertThat(after.get(0).get("status").asString()).isEqualTo("PENDING");
        }
    }
}
