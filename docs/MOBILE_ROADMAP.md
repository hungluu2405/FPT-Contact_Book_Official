# Lộ trình Mobile

Lập ngày 20/10/2026. Quy tắc làm việc cho app nằm ở `mobile/CLAUDE.md`; file này ghi các quyết định và thứ tự giai đoạn.

## 1. Quyết định đã chốt
- **Repo chung**: app Flutter nằm ở `fpt_contact_book/mobile/`.
- **Đối tượng**: học sinh (HS), phụ huynh (PH), giáo viên (GV). **Chỉ làm Android.**
- **Dữ liệu**: mobile chỉ gọi REST API của backend Spring Boot. API đọc và ghi đúng database SQL Server `FptContactBook` mà web đang dùng. Mobile **không có database riêng**, không có SQLite hay chế độ offline. Có thể lưu cache trên máy, nhưng không có dữ liệu nghiệp vụ nào chỉ nằm trên điện thoại.
- **Gộp repo**: **chỉ chép file**, không giữ lịch sử git của repo cũ `hungluu2405/Fpt-Contact_Book_Flutter`. Không chép `.git`, `.dart_tool/`, `build/`, `.idea/`, `*.iml`.
- **Thư mục nền tảng**: **giữ tất cả** (android, ios, web, windows, macos, linux), nhưng chỉ phát triển và kiểm thử Android.
- **2 file đang sửa dở** trong `C:\Users\pooo\myfschoolse1945` (`lib/vn/edu/fpt/screen/account/login_screen.dart`, `lib/vn/edu/fpt/screen/widget/auth_widget.dart`): Claude tự commit nguyên trạng trong repo cũ trước khi chép. Rule global cấm commit trên `main`, nên làm trên nhánh `chore/wip-login-ui`.
- **Đổi tên**: package Dart `myfschoolse1945` thành `fpt_contact_book_mobile`; applicationId/namespace `com.example.myfschoolse1945` thành `vn.edu.fpt.contactbook`.
- **Thư viện mobile** (chốt ở 10a): `dio` và `flutter_secure_storage`. Không dùng Riverpod (controller dùng `ChangeNotifier` theo cấu trúc MVC) và chưa dùng `go_router`; `intl` thêm khi cần hiển thị ngày.
- **Cấu trúc** (chốt ở 10a): MVC dưới `lib/vn/edu/fpt/` gồm `screen/`, `controller/`, `model/`, cùng `service/` và `util/` (xem `mobile/CLAUDE.md` mục 3).
- **Thư viện backend** (đã duyệt ở 9b): `spring-boot-starter-security-oauth2-resource-server` thay cho `jjwt`, và `springdoc-openapi-starter-webmvc-ui` 3.1.1.

## 2. Hiện trạng project Flutter (đã khảo sát)
- 3 file Dart: `main.dart` (chứa màu và `MaterialApp`), `login_screen.dart`, `auth_widget.dart`. Chỉ có giao diện, chưa gọi API.
- Dependency hiện có: `cupertino_icons`, `flutter_lints`. SDK `^3.13.2`. Asset nằm ở `assets/images/`.
- Màu khớp với web lúc khảo sát: navy `#0A1930`, cam `#F97316`, nền `#F8F7F4`; ngoài ra có `fptBlue` và `fptGreen`. Từ 10a app theo bộ màu trắng và cam mới (`mobile/CLAUDE.md` mục 5).
- Những chỗ cần sửa:
  - Nút "Quên mật khẩu?": SRS mục 8 không có chức năng này trên mobile, nên bỏ và thay bằng dòng "Liên hệ quản trị viên".
  - Phụ đề "dành cho học sinh" là sai, vì cả HS, PH và GV đều dùng app.
  - `auth_widget.dart` import `main.dart` để lấy màu: tách màu ra file theme riêng.
  - `test/widget_test.dart` vẫn là test Counter mặc định và đang FAIL.
  - Quyền `INTERNET` chỉ có trong manifest debug/profile, chưa có ở `src/main`, nên bản release không gọi được mạng.
  - Chưa cho phép HTTP thường (cleartext). Máy ảo gọi backend local qua `http://10.0.2.2:8080`; chỉ bản debug được dùng cleartext.

## 3. Lộ trình (mỗi giai đoạn một phiên: lập kế hoạch, chờ duyệt, làm, kiểm tra, báo cáo, DỪNG)
- **Bước 0 (người dùng tự làm)**: merge `fix/phase-8b-review-fixes` vào `main`; đổi mật khẩu `sa` và tạo login riêng; chạy `database/migrations/V001__audit_log_indexes.sql` trên DB dev.
- **9a: gộp repo và đặt quy tắc (đã xong, nhánh `feature/phase-9a-mobile-import`)**
  - Repo cũ: 2 file dở đã commit trên nhánh `chore/wip-login-ui` (`84b6b13`).
  - Đã chép code vào `mobile/`, không kèm `.git`, `.dart_tool`, `build`, `.idea`, `*.iml`, `local.properties`. Các `.gitignore` của Flutter (`mobile/.gitignore`, `mobile/android/.gitignore`, ...) vẫn giữ và có hiệu lực trong thư mục con, nên không cần sửa `.gitignore` gốc.
  - Đã sửa `CLAUDE.md` gốc (phạm vi, REST API từ 9b, ngoại lệ CSRF cho API JWT) và viết `mobile/CLAUDE.md`.
  - Đã thay test Counter bằng smoke test màn hình đăng nhập.
  - Người dùng tự archive repo GitHub cũ.
