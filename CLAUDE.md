# Sổ liên lạc điện tử Đại học FPT (Web)

Ứng dụng web xây dựng theo từng giai đoạn. Mỗi lượt chỉ làm đúng giai đoạn được giao, xong thì báo cáo và DỪNG.

Phạm vi: file này áp dụng cho backend Spring Boot và giao diện web. App Android (Flutter) nằm trong `mobile/` và theo `mobile/CLAUDE.md`. Lộ trình mobile: `docs/MOBILE_ROADMAP.md`.

## 0. GHI ĐÈ RULE GLOBAL
File này bổ sung `~/.claude/CLAUDE.md`. Ở những điểm dưới đây, file này thắng:
- **Ngôn ngữ**: chữ trên giao diện, `messages.properties`, thông báo lỗi nghiệp vụ, chú thích trong code và tài liệu trong `docs/` viết bằng tiếng Việt. Tên lớp, biến, hàm, package, log và commit message vẫn bằng tiếng Anh.
- **Thiếu thông tin**: chuyện vụn vặt thì chọn phương án hợp lý nhất, ghi một dòng vào `docs/ASSUMPTIONS.md` rồi làm tiếp. Vẫn phải dừng và hỏi khi: thay đổi cấu trúc dữ liệu, thêm/đổi dependency ngoài danh sách đã duyệt (mục 2), hoặc hai cách hiểu dẫn tới chức năng khác nhau rõ rệt.
- **Database dev**: được phép chạy ứng dụng với SQL Server trên **localhost** (database `FptContactBook`) để nghiệm thu, và chỉ thao tác dữ liệu **thông qua ứng dụng**. Không chạy DDL/DML trực tiếp bằng công cụ dòng lệnh, không trỏ tới bất kỳ máy chủ nào khác localhost.
- **Database test**: test suite được phép xóa và tạo lại database `FptContactBook_test` trên localhost (xem mục 8). Đây là database tạm của test, không phải database dev.
- **Kế hoạch**: mỗi giai đoạn vẫn phải trình kế hoạch và CHỜ duyệt trước khi sửa file (đúng rule global).

## 1. NGUỒN SỰ THẬT
- Cấu trúc dữ liệu: `database/fpt_contact_book.sql` (SQL Server, 36 bảng). Đọc kỹ các bảng liên quan trước khi viết entity.
- Nghiệp vụ và chức năng: `docs/SRS.md`.
- Hai nguồn mâu thuẫn: script SQL thắng về cấu trúc dữ liệu, SRS thắng về nghiệp vụ.
- Phần công nghệ trong SRS (Spring Boot 3.3.4, H2) đã lỗi thời; về công nghệ thì CLAUDE.md và `pom.xml` thắng.
- Tuyệt đối KHÔNG sửa `fpt_contact_book.sql`. Cần đổi cấu trúc dữ liệu thì dừng lại, đề xuất để duyệt.

## 2. CÔNG NGHỆ (cố định)
- Java 21, Spring Boot 4.1.x (đúng phiên bản trong `pom.xml`, không hạ xuống 3.x), Spring MVC (không WebFlux), Thymeleaf render phía máy chủ.
- Spring Data JPA (Hibernate 7) với SQL Server, driver `com.microsoft.sqlserver:mssql-jdbc`.
- Spring Security 7, cấu hình bằng bean `SecurityFilterChain`.
- Giao diện: Bootstrap 5 và Bootstrap Icons qua WebJars (không CDN), CSS/JS tự viết trong `static/`.
- Excel: Apache POI. Giảm boilerplate: Lombok.
- Không dùng H2. CHƯA thêm jjwt và springdoc (chờ giai đoạn REST API, cần bản tương thích Jackson 3).
- REST API cho mobile mở từ giai đoạn 9b, nằm dưới `/api/v1/**`, dùng lại service hiện có và cùng database `FptContactBook` (mobile không có database riêng). Trước giai đoạn 9b không viết REST API.
- Spring Boot 4: starter web là `spring-boot-starter-webmvc`, starter test là `spring-boot-starter-webmvc-test`, Jackson 3 (package `tools.jackson`). Không chắc tên starter, API hay cấu hình thì tra tài liệu chính thức hoặc Maven Central, KHÔNG đoán.
- **Dependency đã được duyệt** (thêm không cần hỏi, phiên bản để Spring Boot BOM quản lý; chỉ ghi version khi BOM không quản lý): các thư viện kể trên, `spring-boot-starter-security`, `spring-boot-starter-validation`, `thymeleaf-extras-springsecurity6` (hoặc bản tương ứng với Security 7, kiểm tra Maven Central), các starter test của Spring Boot 4, `spring-security-test`, `chart.js` (WebJars), `webjars-locator-lite`, `spring-boot-devtools` (đã có trong `pom.xml`). Không dùng Testcontainers (máy dev không chạy được Docker). Mọi thứ khác phải hỏi.

