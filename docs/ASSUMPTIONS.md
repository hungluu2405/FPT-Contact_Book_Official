# Danh sách giả định kiến trúc và nghiệp vụ (ASSUMPTIONS)

1. Giai đoạn 0 cấu hình SecurityFilterChain mở tạm thời cho endpoint "/" cùng các static assets (css, js, webjars) để kiểm tra khởi động hệ thống trước khi cài đặt luồng đăng nhập chi tiết ở Giai đoạn 1.
2. Bộ màu nhận diện thương hiệu sử dụng CSS variables trong static/css/app.css: navy #0A1930, cam #F97316, nền trang #F8F7F4, chữ #1F2937 theo đúng tài liệu thiết kế.
3. ~~Cấu hình kiểm thử tự động của Maven trong các giai đoạn đầu dùng Slice test (@WebMvcTest) cho tầng Web để đảm bảo build và verify độc lập, không phụ thuộc vào trạng thái chạy của máy chủ SQL Server bên ngoài.~~ Thay thế từ Giai đoạn 1: test repository và tích hợp chạy trên `FptContactBook_test` của SQL Server localhost (CLAUDE.md mục 8); SQL Server không chạy thì các test này FAILED.
4. Giai đoạn 1: `ClassSessionRepository` truy vấn "theo khoảng ngày" trên toàn bộ buổi học (không lọc lớp học phần), sắp theo ngày rồi giờ bắt đầu; lọc theo lớp/sinh viên để dành cho giai đoạn thời khóa biểu.
5. Giai đoạn 1: `UserRepository.search` tìm theo từ khóa trên họ tên, số điện thoại và email; từ khóa null hoặc rỗng trả về toàn bộ người dùng. Lọc theo vai trò, lớp, bộ môn, trạng thái để dành cho Giai đoạn 2.
6. Giai đoạn 1: repository ngoài mục 6 của đề bài chỉ kế thừa `JpaRepository`, chưa có truy vấn riêng; truy vấn được thêm ở giai đoạn cần dùng.
7. Giai đoạn 1: `students.user_id`, `teachers.user_id`, `parents.user_id` có UNIQUE nên ánh xạ `@OneToOne(LAZY)` một chiều từ hồ sơ sang `User`.
8. Giai đoạn 1: unique index có điều kiện (`ux_users_email`, `ux_semesters_current`, `ux_sess_room_slot`) không biểu diễn được bằng annotation JPA; chỉ có ở database, service phải kiểm tra trước khi ghi.
