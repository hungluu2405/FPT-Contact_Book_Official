# Danh sách giả định kiến trúc và nghiệp vụ (ASSUMPTIONS)

1. Giai đoạn 0 cấu hình SecurityFilterChain mở tạm thời cho endpoint "/" cùng các static assets (css, js, webjars) để kiểm tra khởi động hệ thống trước khi cài đặt luồng đăng nhập chi tiết ở Giai đoạn 1.
2. Bộ màu nhận diện thương hiệu sử dụng CSS variables trong static/css/app.css: navy #0A1930, cam #F97316, nền trang #F8F7F4, chữ #1F2937 theo đúng tài liệu thiết kế.
3. Cấu hình kiểm thử tự động của Maven trong các giai đoạn đầu dùng Slice test (@WebMvcTest) cho tầng Web để đảm bảo build và verify độc lập, không phụ thuộc vào trạng thái chạy của máy chủ SQL Server bên ngoài.