## 3. CẤU TRÚC DỰ ÁN
Package gốc `vn.edu.fpt.contactbook`:
- `config/` cấu hình (SecurityConfig, WebConfig, ...)
- `security/` UserDetailsService, principal, bộ lọc, đếm đăng nhập sai
- `model/` entity JPA; `model/enums/` enum
- `repository/` Spring Data JPA repository
- `service/` nghiệp vụ, `@Transactional`
- `dto/` form object, view object, request object
- `controller/` controller Thymeleaf, chia theo module
- `exception/` BusinessException, GlobalExceptionHandler
- `util/` PhoneUtils, PageUtils, ExcelUtils, ...

Tài nguyên: `templates/{layout,fragments,auth,admin,teacher,student,parent,common,error}`, `static/css`, `static/js`, `messages.properties` (tiếng Việt).

## 4. CƠ SỞ DỮ LIỆU VÀ JPA
- `spring.jpa.hibernate.ddl-auto=validate` ở mọi profile. `spring.jpa.open-in-view=false`; controller chỉ làm việc với DTO.
- Mọi entity có `@Table(name=...)` và `@Column(name=...)` khớp CHÍNH XÁC script. Bảng số ít `attendance`; các cột `type`, `comment`, `action`, `status`, `content` khai báo tên tường minh.
- Tên entity tránh trùng lớp Java: `classes` → `SchoolClass`, `requests` → `UserRequest`, `files` → `StoredFile`.
- Khóa chính `BIGINT IDENTITY` → `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Kiểu: `BIT` → `boolean`, `DATETIME2` → `LocalDateTime`, `DATE` → `LocalDate`, `TIME` → `LocalTime`, `DECIMAL` → `BigDecimal`, `SMALLINT` → `Short`, `TINYINT` → `Short` kèm `@JdbcTypeCode(SqlTypes.TINYINT)`. `NVARCHAR` → `String`.
- `NVARCHAR`: mọi cột chuỗi trong script là `nvarchar`, trong khi Hibernate mặc định ánh xạ `String` sang `varchar`, nên `validate` sẽ báo sai kiểu. Bật nationalized character data toàn cục qua `spring.jpa.properties.hibernate.use_nationalized_character_data=true`. Trước khi dùng phải kiểm tra lại tên thuộc tính trong tài liệu chính thức Hibernate 7, không đoán.
- DEFAULT của DB (`sysdatetime()`, giá trị `status` mặc định, `is_active=1`, `must_change_password=1`, `is_published=0`, ...) KHÔNG có tác dụng với JPA, vì Hibernate chèn tường minh mọi cột, kể cả NULL. Giá trị mặc định phải gán trong Java (khởi tạo field hoặc `@PrePersist`) cho khớp DEFAULT trong script.
- Cột có CHECK kiểu chuỗi → enum với `@Enumerated(EnumType.STRING)`, giá trị trùng từng ký tự với CHECK (ví dụ `AttendanceStatus`: PRESENT, LATE, ABSENT_EXCUSED, ABSENT_UNEXCUSED).
- Khóa ghép dùng `@EmbeddedId`: `parent_student`, `notification_recipients`, `notification_files`, `conversation_members`, `request_files`, `club_activity_participants`.
- Chỉ bảng có `created_at` / `updated_at` trong script mới có hai trường đó; gán bằng `@PrePersist` / `@PreUpdate`.
- Mã vai trò (ADMIN, HEAD_OF_DEPARTMENT, TEACHER, STUDENT, PARENT) là dữ liệu bảng `roles`; trong code dùng hằng số lớp `RoleCodes`.
- `validate` báo sai kiểu cột: chỉnh kiểu Java hoặc dùng `@JdbcTypeCode` / `@Column(columnDefinition=...)`, KHÔNG sửa script.
- `@ManyToOne(fetch = LAZY)`. Không cascade REMOVE, không `CascadeType.ALL` bừa bãi.
- Danh sách luôn phân trang (`Pageable`); tránh N+1 bằng `@EntityGraph` hoặc `JOIN FETCH`.

## 5. BẢO MẬT VÀ PHÂN QUYỀN
- Đăng nhập bằng số điện thoại (`users.phone`). `PhoneUtils` chuẩn hóa trước khi tra cứu: bỏ khoảng trắng, dấu chấm, gạch ngang; `+84` / `84` ở đầu đổi thành `0`.
- Mật khẩu BCrypt (strength 10). Không ghi mật khẩu rõ ra log hay cơ sở dữ liệu; không trả mật khẩu băm ra bất kỳ đâu.
- Một người dùng có nhiều vai trò (`user_roles`); quyền là hợp của mọi vai trò, không có chế độ chọn vai trò đang dùng.
- Phân quyền theo tên vai trò (`hasRole` / `@PreAuthorize`).
- Phạm vi dữ liệu BẮT BUỘC kiểm tra ở tầng service:
  - TEACHER chỉ thao tác lớp học phần mình dạy.
  - HEAD_OF_DEPARTMENT chỉ trong bộ môn gắn với vai trò (`user_roles.department_id`).
  - STUDENT chỉ xem dữ liệu của chính mình.
  - PARENT chỉ xem dữ liệu con đã liên kết (`parent_student`).
- CSRF bật cho mọi form, Thymeleaf tự chèn token. Không tắt CSRF. Ngoại lệ duy nhất: `SecurityFilterChain` riêng cho `/api/**`, stateless, xác thực bằng JWT (không dùng cookie phiên).
- Tài khoản `users.is_active = 0` không đăng nhập được.
- Lỗi đăng nhập luôn là "Số điện thoại hoặc mật khẩu không đúng", không tiết lộ số điện thoại có tồn tại hay không.
- Hành động quan trọng ghi `audit_logs` qua một `AuditService` duy nhất (SRS FR-SYS-01).

## 6. GIAO DIỆN
- Ngày `dd/MM/yyyy`, giờ `HH:mm`, múi giờ `Asia/Ho_Chi_Minh`, locale `vi_VN`.
- Màu (CSS variables trong `static/css/app.css`): navy `#0A1930`, cam `#F97316`, nền `#F8F7F4`, chữ `#1F2937`.
- Bố cục chung bằng fragments: `layout/base.html` gồm thanh trên, menu trái theo vai trò, vùng flash, vùng nội dung. Không lặp HTML khung.
- Responsive; chữ rõ, nút lớn (phụ huynh lớn tuổi dùng).
- Form dùng Bean Validation, lỗi tiếng Việt cạnh từng ô, thông báo trong `messages.properties`.
- Có trang lỗi 403, 404, 500 thân thiện.
- Không dùng `${@bean...}` trong template; dữ liệu do controller đưa vào Model, hoặc dùng `sec:authorize` / `#authentication`.
- Danh sách có phân trang, tìm kiếm, bộ lọc, giữ giá trị lọc khi chuyển trang. Xóa/khóa luôn có hộp thoại xác nhận.

## 7. CODE
- Controller → Service → Repository. Controller không chứa nghiệp vụ, không gọi repository.
- Service không phụ thuộc kiểu web (`HttpServletRequest`, `Model`, ...) để dùng lại cho REST API sau này.
- Lỗi nghiệp vụ ném `BusinessException` (thông báo tiếng Việt); `GlobalExceptionHandler` chuyển thành thông báo thân thiện. Lỗi ràng buộc cơ sở dữ liệu (trùng khóa duy nhất, CHECK) phải bắt và đổi thành thông báo dễ hiểu, không lộ lỗi SQL.
- Constructor injection (`@RequiredArgsConstructor`), không `@Autowired` trên field.
- Không lộ entity ra view; dùng DTO.
- Cấu hình nhạy cảm lấy từ biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; giá trị mặc định chỉ cho localhost. Không commit mật khẩu thật.

## 8. KIỂM THỬ (database test trên SQL Server local)
- Service: unit test JUnit 5 + Mockito, chỉ mock ranh giới bên ngoài.
- Repository và tích hợp (`@DataJpaTest` với `@AutoConfigureTestDatabase(replace = NONE)`, `@SpringBootTest` + MockMvc): chạy trên database `FptContactBook_test` của SQL Server localhost, profile `test`.
- Kết nối lấy từ biến môi trường `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`; mặc định chỉ trỏ tới localhost.
- Đầu mỗi lượt chạy test (một lần, không phải mỗi lớp test), một lớp khởi tạo dùng chung xóa và tạo lại `FptContactBook_test` từ chính `database/fpt_contact_book.sql`:
  - Script mã hóa UTF-16 LE có BOM, phải đọc đúng encoding.
  - Đọc script vào bộ nhớ, thay token `FptContactBook` thành `FptContactBook_test` ở toàn bộ script (gồm `CREATE DATABASE`, `ALTER DATABASE`, `USE`, `EXEC [..].[dbo]`, logical name và tên file `.mdf`/`.ldf` trong `FILENAME`). KHÔNG sửa file gốc, không lưu bản sao vào repo.
  - Script dùng `GO` làm dấu phân cách batch, JDBC không hiểu `GO`: tách batch theo dòng chỉ chứa `GO`.
  - Thao tác `DROP`/`CREATE DATABASE` chạy khi kết nối vào `master`.
- **Rào chắn an toàn BẮT BUỘC**, kiểm tra trước mọi lệnh `DROP DATABASE`: tên database phải kết thúc bằng `_test` VÀ máy chủ trong URL phải là `localhost`, `127.0.0.1` hoặc `.`/`(local)`. Sai một trong hai thì ném lỗi và dừng ngay, không chạy gì thêm. Phải có unit test cho chính rào chắn này.
- Mỗi test chạy trong transaction và rollback (`@Transactional` trên lớp test). Test cần commit thật thì phải tự dọn dữ liệu.
- Test tích hợp được dựa vào dữ liệu mẫu có sẵn trong script (mục 10).
- SQL Server local không chạy hoặc không kết nối được thì test tích hợp sẽ FAILED. KHÔNG dùng `@Disabled`, assumption hay cách nào khác để bỏ qua; báo NOT RUN kèm lý do và lệnh để chủ dự án tự chạy.
- Phân quyền: mỗi màn hình có test MockMvc cho đúng vai trò (200), sai vai trò (403), chưa đăng nhập (chuyển tới trang đăng nhập), và test phạm vi dữ liệu ở service (ví dụ TEACHER xem lớp không phải của mình bị chặn).

## 9. QUY TRÌNH MỖI GIAI ĐOẠN
1. Đọc lại phần SQL và SRS liên quan, trình kế hoạch (theo mẫu rule global, thêm danh sách tiêu chí nghiệm thu). CHỜ duyệt.
2. Tạo nhánh `feature/phase-<số>-<mô-tả-ngắn>` từ nhánh chính.
3. Viết code và test theo kế hoạch đã duyệt.
4. Tự chạy `./mvnw clean verify` (hoặc `mvn clean verify` nếu không có wrapper), rồi khởi động ứng dụng với database localhost và kiểm tra từng tiêu chí nghiệm thu.
5. Commit khi mọi thứ PASSED (theo rule git global). Không push.
6. Báo cáo theo mẫu rule global, thêm: cách chạy, kết quả từng tiêu chí nghiệm thu, giả định mới đã ghi vào `docs/ASSUMPTIONS.md`, việc còn dang dở.
7. DỪNG. Không tự làm sang giai đoạn kế tiếp.

## 10. DỮ LIỆU THỬ (có sẵn trong script, mật khẩu chung `Fpt@12345`, chỉ dùng cho dev/test)
- `0900000001`: ADMIN
- `0900000002`: TEACHER + HEAD_OF_DEPARTMENT (bộ môn SE)
- `0900000003`: TEACHER
- `0900000004`: STUDENT (SE190001)
- `0900000005`: PARENT (cha của SE190001)
