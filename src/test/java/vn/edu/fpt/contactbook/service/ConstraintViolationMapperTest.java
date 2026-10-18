package vn.edu.fpt.contactbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.fpt.contactbook.exception.BusinessException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationMapperTest {

    /** Thông báo theo đúng định dạng SQL Server trả về cho UNIQUE constraint và unique index. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Violation of UNIQUE KEY constraint 'uq_users_phone'. Cannot insert duplicate key in object 'dbo.users'.|phone|Số điện thoại đã được sử dụng",
            "Cannot insert duplicate key row in object 'dbo.users' with unique index 'ux_users_email'.|email|Email đã được sử dụng",
            "Violation of UNIQUE KEY constraint 'uq_students_code'.|profile.studentCode|Mã sinh viên đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_teachers_code'.|profile.teacherCode|Mã giảng viên đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_user_roles'.|roleCode|Người dùng đã có vai trò này với bộ môn này",
            "Violation of UNIQUE KEY constraint 'uq_faculties_code'.|code|Mã khoa đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_departments_code'.|code|Mã bộ môn đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_majors_code'.|code|Mã ngành đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_classes_code'.|code|Mã lớp đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_subjects_code'.|code|Mã môn học đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_semesters_code'.|code|Mã học kỳ đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_rooms_code'.|code|Mã phòng đã tồn tại",
            "Violation of UNIQUE KEY constraint 'uq_grade_components'.|name|Môn học đã có đầu điểm cùng tên",
            "Violation of UNIQUE KEY constraint 'uq_enrollments'.||Sinh viên đã có trong lớp học phần",
            "Violation of UNIQUE KEY constraint 'uq_sess_cs_slot'.||Lớp đã có buổi học (kể cả buổi đã hủy) bắt đầu đúng ngày giờ này, vui lòng chọn giờ bắt đầu khác",
            "Cannot insert duplicate key row in object 'dbo.class_sessions' with unique index 'ux_sess_room_slot'.||Phòng đã có buổi học khác bắt đầu cùng ngày giờ, vui lòng chọn phòng hoặc giờ khác"
    })
    void mapsKnownConstraintToField(String sqlMessage, String field, String message) {
        RuntimeException mapped = ConstraintViolationMapper.map(
                new DataIntegrityViolationException("could not execute statement", new SQLException(sqlMessage)));

        assertThat(mapped).isInstanceOf(BusinessException.class).hasMessage(message);
        assertThat(((BusinessException) mapped).getField()).isEqualTo(field);
        assertThat(mapped.getMessage()).doesNotContain("dbo");
    }

    @Test
    void unknownConstraintIsKept() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("x",
                new SQLException("The INSERT statement conflicted with the FOREIGN KEY constraint 'fk_x'."));

        assertThat(ConstraintViolationMapper.map(original)).isSameAs(original);
    }

    @Test
    void constraintWithoutFieldIsGlobalError() {
        RuntimeException mapped = ConstraintViolationMapper.map(new DataIntegrityViolationException("x",
                new SQLException("Violation of UNIQUE KEY constraint 'uq_students_user'.")));

        assertThat(((BusinessException) mapped).getField()).isNull();
    }

    /** CHECK và FOREIGN KEY: SQL Server đặt tên ràng buộc trong dấu nháy kép. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "The INSERT statement conflicted with the CHECK constraint \"ck_subjects_credits\".|credits|Số tín chỉ phải từ 1 đến 20",
            "The UPDATE statement conflicted with the CHECK constraint \"ck_gc_weight\".|weight|Trọng số phải lớn hơn 0 và không quá 100",
            "The INSERT statement conflicted with the CHECK constraint \"ck_semesters_dates\".|endDate|Ngày kết thúc phải sau ngày bắt đầu",
            "The INSERT statement conflicted with the CHECK constraint \"ck_rooms_capacity\".|capacity|Sức chứa phải lớn hơn 0",
            "The UPDATE statement conflicted with the CHECK constraint \"ck_cs_max\".|maxStudents|Sĩ số tối đa phải từ 1 đến 32767"
    })
    void mapsCheckConstraintInDoubleQuotes(String sqlMessage, String field, String message) {
        RuntimeException mapped = ConstraintViolationMapper.map(
                new DataIntegrityViolationException("could not execute statement", new SQLException(sqlMessage)));

        assertThat(mapped).isInstanceOf(BusinessException.class).hasMessage(message);
        assertThat(((BusinessException) mapped).getField()).isEqualTo(field);
    }

    @Test
    void mapsGlobalCatalogConstraints() {
        RuntimeException current = ConstraintViolationMapper.map(new DataIntegrityViolationException("x",
                new SQLException("Cannot insert duplicate key row in object 'dbo.semesters' with unique index "
                        + "'ux_semesters_current'. The duplicate key value is (1).")));
        RuntimeException grades = ConstraintViolationMapper.map(new DataIntegrityViolationException("x",
                new SQLException("The DELETE statement conflicted with the REFERENCE constraint "
                        + "\"fk_grades_component\". The conflict occurred in database \"FptContactBook\".")));

        assertThat(current).hasMessage("Học kỳ hiện tại vừa được người khác thay đổi, vui lòng tải lại trang và thử lại");
        assertThat(((BusinessException) current).getField()).isNull();
        assertThat(grades).hasMessage("Đầu điểm đã có điểm nên không xóa được");
        assertThat(grades.getMessage()).doesNotContain("FptContactBook");
    }

    @Test
    @DisplayName("Tên ràng buộc chỉ khớp nguyên tên trong dấu nháy, không khớp tiền tố")
    void doesNotMatchPrefixOfAnotherConstraint() {
        DataIntegrityViolationException original = new DataIntegrityViolationException("x",
                new SQLException("The INSERT statement conflicted with the CHECK constraint \"ck_gc_weight_extra\"."));

        assertThat(ConstraintViolationMapper.map(original)).isSameAs(original);
    }
}
