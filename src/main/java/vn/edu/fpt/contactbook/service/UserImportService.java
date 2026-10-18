package vn.edu.fpt.contactbook.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.contactbook.dto.CreatedUser;
import vn.edu.fpt.contactbook.dto.ImportRowError;
import vn.edu.fpt.contactbook.dto.ImportedUser;
import vn.edu.fpt.contactbook.dto.RoleProfileForm;
import vn.edu.fpt.contactbook.dto.UserForm;
import vn.edu.fpt.contactbook.dto.UserImportResult;
import vn.edu.fpt.contactbook.model.Department;
import vn.edu.fpt.contactbook.model.Major;
import vn.edu.fpt.contactbook.model.SchoolClass;
import vn.edu.fpt.contactbook.model.enums.Gender;
import vn.edu.fpt.contactbook.model.enums.StudentStatus;
import vn.edu.fpt.contactbook.repository.DepartmentRepository;
import vn.edu.fpt.contactbook.repository.MajorRepository;
import vn.edu.fpt.contactbook.repository.SchoolClassRepository;
import vn.edu.fpt.contactbook.security.AppUserPrincipal;
import vn.edu.fpt.contactbook.util.ExcelUtils;
import vn.edu.fpt.contactbook.util.PhoneUtils;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nhập người dùng từ Excel (SRS FR-USER-09). Kiểm tra TOÀN BỘ file bằng đúng bộ kiểm tra của tạo tay
 * (Bean Validation trên UserForm + UserService.checkNewUser), thêm kiểm tra trùng ngay trong file.
 * Có lỗi thì không ghi dòng nào; không lỗi thì tạo tất cả trong một transaction.
 */
@Service
@RequiredArgsConstructor
public class UserImportService {

    public static final int MAX_ROWS = ExcelImportReader.MAX_ROWS;
    public static final int MAX_BYTES = ExcelImportReader.MAX_BYTES;
    /** Số điện thoại của dòng ví dụ trong file mẫu; dòng này phải được xóa trước khi nhập. */
    public static final String EXAMPLE_PHONE = "0900000000";

    static final Map<String, Gender> GENDERS = Map.of("nam", Gender.MALE, "nữ", Gender.FEMALE, "khác", Gender.OTHER);
    static final Map<String, StudentStatus> STATUS_LABELS = Map.of(
            "đang học", StudentStatus.STUDYING, "bảo lưu", StudentStatus.ON_LEAVE,
            "đã tốt nghiệp", StudentStatus.GRADUATED, "thôi học", StudentStatus.DROPPED);

    private final UserService userService;
    private final UserAccessPolicy accessPolicy;
    private final Validator validator;
    private final MajorRepository majorRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    /** Một dòng dữ liệu đã đọc: số dòng Excel, form dựng từ các ô, lỗi định dạng ô (theo tên ô của UserForm). */
    record ParsedRow(int rowNumber, UserForm form, Map<String, String> cellErrors, String code) {
    }

    /**
     * Đọc, kiểm tra và (nếu không có lỗi) tạo người dùng. Lỗi mức file (không phải .xlsx, quá lớn, quá 500 dòng,
     * file trống) ném BusinessException. Vi phạm unique phát hiện ở database lúc tạo cũng ném BusinessException,
     * khiến cả transaction rollback (không người dùng nào được tạo).
     */
    @Transactional
    public UserImportResult importUsers(AppUserPrincipal actor, UserImportType type, String fileName, byte[] content,
                                        String ipAddress) {
        accessPolicy.requireAdmin(actor);
        List<ImportRowError> headerErrors = new ArrayList<>();
        List<ParsedRow> rows = read(type, fileName, content, headerErrors);
        if (!headerErrors.isEmpty()) {
            return UserImportResult.failed(headerErrors);
        }
        List<ImportRowError> errors = validate(type, rows);
        if (!errors.isEmpty()) {
            return UserImportResult.failed(errors);
        }

        List<ImportedUser> created = new ArrayList<>();
        for (ParsedRow row : rows) {
            CreatedUser user = userService.create(actor, row.form(), ipAddress);
            created.add(new ImportedUser(PhoneUtils.normalize(row.form().getPhone()).orElseThrow(),
                    row.form().getFullName().strip(), row.code(), user.temporaryPassword()));
        }
        auditService.log(actor.getUserId(), AuditActions.IMPORT_USERS, AuditActions.ENTITY_USER, null,
                "type=" + type + ",count=" + created.size(), ipAddress);
        return UserImportResult.succeeded(created);
    }

