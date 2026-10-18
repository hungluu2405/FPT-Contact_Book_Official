# Danh sách giả định kiến trúc và nghiệp vụ (ASSUMPTIONS)

1. Giai đoạn 0 cấu hình SecurityFilterChain mở tạm thời cho endpoint "/" cùng các static assets (css, js, webjars) để kiểm tra khởi động hệ thống trước khi cài đặt luồng đăng nhập chi tiết ở Giai đoạn 1.
2. Bộ màu nhận diện thương hiệu sử dụng CSS variables trong static/css/app.css: navy #0A1930, cam #F97316, nền trang #F8F7F4, chữ #1F2937 theo đúng tài liệu thiết kế.
3. ~~Cấu hình kiểm thử tự động của Maven trong các giai đoạn đầu dùng Slice test (@WebMvcTest) cho tầng Web để đảm bảo build và verify độc lập, không phụ thuộc vào trạng thái chạy của máy chủ SQL Server bên ngoài.~~ Thay thế từ Giai đoạn 1: test repository và tích hợp chạy trên `FptContactBook_test` của SQL Server localhost (CLAUDE.md mục 8); SQL Server không chạy thì các test này FAILED.
4. Giai đoạn 1: `ClassSessionRepository` truy vấn "theo khoảng ngày" trên toàn bộ buổi học (không lọc lớp học phần), sắp theo ngày rồi giờ bắt đầu; lọc theo lớp/sinh viên để dành cho giai đoạn thời khóa biểu.
5. Giai đoạn 1: `UserRepository.search` tìm theo từ khóa trên họ tên, số điện thoại và email; từ khóa null hoặc rỗng trả về toàn bộ người dùng. Lọc theo vai trò, lớp, bộ môn, trạng thái để dành cho Giai đoạn 2.
6. Giai đoạn 1: repository ngoài mục 6 của đề bài chỉ kế thừa `JpaRepository`, chưa có truy vấn riêng; truy vấn được thêm ở giai đoạn cần dùng.
7. Giai đoạn 1: `students.user_id`, `teachers.user_id`, `parents.user_id` có UNIQUE nên ánh xạ `@OneToOne(LAZY)` một chiều từ hồ sơ sang `User`.
8. Giai đoạn 1: unique index có điều kiện (`ux_users_email`, `ux_semesters_current`, `ux_sess_room_slot`) không biểu diễn được bằng annotation JPA; chỉ có ở database, service phải kiểm tra trước khi ghi.
9. Giai đoạn 2: bộ đếm đăng nhập sai (`LoginAttemptService`) nằm trong bộ nhớ; khởi động lại ứng dụng thì bộ đếm và các khóa tạm mất, và không chia sẻ giữa nhiều instance.
10. Giai đoạn 2: số điện thoại sai định dạng khi đăng nhập không được đếm vào bộ khóa tạm (không có số chuẩn hóa làm khóa, tránh làm phình bộ nhớ); vẫn báo lỗi chung.
11. Giai đoạn 2: mật khẩu đúng nhưng tài khoản `is_active = 0` không tính là một lần sai. Mật khẩu dài hơn 72 byte (giới hạn BCrypt) bị coi là sai mà không gọi BCrypt.
12. Giai đoạn 2: người dùng không có vai trò nào bị đăng xuất ngay sau khi đăng nhập, không cập nhật `last_login_at` và không ghi audit LOGIN.
13. Giai đoạn 2: chỉ ghi audit cho đăng nhập thành công (FR-SYS-01 "ghi đăng nhập"); đăng nhập thất bại chưa ghi.
14. Giai đoạn 2: địa chỉ IP lấy từ `remoteAddr`, không tin `X-Forwarded-For` vì chưa có reverse proxy.
15. Giai đoạn 2: sau khi đăng nhập luôn về trang chủ theo vai trò, không quay lại trang đã yêu cầu trước đó.
16. Giai đoạn 2: ảnh đại diện lưu ở `{app.upload-dir}/avatars/<UUID>.jpg|png`, `users.avatar_url` lưu đường dẫn phục vụ `/avatars/<tên file>`; mọi người đã đăng nhập đều xem được ảnh (tên UUID không đoán được). Ảnh cũ bị xóa sau khi lưu thành công.
17. Giai đoạn 2: tên vai trò hiển thị lấy từ `messages.properties` theo mã vai trò, vì cột `roles.name` trong dữ liệu mẫu không có dấu.
18. Giai đoạn 2: email để trống trong hồ sơ được lưu `NULL` (unique index `ux_users_email` chỉ bỏ qua NULL).
19. Giai đoạn 2: mốc thời gian nghiệp vụ dùng `AppTime` (Asia/Ho_Chi_Minh) thay vì múi giờ mặc định của JVM.
20. Giai đoạn 3a: phiên đang mở của người bị khóa hoặc bị đặt lại mật khẩu vẫn còn hiệu lực đến khi hết hạn (30 phút không hoạt động); lần đăng nhập kế tiếp mới bị chặn hoặc bị ép đổi mật khẩu.
21. Giai đoạn 3a: mật khẩu tạm chỉ nằm trong flash attribute (session) cho tới request hiển thị kế tiếp rồi bị xóa; trang hiển thị trả Cache-Control: no-store. Không lưu ở đâu khác để xem lại.
22. Giai đoạn 3a: `user_roles.department_id` của vai trò TEACHER mặc định lấy bộ môn trong hồ sơ giảng viên; HEAD_OF_DEPARTMENT bắt buộc chọn bộ môn; ADMIN, STUDENT, PARENT để NULL. Sửa bộ môn trong hồ sơ giảng viên không tự đổi `user_roles` (đổi ở màn hình vai trò).
23. Giai đoạn 3a: phạm vi của trưởng bộ môn tính theo `teachers.department_id` (đúng yêu cầu), nên người có vai trò TEACHER/HEAD nhưng chưa có hồ sơ giảng viên (ví dụ user 15 `0911223344` trong dữ liệu mẫu) không hiện trong danh sách "Giảng viên bộ môn".
24. Giai đoạn 3a: mã sinh viên, mã giảng viên được bỏ khoảng trắng hai đầu và viết hoa trước khi lưu và kiểm tra trùng.
25. Giai đoạn 3a: liên kết phụ huynh nhập theo số điện thoại phụ huynh (trang sinh viên) hoặc mã sinh viên (trang phụ huynh) thay vì danh sách chọn, vì danh sách người dùng có thể rất dài.
26. Giai đoạn 3a: khóa tài khoản và gỡ vai trò chạy với isolation SERIALIZABLE để không mất quản trị viên đang hoạt động cuối cùng khi hai quản trị viên thao tác cùng lúc; nếu SQL Server chọn một bên làm deadlock victim thì bên đó nhận lỗi chung và chỉ cần thử lại.
27. Giai đoạn 3a: tìm kiếm người dùng theo số điện thoại, họ tên, mã SV/GV (không theo email); ký tự `%`, `_`, `[` trong từ khóa được coi là ký tự thường.
28. Giai đoạn 3b: nhập Excel theo phương án "có lỗi thì không ghi dòng nào": kiểm tra toàn bộ file trước; chỉ khi không có lỗi mới tạo tất cả người dùng trong một transaction. Vi phạm unique phát hiện ở database lúc tạo (thao tác đồng thời) làm rollback toàn bộ.
29. Giai đoạn 3b: tối đa 500 dòng dữ liệu (không tính dòng trống) và tối đa 2 MB mỗi file; chỉ nhận .xlsx. Nhập file lớn sẽ chậm vì mỗi mật khẩu tạm phải băm BCrypt (khoảng 70 ms một người).
30. Giai đoạn 3b: nhập giảng viên chỉ gán vai trò TEACHER (bộ môn lấy theo cột Mã bộ môn); HEAD_OF_DEPARTMENT không gán qua Excel mà gán tay ở màn hình vai trò.
31. Giai đoạn 3b: dòng ví dụ của file mẫu dùng số điện thoại 0900000000; nếu chưa xóa mà vẫn nhập thì báo lỗi ở dòng đó thay vì tạo tài khoản ảo.
32. Giai đoạn 3b: ô số điện thoại kiểu số mà Excel đã bỏ số 0 đầu (còn 9 chữ số) được thêm lại số 0; ngày nhận ô kiểu ngày hoặc chữ d/M/yyyy; trạng thái nhận nhãn tiếng Việt (Đang học, Bảo lưu, Đã tốt nghiệp, Thôi học) hoặc mã (STUDYING...).
33. Giai đoạn 3b: nhập thành công thì trình duyệt tải về file kết quả có mật khẩu tạm; file chỉ tạo trong bộ nhớ, không lưu trên server, không ghi vào log hay audit.
