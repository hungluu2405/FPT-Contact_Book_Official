# Sổ liên lạc điện tử – Đại học FPT (Phân hệ Web)

Hệ thống Sổ liên lạc điện tử Đại học FPT được phát triển trên nền tảng Spring Boot và Thymeleaf, kết nối cơ sở dữ liệu Microsoft SQL Server.

---

## 1. Yêu cầu môi trường & Cài đặt

- **Java Development Kit (JDK)**: Phiên bản 21 trở lên (khuyến nghị JDK 21).
- **Apache Maven**: Phiên bản 3.9+ trở lên.
- **Cơ sở dữ liệu**: Microsoft SQL Server 2019 / 2022 hoặc SQL Server Express.

---

## 2. Hướng dẫn tạo cơ sở dữ liệu

1. Mở công cụ quản trị SQL Server (SSMS, Azure Data Studio hoặc tiện ích dòng lệnh `sqlcmd`).
2. Chạy toàn bộ nội dung tập tin kịch bản:
   ```bash
   sqlcmd -S localhost -E -i database/fpt_contact_book.sql
   ```
3. Kịch bản sẽ tạo cơ sở dữ liệu `FptContactBook` gồm 36 bảng chuẩn hóa, các ràng buộc toàn vẹn và dữ liệu mẫu thử nghiệm.
4. Chạy lần lượt các file trong `database/migrations/` theo thứ tự tên (`V001__…`, `V002__…`). Mỗi file chỉ thêm
   thay đổi mới (không sửa script gốc), có kiểm tra tồn tại nên chạy lại cũng an toàn:
   ```bash
   sqlcmd -S localhost -E -f 65001 -i database/migrations/V001__audit_log_indexes.sql
   ```
   Test tự động tự chạy script gốc rồi các migration này trên database `FptContactBook_test`.

---

## 3. Biến môi trường cấu hình (Environment Variables)

Hệ thống cấu hình kết nối linh hoạt thông qua các biến môi trường:

| Biến môi trường | Giá trị mặc định (cho môi trường phát triển local) | Mô tả |
|---|---|---|
| `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=FptContactBook;encrypt=true;trustServerCertificate=true` | Chuỗi kết nối JDBC SQL Server |
| `DB_USERNAME` | `<tên login SQL Server>` | Tên đăng nhập cơ sở dữ liệu. Nên tạo login riêng chỉ có quyền trên `FptContactBook` (và `FptContactBook_test` khi chạy test), không dùng `sa` |
| `DB_PASSWORD` | *(không có mặc định)* | Mật khẩu của login trên; chỉ đặt bằng biến môi trường, không ghi vào mã nguồn hay tài liệu |

*Lưu ý: Không lưu mật khẩu sản xuất vào mã nguồn hoặc tập tin `.properties`. Tham khảo mẫu tại `src/main/resources/application-example.properties`.*

---

## 4. Hướng dẫn biên dịch và khởi chạy

### Biên dịch và chạy kiểm thử tự động
```bash
mvn clean verify
```

### Khởi chạy ứng dụng
```bash
mvn spring-boot:run
```
Hoặc đóng gói và chạy file JAR:
```bash
mvn clean package
java -jar target/fpt-contact-book-0.0.1-SNAPSHOT.jar
```

Sau khi máy chủ khởi động thành công, truy cập giao diện tại:
```
http://localhost:8080/
```

---

## 5. Cấu trúc thư mục dự án

```text
c:\Project\fpt_contact_book\
├── database/
│   ├── fpt_contact_book.sql       # Script SQL Server 36 bảng (nguồn sự thật cấu trúc dữ liệu)
│   └── migrations/                # Thay đổi bổ sung sau script gốc, chạy theo thứ tự tên file
├── docs/
│   ├── SRS.md                     # Tài liệu đặc tả yêu cầu phần mềm
│   └── ASSUMPTIONS.md             # Danh sách các giả định nghiệp vụ & kỹ thuật
├── src/
│   ├── main/
│   │   ├── java/vn/edu/fpt/contactbook/
│   │   │   ├── ContactBookApplication.java  # Lớp khởi chạy Spring Boot
│   │   │   ├── config/                      # Cấu hình hệ thống (SecurityConfig, WebConfig,...)
│   │   │   ├── security/                    # Phân quyền, UserDetailsService, Authentication filters
│   │   │   ├── model/                       # JPA Entities & model/enums/
│   │   │   ├── repository/                  # Spring Data JPA Repositories
│   │   │   ├── service/                     # Nghiệp vụ hệ thống (@Transactional)
│   │   │   ├── dto/                         # DTO, Form objects, View objects
│   │   │   ├── controller/                  # Spring MVC Thymeleaf Controllers
│   │   │   ├── exception/                   # BusinessException, GlobalExceptionHandler
│   │   │   └── util/                        # Tiện ích (PhoneUtils, PageUtils, ExcelUtils,...)
│   │   └── resources/
│   │       ├── application.properties       # Cấu hình môi trường chính
│   │       ├── application-example.properties # Cấu hình mẫu không chứa bí mật
│   │       ├── messages.properties          # Tài nguyên ngôn ngữ tiếng Việt
│   │       ├── static/                      # css/app.css, js/, hình ảnh
│   │       └── templates/                   # Giao diện Thymeleaf chia theo module
│   │           ├── layout/                  # Bố cục chung base.html
│   │           ├── fragments/               # Thanh trên, menu, thông báo flash
│   │           ├── auth/                    # Đăng nhập, đổi mật khẩu
│   │           ├── admin/                   # Màn hình quản trị
│   │           ├── teacher/                 # Màn hình giảng viên
│   │           ├── student/                 # Màn hình sinh viên
│   │           ├── parent/                  # Màn hình phụ huynh
│   │           ├── common/                  # Màn hình dùng chung
│   │           └── error/                   # Trang lỗi 403, 404, 500
│   └── test/
│       └── java/vn/edu/fpt/contactbook/     # Kiểm thử tự động (Unit / Slice tests)
├── .gitignore                               # Quy tắc loại trừ tệp nhạy cảm và build artifacts
├── pom.xml                                  # Cấu hình Maven & dependencies
└── README.md                                # Hướng dẫn dự án
```
