package vn.edu.fpt.contactbook.service;

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
            "Violation of UNIQUE KEY constraint 'uq_user_roles'.|roleCode|Người dùng đã có vai trò này với bộ môn này"
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
}