    List<ParsedRow> read(UserImportType type, String fileName, byte[] content, List<ImportRowError> headerErrors) {
        List<String> headers = type.columns().stream().map(ImportColumn::header).toList();
        return ExcelImportReader.read(fileName, content, headers, type.label().toLowerCase(Locale.ROOT), headerErrors,
                () -> {
                    Catalog catalog = loadCatalog();
                    return row -> parse(type, row, catalog);
                });
    }

    private ParsedRow parse(UserImportType type, Row row, Catalog catalog) {
        UserForm form = new UserForm();
        form.setRoles(Set.of(type.roleCode()));
        RoleProfileForm profile = form.getProfile();
        Map<String, String> errors = new LinkedHashMap<>();
        List<ImportColumn> columns = type.columns();
        for (int i = 0; i < columns.size(); i++) {
            ImportColumn column = columns.get(i);
            Cell cell = row.getCell(i);
            String text = ExcelUtils.text(cell);
            switch (column) {
                case PHONE -> form.setPhone(ExcelUtils.phone(cell));
                case FULL_NAME -> form.setFullName(text);
                case EMAIL -> form.setEmail(text);
                case ADDRESS -> form.setAddress(text);
                case GENDER -> {
                    if (text != null) {
                        Gender gender = GENDERS.get(text.toLowerCase(Locale.ROOT));
                        if (gender == null) {
                            errors.put(column.field(), "Giới tính chỉ nhận Nam, Nữ hoặc Khác");
                        }
                        form.setGender(gender);
                    }
                }
                case DATE_OF_BIRTH -> {
                    try {
                        form.setDateOfBirth(ExcelUtils.date(cell));
                    } catch (DateTimeException e) {
                        errors.put(column.field(), "Ngày sinh phải có dạng dd/MM/yyyy, ví dụ 01/09/2006");
                    }
                }
                case STUDENT_CODE -> profile.setStudentCode(text);
                case TEACHER_CODE -> profile.setTeacherCode(text);
                case ACADEMIC_TITLE -> profile.setAcademicTitle(text);
                case MAJOR_CODE -> profile.setMajorId(lookup(text, catalog.majors(), Major::getId, column,
                        "Vui lòng nhập mã ngành", "Mã ngành", errors));
                case CLASS_CODE -> profile.setClassId(text == null ? null
                        : lookup(text, catalog.classes(), SchoolClass::getId, column, null, "Mã lớp", errors));
                case DEPARTMENT_CODE -> profile.setTeacherDepartmentId(lookup(text, catalog.departments(),
                        Department::getId, column, "Vui lòng nhập mã bộ môn", "Mã bộ môn", errors));
                case ENROLLMENT_YEAR -> {
                    if (text == null) {
                        errors.put(column.field(), "Vui lòng nhập năm nhập học");
                    } else if (!text.matches("\\d{4}")) {
                        errors.put(column.field(), "Năm nhập học phải là số có 4 chữ số");
                    } else {
                        profile.setEnrollmentYear(Short.valueOf(text));
                    }
                }
                case STUDENT_STATUS -> {
                    if (text != null) {
                        StudentStatus status = studentStatus(text);
                        if (status == null) {
                            errors.put(column.field(), "Trạng thái chỉ nhận Đang học, Bảo lưu, Đã tốt nghiệp hoặc Thôi học");
                        }
                        profile.setStudentStatus(status);
                    }
                }
            }
        }
        String code = type == UserImportType.STUDENT ? profile.getStudentCode() : profile.getTeacherCode();
        return new ParsedRow(row.getRowNum() + 1, form, errors,
                code == null ? null : code.strip().toUpperCase(Locale.ROOT));
    }

