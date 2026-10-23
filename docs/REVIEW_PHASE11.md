# Rà soát và kiểm thử tổng – Giai đoạn 11

Ngày rà soát: 23/10/2026. Nhánh `feature/phase-11-release-packaging`, tách từ `main` tại `3cf2642` (đã gồm 10d-3).
Phần web đã rà soát ở `docs/REVIEW_PHASE8.md`; tài liệu này phủ REST API cho mobile (từ 9b), app Flutter (từ 10a) và
cấu hình đóng gói Android.

## 1. Phạm vi và cách làm

- **Skill đã dùng:** `concise-planning` (kế hoạch), `security-review` (phần 2), `ponytail-audit` chỉ cho `mobile/`
  (phần 3; web đã rà ở 8a), `test-driven-development` (hai lỗi sửa ở phần 3: viết test thấy FAILED rồi mới sửa).
- **Bảo mật:** đọc `ApiSecurityConfig`, `JwtTokenService`, `ApiAuthService`, `ApiPrincipalConverter`, mã đặt lại mật
  khẩu, mọi controller dưới `controller/api/**` và các hàm kiểm tra phạm vi ở service; trong app: lưu token, log,
  tải và mở tệp, làm mới token; cấu hình Android mới của giai đoạn này. Chỉ báo phát hiện có độ tin cậy từ 8/10.
- **Chất lượng mobile:** lần theo các luồng đăng nhập/làm mới token, đổi con, lịch tuần, điểm và điểm danh, thông
  báo và tệp đính kèm, đơn nghỉ, điểm danh của giáo viên, duyệt đơn, gửi thông báo tổ. Tải giả định: mỗi máy một
  người dùng, một máy có thể được hai tài khoản trong gia đình dùng lần lượt.

## 2. Bảo mật: không có phát hiện mức Cao hoặc Trung bình

Đã kiểm tra và không thấy lỗi:
- Chuỗi bộ lọc `/api/**`: chỉ đăng nhập, làm mới token, hai bước quên mật khẩu và OpenAPI là công khai; vai trò chặn
  theo đường dẫn (`/student`, `/parent`, `/teacher`, `/head`); stateless, CSRF chỉ tắt ở chuỗi này; không cấu hình
  CORS nên trình duyệt ở nguồn khác không gọi được.
- JWT: HS256 cố định ở cả bộ ký và bộ giải; kiểm tra issuer và hạn; access token và refresh token có decoder riêng
  theo `token_type` nên không dùng thay nhau được; refresh kiểm tra dấu vân tay mật khẩu bằng so sánh thời gian cố
  định; mỗi request nạp lại người dùng từ database (tài khoản bị khóa nhận 401).
- Mã đặt lại mật khẩu: HMAC gắn người dùng, dấu vân tay mật khẩu và khung 10 phút; phản hồi như nhau dù số điện
  thoại có tồn tại hay không; đếm nhập sai theo số đã chuẩn hóa.
- Phạm vi dữ liệu: học sinh chỉ dữ liệu của mình, phụ huynh chỉ con đã liên kết, giáo viên chỉ lớp mình dạy (buổi
  học phải thuộc đúng lớp trên đường dẫn), tổ trưởng chỉ tổ mình; đơn từ, thông báo, tệp đính kèm kiểm tra người
  xem trước khi đọc dữ liệu.
- Tệp: tải về chỉ nhận khóa `attachments/<UUID>.<đuôi>` nên không có path traversal; tải lên kiểm tra đuôi,
  Content-Type và chữ ký đầu tệp; phản hồi JSON không có mật khẩu băm hay đường dẫn lưu tệp; lỗi không lộ SQL.
- App: token chỉ trong `flutter_secure_storage`; không log token, mật khẩu hay nội dung phản hồi; không tắt kiểm tra
  chứng chỉ; refresh token chỉ gửi tới cùng `baseUrl`; tên tệp từ server được làm sạch trước khi ghi vào cache.
- Android: `allowBackup="false"`, không `debuggable`, chỉ `MainActivity` exported, đã gỡ quyền đọc bộ nhớ; cấu hình
  mạng của bản release chỉ HTTPS (phần 4).

Ghi nhận theo thiết kế, không phải lỗi: OpenAPI công khai khi chạy localhost (giả định 133; cách tắt ở
`docs/INSTALL_MOBILE.md` mục 2), refresh token không thu hồi riêng được (giả định 130).

## 3. Chất lượng app mobile

Không có lỗi phải sửa gấp (không crash, không lộ dữ liệu sang tài khoản khác ở mức API). Mã `Q` để chọn mục cần sửa.

