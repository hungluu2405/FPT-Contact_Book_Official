package vn.edu.fpt.contactbook.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.Semester;
import vn.edu.fpt.contactbook.repository.ClubActivityRepository;
import vn.edu.fpt.contactbook.repository.SemesterRepository;
import vn.edu.fpt.contactbook.security.AppUserDetailsService;
import vn.edu.fpt.contactbook.support.IntegrationTest;
import vn.edu.fpt.contactbook.support.TestAccounts;
import vn.edu.fpt.contactbook.util.AppTime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ô ngày, giờ là ô chữ theo dd/MM/yyyy và HH:mm trên mọi trình duyệt (review 8a, U3): hiển thị lại đúng định dạng,
 * nhận cả dạng ISO cũ (yyyy-MM-dd) để link và form cũ vẫn chạy, nhập sai báo lỗi tiếng Việt cạnh ô.
 */
@IntegrationTest
class DateInputIntegrationTest {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Chủ nhiệm CLB 1 trong dữ liệu mẫu (users.id 18). */
    static final String CLUB_PRESIDENT = "0933112233";

    @Autowired MockMvc mockMvc;
    @Autowired AppUserDetailsService userDetailsService;
    @Autowired SemesterRepository semesterRepository;
    @Autowired ClubActivityRepository activityRepository;

    @Test
    @DisplayName("Tạo học kỳ với ngày dd/MM/yyyy (kể cả d/M/yyyy); trang sửa hiện lại dd/MM/yyyy trong ô chữ")
    void semesterDates() throws Exception {
        mockMvc.perform(post("/admin/catalog/semesters/new").with(as(TestAccounts.ADMIN)).with(csrf())
                        .param("code", "SU28").param("name", "Summer 2028")
                        .param("startDate", "03/05/2028").param("endDate", "2/9/2028"))
                .andExpect(redirectedUrlPattern("/admin/catalog/semesters/*"));
        Semester semester = semesterRepository.findAll().stream().filter(s -> s.getCode().equals("SU28"))
                .findFirst().orElseThrow();
        assertThat(semester.getStartDate()).isEqualTo(LocalDate.of(2028, 5, 3));
        assertThat(semester.getEndDate()).isEqualTo(LocalDate.of(2028, 9, 2));

        mockMvc.perform(get("/admin/catalog/semesters/{id}/edit", semester.getId()).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("value=\"03/05/2028\""),
                        containsString("value=\"02/09/2028\""), containsString("placeholder=\"dd/mm/yyyy\""))));
    }

    @Test
    @DisplayName("Ngày không tồn tại hoặc sai dạng: lỗi tiếng Việt kèm mẫu cần nhập, không lưu")
    void invalidDate() throws Exception {
        mockMvc.perform(post("/admin/catalog/semesters/new").with(as(TestAccounts.ADMIN)).with(csrf())
                        .param("code", "BAD2").param("name", "Sai").param("startDate", "31/02/2028")
                        .param("endDate", "2028-13-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ngày không hợp lệ, nhập theo dạng dd/mm/yyyy")));
        assertThat(semesterRepository.existsByCode("BAD2")).isFalse();
    }

    @Test
    @DisplayName("Hoạt động CLB: thời gian dd/MM/yyyy HH:mm được nhận và hiện lại cùng định dạng")
    void activityDateTime() throws Exception {
        LocalDateTime start = AppTime.now().plusDays(10).withHour(8).withMinute(30).withSecond(0).withNano(0);
        DateTimeFormatter input = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        mockMvc.perform(post("/clubs/1/activities/new").with(as(CLUB_PRESIDENT)).with(csrf())
                        .param("title", "Workshop Spring").param("location", "Phòng 201")
                        .param("startTime", start.format(input)).param("endTime", start.plusHours(2).format(input)))
                .andExpect(status().is3xxRedirection());
        ClubActivity activity = activityRepository.findAll().stream()
                .max(Comparator.comparing(ClubActivity::getId)).orElseThrow();
        assertThat(activity.getStartTime()).isEqualTo(start);

        mockMvc.perform(get("/clubs/1/activities/{id}/edit", activity.getId()).with(as(CLUB_PRESIDENT)))
                .andExpect(content().string(containsString("value=\"" + start.format(input) + "\"")));
    }

    @Test
    @DisplayName("Ô giờ nhận cả 8:00 lẫn 08:00 (như ô ngày nhận 5/11/2026)")
    void singleDigitHour() throws Exception {
        mockMvc.perform(post("/admin/sections/1/exams/new").with(as(TestAccounts.ADMIN)).with(csrf())
                        .param("examType", "FINAL").param("examDate", "16/12/2026").param("startTime", "8:00")
                        .param("endTime", "09:30").param("roomId", "1"))
                .andExpect(redirectedUrl("/admin/sections/1"));
    }

    @Test
    @DisplayName("Lọc nhật ký theo ngày dd/MM/yyyy")
    void auditFilter() throws Exception {
        String today = AppTime.today().format(DATE);

        mockMvc.perform(get("/admin/audit-logs").param("from", today).param("to", today).with(as(TestAccounts.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("value=\"" + today + "\""),
                        not(containsString("Ngày không hợp lệ")))));
    }

    @Test
    @DisplayName("Không còn ô date/time/datetime-local của trình duyệt trong template (định dạng phụ thuộc máy)")
    void noBrowserDateInputs() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "resources", "templates"))) {
            List<Path> offending = files.filter(path -> path.toString().endsWith(".html")).filter(path -> {
                try {
                    String html = Files.readString(path);
                    return html.contains("type=\"date\"") || html.contains("type=\"time\"")
                            || html.contains("type=\"datetime-local\"");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertThat(offending).isEmpty();
        }
    }

    private RequestPostProcessor as(String phone) {
        return user(userDetailsService.loadUserByUsername(phone));
    }
}