    /**
     * Kiểm tra từng dòng bằng bộ kiểm tra của tạo tay, rồi kiểm tra trùng trong file. Lỗi định dạng ô
     * được giữ trước (putIfAbsent), để mỗi ô chỉ báo một lý do rõ nhất.
     */
    List<ImportRowError> validate(UserImportType type, List<ParsedRow> rows) {
        Map<String, Integer> phones = new HashMap<>();
        Map<String, Integer> emails = new HashMap<>();
        Map<String, Integer> codes = new HashMap<>();
        String codeField = type == UserImportType.STUDENT ? ImportColumn.STUDENT_CODE.field()
                : ImportColumn.TEACHER_CODE.field();

        List<ImportRowError> errors = new ArrayList<>();
        for (ParsedRow row : rows) {
            Map<String, String> rowErrors = new LinkedHashMap<>(row.cellErrors());
            UserForm form = row.form();
            String phone = PhoneUtils.normalize(form.getPhone()).orElse(null);
            if (EXAMPLE_PHONE.equals(phone)) {
                rowErrors.put(ImportColumn.PHONE.field(), "Đây là dòng ví dụ của file mẫu, hãy xóa trước khi nhập");
            }
            for (ConstraintViolation<UserForm> violation : validator.validate(form)) {
                rowErrors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage());
            }
            userService.checkNewUser(form).forEach(rowErrors::putIfAbsent);

            duplicateInFile(phones, phone, row.rowNumber(), ImportColumn.PHONE.field(), rowErrors);
            String email = form.getEmail() == null ? null : form.getEmail().toLowerCase(Locale.ROOT);
            duplicateInFile(emails, email, row.rowNumber(), ImportColumn.EMAIL.field(), rowErrors);
            duplicateInFile(codes, row.code(), row.rowNumber(), codeField, rowErrors);

            rowErrors.forEach((field, message) -> errors.add(new ImportRowError(row.rowNumber(),
                    type.columnOf(field).map(ImportColumn::header).orElse(""), message)));
        }
        List<String> order = type.columns().stream().map(ImportColumn::header).toList();
        errors.sort(Comparator.comparingInt(ImportRowError::rowNumber)
                .thenComparingInt(error -> order.indexOf(error.column())));
        return errors;
    }

    private static void duplicateInFile(Map<String, Integer> seen, String key, int rowNumber, String field,
                                        Map<String, String> rowErrors) {
        if (key == null) {
            return;
        }
        Integer first = seen.putIfAbsent(key, rowNumber);
        if (first != null) {
            rowErrors.putIfAbsent(field, "Trùng với dòng " + first + " trong file");
        }
    }

    private static <T> Long lookup(String code, Map<String, T> byCode, Function<T, Long> id, ImportColumn column,
                                   String missingMessage, String label, Map<String, String> errors) {
        if (code == null) {
            if (missingMessage != null) {
                errors.put(column.field(), missingMessage);
            }
            return null;
        }
        T found = byCode.get(code.toUpperCase(Locale.ROOT));
        if (found == null) {
            errors.put(column.field(), label + " \"" + code + "\" không tồn tại (xem sheet Danh mục)");
            return null;
        }
        return id.apply(found);
    }

    private static StudentStatus studentStatus(String text) {
        StudentStatus byLabel = STATUS_LABELS.get(text.toLowerCase(Locale.ROOT));
        if (byLabel != null) {
            return byLabel;
        }
        try {
            return StudentStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Danh mục tra theo mã (viết hoa), nạp một lần cho cả file. */
    record Catalog(Map<String, Major> majors, Map<String, SchoolClass> classes, Map<String, Department> departments) {
    }

    private Catalog loadCatalog() {
        return new Catalog(
                byCode(majorRepository.findAllByOrderByCodeAsc(), Major::getCode),
                byCode(schoolClassRepository.findAllByOrderByCodeAsc(), SchoolClass::getCode),
                byCode(departmentRepository.findAllByOrderByCodeAsc(), Department::getCode));
    }

    private static <T> Map<String, T> byCode(List<T> items, Function<T, String> code) {
        return items.stream().collect(Collectors.toMap(item -> code.apply(item).toUpperCase(Locale.ROOT),
                Function.identity(), (first, second) -> first));
    }
}
