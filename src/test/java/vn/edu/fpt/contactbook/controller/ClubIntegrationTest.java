package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.AuditLog;
import vn.edu.fpt.contactbook.model.Club;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubActivityParticipant;
import vn.edu.fpt.contactbook.model.ClubActivityParticipantId;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;
import vn.edu.fpt.contactbook.model.enums.ParticipantStatus;
import vn.edu.fpt.contactbook.repository.AuditLogRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityParticipantRepository;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.ClubMemberRepository;
import vn.edu.fpt.contactbook.repository.ClubRepository;
import vn.edu.fpt.contactbook.repository.NotificationRecipientRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.service.AuditActions;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Câu lạc bộ trên FptContactBook_test. Dữ liệu mẫu: CLB 1 F-CODE (chủ nhiệm SE180101 users.id 18 / 0933112233,
 * thành viên users.id 19 / 0933112234, users.id 20 đang chờ duyệt), CLB 2 F-GUITAR (chủ nhiệm users.id 20 /
 * 0933112235). SV 0900000004 (SE190001, users.id 4) chưa vào CLB nào.
 */
@IntegrationTest
class ClubIntegrationTest {

    static final String PRESIDENT = "0933112233";
    static final String MEMBER = "0933112234";
    static final String OTHER_PRESIDENT = "0933112235";
    static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired ClubRepository clubRepository;
    @Autowired ClubMemberRepository memberRepository;
    @Autowired ClubActivityRepository activityRepository;
    @Autowired ClubActivityParticipantRepository participantRepository;
    @Autowired NotificationRecipientRepository recipientRepository;
    @Autowired AuditLogRepository auditLogRepository;

    @Nested
    @DisplayName("ADMIN quản lý CLB")
    class Admin {

