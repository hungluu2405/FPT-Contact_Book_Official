package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import vn.edu.fpt.contactbook.exception.BusinessException;
import vn.edu.fpt.contactbook.model.ClubActivity;
import vn.edu.fpt.contactbook.model.ClubMember;
import vn.edu.fpt.contactbook.model.enums.ClubActivityStatus;
import vn.edu.fpt.contactbook.model.enums.ClubMemberRole;
import vn.edu.fpt.contactbook.model.enums.ClubMemberStatus;
import vn.edu.fpt.contactbook.model.enums.ClubStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Luật thuần của câu lạc bộ: trạng thái hoạt động theo thời gian (biên đúng giờ bắt đầu / kết thúc), vòng đời yêu
 * cầu tham gia, mở đăng ký, sĩ số, ghi nhận tham dự.
 */
class ClubRuleTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 8, 0);
    static final LocalDateTime END = LocalDateTime.of(2026, 11, 1, 11, 0);

    @Nested
    @DisplayName("Trạng thái hoạt động tính theo thời gian")
    class State {

        @Test
        @DisplayName("Trước giờ bắt đầu PLANNED; đúng giờ bắt đầu ONGOING; đúng giờ kết thúc DONE")
        void boundaries() {
            ClubActivity activity = activity(ClubActivityStatus.PLANNED);
            assertThat(ClubRule.state(activity, START.minusSeconds(1))).isEqualTo(ClubActivityStatus.PLANNED);
            assertThat(ClubRule.state(activity, START)).isEqualTo(ClubActivityStatus.ONGOING);
            assertThat(ClubRule.state(activity, END.minusSeconds(1))).isEqualTo(ClubActivityStatus.ONGOING);
            assertThat(ClubRule.state(activity, END)).isEqualTo(ClubActivityStatus.DONE);
            assertThat(ClubRule.state(activity, END.plusDays(30))).isEqualTo(ClubActivityStatus.DONE);
        }

        @Test
        @DisplayName("Đã hủy luôn là CANCELLED, mọi thời điểm")
        void cancelled() {
            ClubActivity activity = activity(ClubActivityStatus.CANCELLED);
            assertThat(ClubRule.state(activity, START.minusDays(1))).isEqualTo(ClubActivityStatus.CANCELLED);
            assertThat(ClubRule.state(activity, START)).isEqualTo(ClubActivityStatus.CANCELLED);
            assertThat(ClubRule.state(activity, END.plusDays(1))).isEqualTo(ClubActivityStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("Vòng đời yêu cầu tham gia")
    class Join {

        @Test
        @DisplayName("Chưa có dòng: được gửi")
        void noRow() {
            assertThatCode(() -> ClubRule.requireCanJoin(ClubStatus.ACTIVE, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PENDING hoặc ACTIVE: không gửi lại")
        void pendingOrActive() {
            assertThatThrownBy(() -> ClubRule.requireCanJoin(ClubStatus.ACTIVE, member(ClubMemberStatus.PENDING)))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ALREADY_PENDING);
            assertThatThrownBy(() -> ClubRule.requireCanJoin(ClubStatus.ACTIVE, member(ClubMemberStatus.ACTIVE)))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ALREADY_MEMBER);
        }

        @ParameterizedTest
        @EnumSource(value = ClubMemberStatus.class, names = {"REJECTED", "LEFT"})
        @DisplayName("REJECTED hoặc LEFT: gửi lại được, dòng cũ về PENDING / MEMBER, joined_at = NULL")
        void resubmit(ClubMemberStatus status) {
            ClubMember old = member(status);
            old.setMemberRole(ClubMemberRole.VICE_PRESIDENT);
            old.setJoinedAt(START);

            assertThatCode(() -> ClubRule.requireCanJoin(ClubStatus.ACTIVE, old)).doesNotThrowAnyException();
            ClubRule.resetToPending(old);

            assertThat(old.getStatus()).isEqualTo(ClubMemberStatus.PENDING);
            assertThat(old.getMemberRole()).isEqualTo(ClubMemberRole.MEMBER);
            assertThat(old.getJoinedAt()).isNull();
        }

        @Test
        @DisplayName("CLB INACTIVE: không nhận yêu cầu, kể cả người chưa có dòng")
        void inactiveClub() {
            assertThatThrownBy(() -> ClubRule.requireCanJoin(ClubStatus.INACTIVE, null))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.CLUB_INACTIVE);
            assertThatThrownBy(() -> ClubRule.requireCanJoin(ClubStatus.INACTIVE, member(ClubMemberStatus.LEFT)))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.CLUB_INACTIVE);
        }
    }

    @Nested
    @DisplayName("Đăng ký, sĩ số, tham dự")
    class Registration {

        @Test
        @DisplayName("Đăng ký và sửa chỉ trước giờ bắt đầu; đúng giờ bắt đầu là đã bắt đầu")
        void openUntilStart() {
            ClubActivity activity = activity(ClubActivityStatus.PLANNED);
            assertThatCode(() -> ClubRule.requireOpenForRegistration(activity, START.minusSeconds(1)))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> ClubRule.requireOpenForRegistration(activity, START))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_STARTED);
            assertThatThrownBy(() -> ClubRule.requireEditable(activity, END))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_STARTED);
        }

        @Test
        @DisplayName("Hoạt động đã hủy: không đăng ký, không sửa, không ghi nhận tham dự")
        void cancelled() {
            ClubActivity activity = activity(ClubActivityStatus.CANCELLED);
            assertThatThrownBy(() -> ClubRule.requireOpenForRegistration(activity, START.minusDays(1)))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_CANCELLED);
            assertThatThrownBy(() -> ClubRule.requireCanMarkAttendance(activity, END))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_CANCELLED);
        }

        @Test
        @DisplayName("Sĩ số: còn chỗ khi đã giữ < tối đa; đủ chỗ thì từ chối; không giới hạn khi để trống")
        void capacity() {
            assertThatCode(() -> ClubRule.requireCapacity((short) 2, 1)).doesNotThrowAnyException();
            assertThatThrownBy(() -> ClubRule.requireCapacity((short) 2, 2))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_FULL);
            assertThatCode(() -> ClubRule.requireCapacity(null, 10_000)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ghi nhận ATTENDED trước giờ bắt đầu bị từ chối; từ giờ bắt đầu thì được")
        void attendance() {
            ClubActivity activity = activity(ClubActivityStatus.PLANNED);
            assertThatThrownBy(() -> ClubRule.requireCanMarkAttendance(activity, START.minusSeconds(1)))
                    .isInstanceOf(BusinessException.class).hasMessage(ClubMessages.ACTIVITY_NOT_STARTED);
            assertThatCode(() -> ClubRule.requireCanMarkAttendance(activity, START)).doesNotThrowAnyException();
            assertThatCode(() -> ClubRule.requireCanMarkAttendance(activity, END.plusDays(1)))
                    .doesNotThrowAnyException();
        }
    }

    static ClubActivity activity(ClubActivityStatus status) {
        ClubActivity activity = new ClubActivity();
        activity.setStartTime(START);
        activity.setEndTime(END);
        activity.setStatus(status);
        return activity;
    }

    static ClubMember member(ClubMemberStatus status) {
        ClubMember member = new ClubMember();
        member.setStatus(status);
        return member;
    }
}