| Mã | Phát hiện | Xử lý |
|---|---|---|
| Q1 | Hàng đợi "Chờ duyệt" tải trang theo vị trí. Duyệt một đơn rồi cuộn tải thêm thì đơn đứng ngay sau ranh giới trang bị bỏ sót (ví dụ 21 đơn: duyệt một đơn ở trang đầu thì đơn thứ 21 không bao giờ hiện cho tới khi kéo tải lại). | **Đã sửa** (`request_review_controller.dart`): đơn rời hàng đợi khi còn trang sau thì lần tải thêm kế tiếp tải lại từ trang đầu. Test `loading more after an approval skips no pending request` |
| Q2 | Màn hình CLB của phụ huynh đổi sang con khác (hoặc tài khoản khác đăng nhập trên cùng máy) vẫn hiện "CLB của con" của người trước trong lúc chờ phản hồi. | **Đã sửa** (`club_controller.dart`): `open()` xóa danh sách cũ. Test `switching child hides the clubs of the previous one` |
| Q3 | Kéo xuống trên bảng điểm danh đang sửa (hoặc nút "Tải lại bảng") tải lại bảng và mất các lựa chọn chưa lưu, không hỏi. | Chưa sửa: cần thêm hộp xác nhận (thay đổi giao diện), đề xuất làm ở đợt sau |
| Q4 | Bấm lại tab đang mở hoặc nút back trên form đơn nghỉ, form gửi thông báo tổ, bảng điểm danh làm mất nội dung đang soạn mà không hỏi. | Chưa sửa: cần một hộp "Bỏ nội dung đang soạn?" dùng chung, đề xuất làm cùng Q3 |
| Q5 | Lưu điểm danh chậm, giáo viên kịp mở buổi khác thì màn hình buổi mới có thể hiện bảng của buổi vừa lưu (`save()` không kiểm tra lượt mở như `open()`). | Chưa sửa, hiếm gặp |
| Q6 | Chuyển tuần lỗi mạng rồi bấm "Thử lại" thì về tuần cũ thay vì tuần định xem. | Chưa sửa, ảnh hưởng nhỏ |
| Q7 | Hàm tải và mở tệp đính kèm chép ở 4 controller; tách lỗi theo ô của backend chép ở 2 controller. | Chưa sửa: gom thành helper chung là refactor, ngoài phạm vi |

## 4. Đóng gói Android (thay đổi của giai đoạn này)

- Bản release ký bằng khóa trong `android/key.properties`; thiếu file hoặc thiếu khóa thì build dừng (giả định 185).
- Địa chỉ server: Gradle chặn lúc build release, `AppConfig` chặn lúc chạy (giả định 187). Các trường hợp đã thử
  bằng `flutter build apk --release`, đều dừng đúng thông báo: không có `API_BASE_URL`; `http://` không có host demo;
  `DEMO_CLEARTEXT_HOST` không kèm thuộc tính Gradle; host demo là IP công khai (`8.8.8.8`); `API_BASE_URL` trỏ IP
  khác host demo (`192.168.1.200` so với `192.168.1.20`); thiếu `DEMO_CLEARTEXT_HOST`; thiếu `key.properties`.
- APK demo LAN chỉ được HTTP tới một IPv4 nội bộ (giả định 186). Bản debug dùng cấu hình mạng riêng (giả định 188).

## 5. Kiểm thử tổng

| Lệnh | Kết quả |
|---|---|
| `mvn clean verify` (gốc, SQL Server localhost, `FptContactBook_test`) | PASSED: 1398 run / 1398 passed / 0 failed / 0 skipped |
| `flutter analyze` (`mobile/`) | PASSED: không có vấn đề |
| `flutter test` (`mobile/`, gồm golden test) | PASSED: 395 run / 395 passed / 0 failed / 0 skipped |
| `flutter test test_contract` (backend chạy trên localhost:8080) | PASSED: 49 run / 49 passed / 0 failed / 0 skipped |
| `flutter build apk --debug` | PASSED; manifest đã gộp trỏ `@xml/network_security_config`, bản debug lấy cấu hình cho phép HTTP của `src/debug` |
| Backend với `SPRINGDOC_APIDOCS_ENABLED=false`, `SPRINGDOC_SWAGGERUI_ENABLED=false` | `/v3/api-docs`, `/swagger-ui.html` trả 404 |
| `flutter build apk --release` có khóa ký, `apksigner verify` | NOT RUN: chưa có keystore và `key.properties` (chủ dự án tạo theo `docs/INSTALL_MOBILE.md` mục 3) |
| `tool/build_demo_apk.ps1` | NOT RUN: cùng lý do |
| Chạy APK release/demo trên máy ảo Pixel_6a (5 tài khoản mẫu) | NOT RUN: cần APK đã ký ở hai dòng trên |

Khi có khóa ký, chạy trong `mobile/`:
```powershell
flutter build apk --release --dart-define=API_BASE_URL=https://<tên miền>
.\tool\build_demo_apk.ps1 -HostIp 10.0.2.2
```
rồi cài `app-demo-lan.apk` lên máy ảo (backend chạy trên máy dev) và kiểm tra: đăng nhập 5 tài khoản mẫu; lịch,
điểm, điểm danh, thông báo kèm mở tệp; gửi đơn nghỉ kèm ảnh; giáo viên điểm danh và duyệt đơn; tổ trưởng gửi thông
báo; đăng xuất xóa token. Chưa kiểm tra trên máy thật: R8 thu gọn code bản release có làm hỏng plugin hay không, và
`domain-config` với địa chỉ IP trong cấu hình mạng của bản demo.