        @Test
        @DisplayName("Thêm (kèm logo), sửa, ngừng, mở lại, chỉ định chủ nhiệm; có audit")
        void manage() throws Exception {
            String url = mockMvc.perform(multipart("/admin/clubs/new")
                            .file(new MockMultipartFile("logo", "logo.png", "image/png", PNG))
                            .param("code", "f-chess").param("name", "Câu lạc bộ Cờ vua")
                            .param("advisorUserId", "16").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrlPattern("/admin/clubs/*"))
                    .andReturn().getResponse().getRedirectedUrl();
            Long id = Long.valueOf(url.substring(url.lastIndexOf('/') + 1));
            Club club = clubRepository.findById(id).orElseThrow();
            assertThat(club.getCode()).isEqualTo("F-CHESS");
            assertThat(club.getLogoUrl()).startsWith("/club-logos/");
            mockMvc.perform(get(club.getLogoUrl()).with(as(TestAccounts.STUDENT))).andExpect(status().isOk());

            mockMvc.perform(multipart("/admin/clubs/{id}/edit", id).param("code", "F-CHESS")
                            .param("name", "CLB Cờ vua FPT").with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/clubs/" + id));
            mockMvc.perform(post("/admin/clubs/{id}/deactivate", id).with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/clubs/" + id));
            assertThat(clubRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ClubStatus.INACTIVE);
            mockMvc.perform(post("/admin/clubs/{id}/activate", id).with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(redirectedUrl("/admin/clubs/" + id));
            mockMvc.perform(post("/admin/clubs/{id}/president", id).param("studentCode", "se190001")
                            .with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã chỉ định chủ nhiệm"));

            ClubMember president = memberRepository.findByClubIdAndUserId(id, TestAccounts.STUDENT_ID).orElseThrow();
            assertThat(president.getMemberRole()).isEqualTo(ClubMemberRole.PRESIDENT);
            assertThat(president.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            assertThat(auditActions()).contains(AuditActions.CREATE_CLUB, AuditActions.UPDATE_CLUB,
                    AuditActions.DEACTIVATE_CLUB, AuditActions.ACTIVATE_CLUB, AuditActions.ASSIGN_CLUB_PRESIDENT);
        }

        @Test
        @DisplayName("Chuyển giao chủ nhiệm CLB 1 cho SV khác: người cũ thành MEMBER, chỉ còn một PRESIDENT")
        void transfer() throws Exception {
            mockMvc.perform(post("/admin/clubs/1/president").param("studentCode", "SE180102")
                    .with(as(TestAccounts.ADMIN)).with(csrf()));

            assertThat(memberRepository.findPresidents(1L)).extracting(m -> m.getUser().getId()).containsExactly(19L);
            assertThat(memberRepository.findByClubIdAndUserId(1L, 18L).orElseThrow().getMemberRole())
                    .isEqualTo(ClubMemberRole.MEMBER);
        }

        @Test
        @DisplayName("Mã trùng: báo lỗi cạnh ô, không lưu; không phải ADMIN: 403")
        void guards() throws Exception {
            mockMvc.perform(multipart("/admin/clubs/new").param("code", "f-code").param("name", "Trùng")
                            .with(as(TestAccounts.ADMIN)).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Mã câu lạc bộ đã tồn tại")));
            mockMvc.perform(get("/admin/clubs").with(as(PRESIDENT))).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/clubs/1/deactivate").with(as(TestAccounts.TEACHER)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Tham gia và duyệt")
    class Membership {

        @Test
        @DisplayName("SV gửi yêu cầu, chủ nhiệm duyệt: ACTIVE, SV nhận thông báo; bị từ chối rồi gửi lại không lỗi trùng")
        void joinApproveAndResubmit() throws Exception {
            long unreadBefore = recipientRepository.countUnread(TestAccounts.STUDENT_ID);
            join(TestAccounts.STUDENT);
            ClubMember request = memberRepository.findByClubIdAndUserId(1L, TestAccounts.STUDENT_ID).orElseThrow();
            assertThat(request.getStatus()).isEqualTo(ClubMemberStatus.PENDING);

            mockMvc.perform(get("/clubs/1/members").with(as(PRESIDENT)))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Lê Minh Sinh")));
            mockMvc.perform(post("/clubs/1/members/{m}/reject", request.getId()).with(as(PRESIDENT)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã từ chối yêu cầu"));
            assertThat(memberStatus(request)).isEqualTo(ClubMemberStatus.REJECTED);

            join(TestAccounts.STUDENT);
            assertThat(memberRepository.findByClubIdAndUserId(1L, TestAccounts.STUDENT_ID).orElseThrow().getId())
                    .isEqualTo(request.getId());
            assertThat(memberStatus(request)).isEqualTo(ClubMemberStatus.PENDING);

            mockMvc.perform(post("/clubs/1/members/{m}/approve", request.getId()).with(as(PRESIDENT)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã duyệt thành viên"));
            ClubMember approved = memberRepository.findById(request.getId()).orElseThrow();
            assertThat(approved.getStatus()).isEqualTo(ClubMemberStatus.ACTIVE);
            assertThat(approved.getJoinedAt()).isNotNull();
            assertThat(recipientRepository.countUnread(TestAccounts.STUDENT_ID)).isEqualTo(unreadBefore + 2);
            mockMvc.perform(get("/clubs/my").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(containsString("Câu lạc bộ Lập trình FPT (F-Code)")));
        }

        @Test
        @DisplayName("SV không phải chủ nhiệm duyệt: 403; chủ nhiệm CLB 2 duyệt cho CLB 1: 403")
        void wrongReviewer() throws Exception {
            mockMvc.perform(post("/clubs/1/members/3/approve").with(as(MEMBER)).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/clubs/1/members/3/approve").with(as(OTHER_PRESIDENT)).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/clubs/2/members/3/approve").with(as(OTHER_PRESIDENT)).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/clubs/1/members").with(as(OTHER_PRESIDENT))).andExpect(status().isForbidden());
            assertThat(memberRepository.findById(3L).orElseThrow().getStatus()).isEqualTo(ClubMemberStatus.PENDING);
        }

        @Test
        @DisplayName("Chủ nhiệm không tự rời; thành viên rời được (LEFT)")
        void leave() throws Exception {
            mockMvc.perform(post("/clubs/1/leave").with(as(PRESIDENT)).with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Chủ nhiệm không thể rời câu lạc bộ khi chưa chuyển giao"));
            mockMvc.perform(post("/clubs/1/leave").with(as(MEMBER)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Bạn đã rời câu lạc bộ"));
            assertThat(memberRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(ClubMemberStatus.LEFT);
        }

        @Test
        @DisplayName("CLB INACTIVE không nhận yêu cầu tham gia")
        void inactive() throws Exception {
            clubRepository.findById(1L).orElseThrow().setStatus(ClubStatus.INACTIVE);
            clubRepository.flush();

            mockMvc.perform(post("/clubs/1/join").with(as(TestAccounts.STUDENT)).with(csrf()))
                    .andExpect(flash().attribute("errorMessage", "Câu lạc bộ đã ngừng hoạt động"));
            assertThat(memberRepository.findByClubIdAndUserId(1L, TestAccounts.STUDENT_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Hoạt động")
    class Activities {

        @Test
        @DisplayName("Chủ nhiệm tạo hoạt động 1 chỗ; thành viên đăng ký; người thứ hai bị từ chối vì đủ chỗ; "
                + "hủy rồi đăng ký lại dùng dòng cũ")
        void capacityAndReRegister() throws Exception {
            LocalDateTime start = AppTime.now().plusDays(3).withSecond(0).withNano(0);
            mockMvc.perform(post("/clubs/1/activities/new").with(as(PRESIDENT)).with(csrf())
                            .param("title", "Workshop Spring Boot").param("location", "Phòng 201")
                            .param("startTime", start.format(INPUT)).param("endTime", start.plusHours(2).format(INPUT))
                            .param("maxParticipants", "1"))
                    .andExpect(redirectedUrl("/clubs/1/activities"));
            ClubActivity activity = activityRepository.findUpcoming(1L, AppTime.now()).getFirst();
            assertThat(activity.getMaxParticipants()).isEqualTo((short) 1);

            register(MEMBER, activity).andExpect(flash().attribute("successMessage", "Đã đăng ký hoạt động"));
            register(PRESIDENT, activity).andExpect(flash().attribute("errorMessage", "Hoạt động đã đủ số lượng"));
            assertThat(participantRepository.countTaken(activity.getId())).isEqualTo(1);

            mockMvc.perform(post("/clubs/1/activities/{a}/unregister", activity.getId()).with(as(MEMBER)).with(csrf()))
                    .andExpect(flash().attribute("successMessage", "Đã hủy đăng ký hoạt động"));
            register(MEMBER, activity).andExpect(flash().attribute("successMessage", "Đã đăng ký hoạt động"));

            ClubActivityParticipant row = participantRepository
                    .findById(new ClubActivityParticipantId(activity.getId(), 19L)).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(ParticipantStatus.REGISTERED);
            assertThat(participantRepository.findAll().stream()
                    .filter(p -> p.getId().getActivityId().equals(activity.getId()))).hasSize(1);
            mockMvc.perform(get("/clubs/1").with(as(MEMBER)))
                    .andExpect(content().string(allOf(containsString("Workshop Spring Boot"),
                            containsString("Hủy đăng ký"))));
        }

        @Test
        @DisplayName("Thành viên thường tạo hoạt động: 403; chủ nhiệm CLB 2 tạo cho CLB 1: 403")
        void createGuards() throws Exception {
            LocalDateTime start = AppTime.now().plusDays(3);
            for (String phone : List.of(MEMBER, OTHER_PRESIDENT, TestAccounts.TEACHER)) {
                mockMvc.perform(post("/clubs/1/activities/new").with(as(phone)).with(csrf())
                                .param("title", "X").param("startTime", start.format(INPUT))
                                .param("endTime", start.plusHours(1).format(INPUT)))
                        .andExpect(status().isForbidden());
                mockMvc.perform(get("/clubs/1/activities/new").with(as(phone))).andExpect(status().isForbidden());
            }
        }
    }

    @Nested
    @DisplayName("Truy cập")
    class Access {

        @Test
        @DisplayName("TEACHER, PARENT xem được danh sách và chi tiết, không đăng ký được (403)")
        void viewOnly() throws Exception {
            for (String phone : List.of(TestAccounts.TEACHER, TestAccounts.PARENT)) {
                mockMvc.perform(get("/clubs").with(as(phone)))
                        .andExpect(status().isOk()).andExpect(content().string(containsString("F-GUITAR")));
                mockMvc.perform(get("/clubs/1").with(as(phone)))
                        .andExpect(status().isOk())
                        .andExpect(content().string(allOf(containsString("Câu lạc bộ Lập trình FPT"),
                                not(containsString("Gửi yêu cầu tham gia")))));
                mockMvc.perform(post("/clubs/1/join").with(as(phone)).with(csrf())).andExpect(status().isForbidden());
                mockMvc.perform(get("/clubs/my").with(as(phone))).andExpect(status().isForbidden());
            }
        }

        @Test
        @DisplayName("Menu: Câu lạc bộ cho mọi vai trò, CLB của tôi cho STUDENT, Quản lý CLB cho ADMIN")
        void menu() throws Exception {
            mockMvc.perform(get("/clubs").with(as(TestAccounts.STUDENT)))
                    .andExpect(content().string(allOf(containsString("href=\"/clubs/my\""),
                            not(containsString("href=\"/admin/clubs\"")))));
            mockMvc.perform(get("/clubs").with(as(TestAccounts.ADMIN)))
                    .andExpect(content().string(allOf(containsString("href=\"/admin/clubs\""),
                            not(containsString("href=\"/clubs/my\"")))));
        }

        @Test
        @DisplayName("Chưa đăng nhập: chuyển tới trang đăng nhập")
        void anonymous() throws Exception {
            mockMvc.perform(get("/clubs")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/clubs/1")).andExpect(redirectedUrl("/login"));
            mockMvc.perform(get("/admin/clubs")).andExpect(redirectedUrl("/login"));
        }
    }

    private void join(String phone) throws Exception {
        mockMvc.perform(post("/clubs/1/join").with(as(phone)).with(csrf()))
                .andExpect(flash().attribute("successMessage", "Đã gửi yêu cầu tham gia, vui lòng chờ chủ nhiệm duyệt"));
    }

    private ResultActions register(String phone, ClubActivity activity)
            throws Exception {
        return mockMvc.perform(post("/clubs/1/activities/{a}/register", activity.getId()).with(as(phone)).with(csrf()));
    }

    private ClubMemberStatus memberStatus(ClubMember member) {
        return memberRepository.findById(member.getId()).orElseThrow().getStatus();
    }

    private List<String> auditActions() {
        return auditLogRepository.findAll().stream().map(AuditLog::getAction).toList();
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
