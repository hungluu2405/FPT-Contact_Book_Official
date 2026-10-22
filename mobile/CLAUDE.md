# Sổ liên lạc điện tử Đại học FPT (Mobile, Flutter)

App Android cho sinh viên (SV), phụ huynh (PH) và giảng viên (GV). File này áp dụng cho mọi file trong `mobile/` và bổ sung `~/.claude/CLAUDE.md`. Các mục 0 (ghi đè rule global), 1 (nguồn sự thật) và 9 (quy trình mỗi giai đoạn) của `CLAUDE.md` gốc vẫn áp dụng. Lộ trình: `docs/MOBILE_ROADMAP.md`.

## 1. DỮ LIỆU
- App **không có database riêng**: không SQLite, không Hive, không chế độ offline. Mọi dữ liệu nghiệp vụ đọc và ghi qua REST API của backend (`/api/v1/**`). Backend lưu vào database SQL Server `FptContactBook`, chung với web.
- Chỉ được lưu trên máy: token và cài đặt nhỏ của app. Không lưu điểm, thông báo hay hồ sơ xuống máy.
- Hợp đồng API: `docs/API.md` (quy ước token, mã lỗi, phân trang) và tài liệu OpenAPI của backend (`/v3/api-docs`). Không đoán tên trường; thiếu API thì dừng và đề xuất thêm ở backend.
- Phân quyền và phạm vi dữ liệu do backend kiểm tra. App chỉ ẩn hoặc hiện chức năng theo vai trò, không thay thế việc kiểm tra đó.

## 2. CÔNG NGHỆ
- Flutter stable, Dart theo `pubspec.yaml`. Chỉ phát triển và kiểm thử **Android**. Các thư mục ios, web, windows, macos, linux giữ nguyên, không sửa.
- Quản lý trạng thái: `flutter_riverpod`. HTTP: `dio`.
- Đã duyệt nhưng chưa thêm: `flutter_riverpod`, `dio`. Chờ duyệt: `flutter_secure_storage`, `go_router`, `intl`. Mọi package khác (kể cả dev) phải hỏi, kèm lý do, license và tình trạng bảo trì.
- Địa chỉ server truyền qua `--dart-define=API_BASE_URL=...`, không viết cứng trong code. Máy ảo Android gọi backend local qua `http://10.0.2.2:8080`.
- HTTP thường (cleartext) chỉ bật cho bản debug. Bản release dùng HTTPS. Quyền `INTERNET` phải có trong manifest `src/main`.

## 3. CẤU TRÚC (feature-first, áp dụng từ giai đoạn 10a)
- `lib/core/`: theme (màu, chữ), API client, xử lý lỗi, lưu token, router.
- `lib/features/<chức năng>/`: `data/` (gọi API, model), `presentation/` (màn hình, widget, provider).
- Màu lấy từ theme, không import `main.dart` để lấy màu.
- Hiện code còn ở `lib/vn/edu/fpt/screen/...` và package tên `myfschoolse1945`. Giai đoạn 10a sẽ đổi sang package `fpt_contact_book_mobile`, applicationId `vn.edu.fpt.contactbook`.

## 4. NGHIỆP VỤ (theo SRS mục 8)
- SV: thời khóa biểu, lịch thi, điểm (chỉ cột đã công bố), điểm danh, thông báo, đơn nghỉ.
- PH: như SV, cho từng con đã liên kết; có chọn con.
- GV: lịch dạy, điểm danh một buổi, duyệt đơn nghỉ.
- Ngoài phạm vi: đăng ký, quên mật khẩu (hiện "Liên hệ quản trị viên"), nhập hoặc sửa điểm, lịch, danh mục, offline. Push notification, tin nhắn, CLB chỉ làm khi được duyệt.
- Đăng nhập bằng số điện thoại. Lỗi đăng nhập hiển thị đúng thông báo backend trả về. `must_change_password` bắt buộc đổi mật khẩu trước khi vào app.

## 5. GIAO DIỆN
- Chữ trên giao diện bằng tiếng Việt. Tên lớp, biến, hàm, log, commit bằng tiếng Anh.
- Ngày `dd/MM/yyyy`, giờ `HH:mm`, múi giờ `Asia/Ho_Chi_Minh`.
- Màu như web (mục 6 `CLAUDE.md` gốc): nền trắng, mọi nút chính màu cam `#F97316`, cam đậm `#C2410C` cho link chữ nhỏ, cam nhạt `#FFF7ED` cho mảng nền, navy `#0A1930` chỉ cho chữ tiêu đề lớn, chữ `#1F2937`, chữ phụ `#6B7280`.
- Chữ rõ, nút lớn (phụ huynh lớn tuổi dùng). Mọi màn hình có trạng thái đang tải, rỗng, lỗi kèm nút thử lại.
- Danh sách dài thì tải theo trang, đúng với phân trang của API.

## 6. BẢO MẬT
- Token chỉ lưu trong secure storage, không dùng `SharedPreferences`. Đăng xuất phải xóa token.
- Không log mật khẩu, token, header `Authorization` hay nội dung phản hồi có dữ liệu cá nhân.
- Không commit `key.properties`, file `.jks`/`.keystore` hay `google-services.json` thật.

## 7. KIỂM THỬ VÀ HOÀN THÀNH
- Chạy trong `mobile/`: `flutter analyze` (không có error/warning mới) và `flutter test` (PASSED).
- Unit test cho model và repository (mock ở ranh giới HTTP), widget test cho màn hình chính; không test rỗng.
- Thay đổi chạm vào backend thì chạy thêm `mvn clean verify` ở gốc theo `CLAUDE.md` gốc.
- Nhánh `feature/phase-<số>-<mô-tả>`, commit theo rule global, không push.
