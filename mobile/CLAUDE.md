# Sổ liên lạc điện tử Đại học FPT (Mobile, Flutter)

App Android cho sinh viên (SV), phụ huynh (PH) và giảng viên (GV). File này áp dụng cho mọi file trong `mobile/` và bổ sung `~/.claude/CLAUDE.md`. Các mục 0 (ghi đè rule global), 1 (nguồn sự thật) và 9 (quy trình mỗi giai đoạn) của `CLAUDE.md` gốc vẫn áp dụng. Lộ trình: `docs/MOBILE_ROADMAP.md`.

## 1. DỮ LIỆU
- App **không có database riêng**: không SQLite, không Hive, không chế độ offline. Mọi dữ liệu nghiệp vụ đọc và ghi qua REST API của backend (`/api/v1/**`). Backend lưu vào database SQL Server `FptContactBook`, chung với web.
- Chỉ được lưu trên máy: token và cài đặt nhỏ của app. Không lưu điểm, thông báo hay hồ sơ xuống máy.
- Hợp đồng API: `docs/API.md` (quy ước token, mã lỗi, phân trang) và tài liệu OpenAPI của backend (`/v3/api-docs`). Không đoán tên trường; thiếu API thì dừng và đề xuất thêm ở backend.
- Phân quyền và phạm vi dữ liệu do backend kiểm tra. App chỉ ẩn hoặc hiện chức năng theo vai trò, không thay thế việc kiểm tra đó.

## 2. CÔNG NGHỆ
- Flutter stable, Dart theo `pubspec.yaml`. Chỉ phát triển và kiểm thử **Android**. Các thư mục ios, web, windows, macos, linux giữ nguyên, không sửa.
- Quản lý trạng thái: controller kế thừa `ChangeNotifier` (có sẵn trong Flutter), màn hình nghe bằng `ListenableBuilder`. Không dùng Riverpod. Điều hướng bằng `Navigator` sẵn có.
- Đã thêm (10a): `dio` (HTTP), `flutter_secure_storage` (lưu token). Chưa thêm: `go_router`, `intl`, chỉ thêm khi cần và được duyệt. Mọi package khác (kể cả dev) phải hỏi, kèm lý do, license và tình trạng bảo trì.
- Địa chỉ server truyền qua `--dart-define=API_BASE_URL=...`, không viết cứng trong code. Máy ảo Android gọi backend local qua `http://10.0.2.2:8080`.
- HTTP thường (cleartext) chỉ bật cho bản debug. Bản release dùng HTTPS. Quyền `INTERNET` phải có trong manifest `src/main`.

## 3. CẤU TRÚC (MVC, áp dụng từ giai đoạn 10a)
Package Dart `fpt_contact_book_mobile`, applicationId `vn.edu.fpt.contactbook`. Code nằm dưới `lib/vn/edu/fpt/`:
- `screen/`: màn hình (View), tên `<hành động>_<đối tượng>_screen.dart`, ví dụ `add_user_screen.dart`, `list_user_screen.dart`, `edit_user_screen.dart`, `view_user_screen.dart`. Chia thư mục con theo nhóm (`account/`, `home/`, ...); widget dùng chung ở `screen/widget/`.
- `controller/`: mỗi nghiệp vụ một file `<đối tượng>_controller.dart`, các màn hình của nghiệp vụ đó dùng chung (ví dụ bốn màn hình User dùng `user_controller.dart`). Controller kế thừa `ChangeNotifier`, giữ trạng thái đang tải, lỗi, dữ liệu; được truyền vào màn hình qua tham số constructor.
- `model/`: `<đối tượng>_model.dart`, dùng làm tham số truyền dữ liệu giữa các file. Field private, có `get`/`set` tường minh, `fromJson`/`toJson` đúng tên trường của API (lint `unnecessary_getters_setters` đã tắt cho quy ước này).
- `service/`: gọi API (`ApiClient` tự gắn và làm mới token), lưu token. Controller gọi service, không gọi HTTP trực tiếp.
- `util/`: màu (`AppColors`), theme, cấu hình (`AppConfig`).
- Màu lấy từ `AppColors`/theme, không import `main.dart` để lấy màu.
- Test đặt trong `test/` theo cùng tầng (`model/`, `service/`, `controller/`, widget test); giả lập ở ranh giới HTTP bằng `test/support/fakes.dart`.

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