- **9b: nền tảng API (backend) (đã xong, nhánh `feature/phase-9b-api-foundation`; hợp đồng ở `docs/API.md`, giả định 129–134)**
  - `SecurityFilterChain` riêng cho `/api/**`: stateless, xác thực JWT, lỗi trả JSON.
  - Đăng nhập bằng số điện thoại, dùng lại `PhoneAuthenticationProvider`, `LoginAttemptService` và audit (LOGIN_FAILED, LOGIN_LOCKED). Có refresh token, đăng xuất, đổi mật khẩu (gồm cả trường hợp `must_change_password`).
  - Chuẩn hóa dạng lỗi và dạng phân trang; OpenAPI dùng làm hợp đồng cho mobile.
  - Service dùng chung với web; dữ liệu nằm cùng database SQL Server.
- **9c: API cho HS và PH (đã xong, nhánh `feature/phase-9c-student-parent-api`; giả định 135–138)**
  - Hồ sơ, danh sách con (PH), thời khóa biểu, lịch thi, điểm (chỉ cột đã công bố), điểm danh.
  - Thông báo: danh sách, đánh dấu đã đọc, tải tệp.
  - Đơn nghỉ: gửi, xem, hủy.
  - Phạm vi dữ liệu kiểm tra ở service: STUDENT chỉ xem của mình, PARENT chỉ xem con đã liên kết.
- **9d: API cho GV (đã xong, nhánh `feature/phase-9d-teacher-api`; giả định 139–140)**
  - Lịch dạy, các lớp đang dạy, điểm danh một buổi (xem và lưu), duyệt hoặc từ chối đơn nghỉ.
  - TEACHER chỉ thao tác lớp mình dạy.
  - Nhập điểm, sửa lịch, danh mục chỉ làm trên Web.
- **10a: khung app (đã xong, nhánh `feature/phase-10a-app-shell`; giả định 147–151)**
  - Đổi tên package/applicationId; sắp xếp lại cấu trúc theo MVC (`screen`, `controller`, `model`, `service`, `util`); tách theme.
  - Quyền `INTERNET` trong manifest main; cleartext chỉ cho debug.
  - API client: tự gắn và làm mới token; lỗi mạng hiện thông báo kèm nút thử lại.
  - Nối màn hình đăng nhập với API; thêm màn hình bắt buộc đổi mật khẩu và đăng xuất.
  - Thay test Counter bằng test thật.
- **10b: màn hình HS và PH** (nhánh `feature/phase-10b-student-parent`, giả định 157–160)
  - 10b-1 (đã xong): trang chủ theo mockup (ảnh trường, lưới 6 chức năng), chọn con, thanh điều hướng dưới, tab Tài khoản.
  - 10b-2 (đã xong, nhánh `feature/phase-10b-schedule`, giả định 161–164): lịch học theo tuần theo mockup, danh sách buổi cả học kỳ, lịch thi, thẻ "Lịch học hôm nay" trên trang chủ.
  - 10b-3 (đã xong, nhánh `feature/phase-10b-grades-attendance`, giả định 166–168): điểm theo học kỳ (mở từng đầu điểm), điểm danh các môn và từng buổi, ô chọn học kỳ.
  - 10b-4 (đã xong, nhánh `feature/phase-10b-clubs`, giả định 169–170): CLB chỉ đọc, 5 API ở backend (`/clubs`, chi tiết, thành viên, hoạt động sắp tới, CLB của tôi / của con) và hai màn hình mobile.
- **10c: thông báo và đơn nghỉ**: số chưa đọc, chi tiết thông báo, tải tệp; gửi đơn nghỉ kèm ảnh hoặc tệp minh chứng.
- **10d: màn hình GV**: lịch dạy, điểm danh một buổi, duyệt đơn nghỉ.
- **11: kiểm thử tổng và đóng gói**: chạy trên máy ảo và máy thật; rà soát bảo mật; build APK release; viết hướng dẫn cài đặt và cấu hình địa chỉ server.

## 4. Ngoài phạm vi
Đăng ký, nhập/sửa điểm, lịch và danh mục trên mobile (quên mật khẩu qua email đã thêm, giả định 154–156); chế độ offline; push notification; tin nhắn trên mobile (CLB chỉ đọc đã duyệt ở 10b-4). Các mục này có thể thêm sau nếu duyệt.

## 5. Ràng buộc vẫn giữ
- Không push.
- Không commit trên `main`.
- Không in hoặc commit secret.
- Lập kế hoạch và chờ duyệt trước khi sửa file.
- Dependency cần được duyệt.
- Database chỉ trên localhost; không chạy DDL/DML bằng dòng lệnh.
- Không sửa `database/fpt_contact_book.sql`.

## 6. Câu mở đầu cho phiên kế tiếp
"Đọc `docs/MOBILE_ROADMAP.md`, `docs/API.md` và `mobile/CLAUDE.md`, rồi trình kế hoạch chi tiết giai đoạn 10b để tôi duyệt."
