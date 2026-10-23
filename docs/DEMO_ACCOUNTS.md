# Tài khoản đăng nhập demo

Chỉ dùng cho môi trường dev/test trên máy cá nhân. Đăng nhập bằng **số điện thoại**; mật khẩu mọi tài khoản dưới đây
là `Fpt@12345` (nếu chưa ai đổi). Không dùng các tài khoản và mật khẩu này trên môi trường thật.

- Nhóm 1 có sẵn trong `database/fpt_contact_book.sql`.
- Nhóm 2 do script dữ liệu demo học kỳ HK1-2627 thêm vào (giáo viên `GV101`–`GV128`, học sinh, phụ huynh của các lớp
  10A1, 10A2, 11A1, 11A2, 8A1, 8A2). Chỉ có khi đã chạy script demo.
- Tài khoản tự tạo trên web (ví dụ `GV005`, `GV006`) không có trong danh sách; mật khẩu là mật khẩu tạm lúc tạo.

## Gợi ý tài khoản để demo nhanh

| Vai trò | Số điện thoại | Người dùng | Có gì để xem |
|---|---|---|---|
| Quản trị viên | `0900000001` | Quản trị hệ thống | Quản lý người dùng, danh mục, lớp, lịch, báo cáo, nhật ký |
| Tổ trưởng + giáo viên | `0900000002` | Nguyễn Văn Trưởng (GV001, tổ Toán – Tin học) | Quản lý tổ, công bố điểm; dạy Toán 10A2, Toán 8A2 |
| Tổ trưởng + giáo viên | `0910000017` | Nguyễn Hồng Linh (GV117, tổ Vật lý – Hóa học) | Thông báo tổ có thống kê đã đọc |
| Giáo viên chủ nhiệm | `0910000006` | Phạm Công Đăng (GV106) | Chủ nhiệm 11A2; dạy Ngữ văn 10A2 có **đơn nghỉ chờ duyệt**, có buổi bị hủy và buổi học bù |
| Giáo viên | `0900000003` | Trần Thị Giáo (GV002) | Dạy Toán 10A1, Toán 11A1; điểm danh buổi học |
| Học sinh | `0920000015` | Võ Trung Kiên (HS260108, 10A2) | **Cảnh báo vắng** môn Ngữ văn 10 |
| Học sinh | `0900000004` | Lê Minh Sinh (HS260001, 10A1) | Lịch học, lịch thi, điểm, điểm danh |
| Phụ huynh có 2 con | `0930000022` | Bùi Công Khôi | Chọn con: HS250201 (11A2) và HS240301 (8A2) |
| Phụ huynh | `0930000008` | Phan Đức Dũng | Con HS260101 (10A2); có **đơn nghỉ đang chờ duyệt** |

## 1. Tài khoản có sẵn trong script gốc

| Số điện thoại | Họ tên | Vai trò | Ghi chú |
|---|---|---|---|
| `0900000001` | Quản trị hệ thống | ADMIN |  |
| `0901234567` | Trần Quản Trị | ADMIN |  |
| `0900000002` | Nguyễn Văn Trưởng | TEACHER, HEAD_OF_DEPARTMENT | GV001, tổ trưởng tổ Toán – Tin học |
| `0911223344` | TS. Lê Trí Tuệ | TEACHER, HEAD_OF_DEPARTMENT | Tổ Toán – Tin học, chưa có hồ sơ giáo viên |
| `0900000003` | Trần Thị Giáo | TEACHER | GV002, chủ nhiệm 10A1 |
| `0922334455` | ThS. Hoàng Văn Code | TEACHER | GV003, chủ nhiệm 11A1 |
| `0922334456` | ThS. Đỗ Thị Thuật | TEACHER | GV004, chủ nhiệm 8A1 |
| `0900000004` | Lê Minh Sinh | STUDENT | HS260001, lớp 10A1 |
| `0933112233` | Nguyễn Văn Hùng | STUDENT | HS250101, lớp 11A1 |
| `0933112234` | Vũ Thị Mai | STUDENT | HS250102, lớp 11A1 |
| `0933112235` | Trần Đức Anh | STUDENT | HS240201, lớp 8A1 |
| `0900000005` | Lê Văn Phụ | PARENT | Cha của HS260001 |
| `0944112233` | Nguyễn Văn Hưng | PARENT | Cha của HS250101 |
| `0944112234` | Lê Thị Ngọc | PARENT | Mẹ của HS250102 |

## 2. Tài khoản demo HK1-2627

### Giáo viên (`0910000001`–`0910000028`)

Vai trò TEACHER; người đánh dấu ★ có thêm HEAD_OF_DEPARTMENT (tổ trưởng) của tổ mình.

| Số điện thoại | Mã GV | Họ tên | Tổ | Môn | Lớp dạy HK1-2627 | Chủ nhiệm |
|---|---|---|---|---|---|---|
| `0910000001` | GV101 | Nguyễn Thị Anh | Toán – Tin học | Toán | Toán 11 (11A2) | 10A2 |
| `0910000002` | GV102 | Vũ Thu Thảo | Toán – Tin học | Toán | Toán 8 (8A1) |  |
| `0910000003` | GV103 | Dương Quang Kiên | Toán – Tin học | Tin học | Tin học 11 (11A2) |  |
| `0910000004` | GV104 | Huỳnh Thanh Uyên | Toán – Tin học | Tin học | Tin học 8 (8A1) |  |
| `0910000005` | GV105 ★ | Hồ Hồng Ngọc | Ngữ văn | Ngữ văn | Ngữ văn 10 (10A1), Ngữ văn 8 (8A1) |  |
| `0910000006` | GV106 | Phạm Công Đăng | Ngữ văn | Ngữ văn | Ngữ văn 10 (10A2), Ngữ văn 8 (8A2) | 11A2 |
| `0910000007` | GV107 | Bùi Thị Anh | Ngữ văn | Ngữ văn | Ngữ văn 11 (11A1) |  |
| `0910000008` | GV108 | Trần Thu Ngọc | Ngữ văn | Ngữ văn | Ngữ văn 11 (11A2) |  |
| `0910000009` | GV109 | Võ Quang Bảo | Ngữ văn | Lịch sử | Lịch sử 10 (10A1), Lịch sử và Địa lý 8 (8A1) |  |
| `0910000010` | GV110 | Lý Thanh Chi | Ngữ văn | Lịch sử | Lịch sử 10 (10A2), Lịch sử và Địa lý 8 (8A2) |  |
| `0910000011` | GV111 | Phan Hồng Hân | Ngữ văn | Lịch sử | Lịch sử 11 (11A1) |  |
| `0910000012` | GV112 | Ngô Công Huy | Ngữ văn | Lịch sử | Lịch sử 11 (11A2) |  |
| `0910000013` | GV113 ★ | Hoàng Thị My | Ngoại ngữ | Tiếng Anh | Tiếng Anh 10 (10A1), Tiếng Anh 8 (8A1) |  |
| `0910000014` | GV114 | Đỗ Thu Dương | Ngoại ngữ | Tiếng Anh | Tiếng Anh 10 (10A2), Tiếng Anh 8 (8A2) |  |
| `0910000015` | GV115 | Lê Quang Vinh | Ngoại ngữ | Tiếng Anh | Tiếng Anh 11 (11A1) |  |
| `0910000016` | GV116 | Đặng Thanh Linh | Ngoại ngữ | Tiếng Anh | Tiếng Anh 11 (11A2) |  |
| `0910000017` | GV117 ★ | Nguyễn Hồng Linh | Vật lý – Hóa học | Vật lý | Vật lý 10 (10A1) |  |
| `0910000018` | GV118 | Vũ Công Khôi | Vật lý – Hóa học | Vật lý | Vật lý 10 (10A2) |  |
| `0910000019` | GV119 | Dương Thị Anh | Vật lý – Hóa học | Vật lý | Vật lý 11 (11A1) |  |
| `0910000020` | GV120 | Huỳnh Thu Thảo | Vật lý – Hóa học | Vật lý | Vật lý 11 (11A2) |  |
| `0910000021` | GV121 | Hồ Quang Kiên | Vật lý – Hóa học | Hóa học | Hóa học 10 (10A1) |  |
| `0910000022` | GV122 | Phạm Thanh Uyên | Vật lý – Hóa học | Hóa học | Hóa học 10 (10A2) |  |
| `0910000023` | GV123 | Bùi Hồng Ngọc | Vật lý – Hóa học | Hóa học | Hóa học 11 (11A1) |  |
| `0910000024` | GV124 | Trần Công Đăng | Vật lý – Hóa học | Hóa học | Hóa học 11 (11A2) |  |
| `0910000025` | GV125 ★ | Võ Thị Anh | Khoa học tự nhiên | Khoa học tự nhiên | Khoa học tự nhiên 8 (8A1) |  |
| `0910000026` | GV126 | Lý Thu Ngọc | Khoa học tự nhiên | Khoa học tự nhiên | Khoa học tự nhiên 8 (8A2) | 8A2 |
| `0910000027` | GV127 | Phan Quang Bảo | Khoa học tự nhiên | Khoa học tự nhiên | — |  |
| `0910000028` | GV128 | Ngô Thanh Chi | Khoa học tự nhiên | Khoa học tự nhiên | — |  |

### Học sinh (`0920000001`–`0920000044`)

Vai trò STUDENT. Mỗi lớp có 8 học sinh, gồm cả học sinh có sẵn trong script gốc.

| Số điện thoại | Mã HS | Họ tên | Lớp |
|---|---|---|---|
| `0920000001` | HS260002 | Hoàng Bảo Long | 10A1 |
| `0920000002` | HS260003 | Lê Hà My | 10A1 |
| `0920000003` | HS260004 | Nguyễn Công Vinh | 10A1 |
| `0920000004` | HS260005 | Dương Diệu Linh | 10A1 |
| `0920000005` | HS260006 | Hồ Minh Quân | 10A1 |
| `0920000006` | HS260007 | Bùi Mai Phương | 10A1 |
| `0920000007` | HS260008 | Võ Đức Anh | 10A1 |
| `0920000008` | HS260101 | Phan Lan Anh | 10A2 |
| `0920000009` | HS260102 | Hoàng Quốc Bảo | 10A2 |
| `0920000010` | HS260103 | Lê Ngọc Hân | 10A2 |
| `0920000011` | HS260104 | Nguyễn Thành Đạt | 10A2 |
| `0920000012` | HS260105 | Dương Kim Ngân | 10A2 |
| `0920000013` | HS260106 | Hồ Văn Khoa | 10A2 |
| `0920000014` | HS260107 | Bùi Ngọc Anh | 10A2 |
| `0920000015` | HS260108 | Võ Trung Kiên | 10A2 |
| `0920000016` | HS250103 | Phan Bảo Ngọc | 11A1 |
| `0920000017` | HS250104 | Hoàng Phúc Lâm | 11A1 |
| `0920000018` | HS250105 | Lê Minh Thư | 11A1 |
| `0920000019` | HS250106 | Nguyễn Bảo Long | 11A1 |
| `0920000020` | HS250107 | Dương Hà My | 11A1 |
| `0920000021` | HS250108 | Hồ Công Vinh | 11A1 |
| `0920000022` | HS250201 | Bùi Diệu Linh | 11A2 |
| `0920000023` | HS250202 | Võ Minh Quân | 11A2 |
| `0920000024` | HS250203 | Phan Mai Phương | 11A2 |
| `0920000025` | HS250204 | Hoàng Đức Anh | 11A2 |
| `0920000026` | HS250205 | Lê Lan Anh | 11A2 |
| `0920000027` | HS250206 | Nguyễn Quốc Bảo | 11A2 |
| `0920000028` | HS250207 | Dương Ngọc Hân | 11A2 |
| `0920000029` | HS250208 | Hồ Thành Đạt | 11A2 |
| `0920000030` | HS240202 | Bùi Kim Ngân | 8A1 |
| `0920000031` | HS240203 | Võ Văn Khoa | 8A1 |
| `0920000032` | HS240204 | Phan Ngọc Anh | 8A1 |
| `0920000033` | HS240205 | Hoàng Trung Kiên | 8A1 |
| `0920000034` | HS240206 | Lê Bảo Ngọc | 8A1 |
| `0920000035` | HS240207 | Nguyễn Phúc Lâm | 8A1 |
| `0920000036` | HS240208 | Dương Minh Thư | 8A1 |
| `0920000037` | HS240301 | Hồ Bảo Long | 8A2 |
| `0920000038` | HS240302 | Phạm Thu Trang | 8A2 |
| `0920000039` | HS240303 | Bùi Thành Đạt | 8A2 |
| `0920000040` | HS240304 | Võ Kim Ngân | 8A2 |
| `0920000041` | HS240305 | Phan Văn Khoa | 8A2 |
| `0920000042` | HS240306 | Hoàng Ngọc Anh | 8A2 |
| `0920000043` | HS240307 | Lê Trung Kiên | 8A2 |
| `0920000044` | HS240308 | Nguyễn Bảo Ngọc | 8A2 |

### Phụ huynh (`0930000001`–`0930000042`)

Vai trò PARENT.

| Số điện thoại | Họ tên | Con |
|---|---|---|
| `0930000001` | Hoàng Công Huy | HS260002 Hoàng Bảo Long (10A1) |
| `0930000002` | Lê Đức Tuấn | HS260003 Lê Hà My (10A1) |
| `0930000003` | Nguyễn Thanh Linh | HS260004 Nguyễn Công Vinh (10A1) |
| `0930000004` | Dương Công Khôi | HS260005 Dương Diệu Linh (10A1) |
| `0930000005` | Hồ Đức Kiệt | HS260006 Hồ Minh Quân (10A1) |
| `0930000006` | Bùi Thanh Uyên | HS260007 Bùi Mai Phương (10A1) |
| `0930000007` | Võ Công Đăng | HS260008 Võ Đức Anh (10A1) |
| `0930000008` | Phan Đức Dũng | HS260101 Phan Lan Anh (10A2) |
| `0930000009` | Hoàng Thanh Chi | HS260102 Hoàng Quốc Bảo (10A2) |
| `0930000010` | Lê Công Huy | HS260103 Lê Ngọc Hân (10A2) |
| `0930000011` | Nguyễn Đức Tuấn | HS260104 Nguyễn Thành Đạt (10A2) |
| `0930000012` | Dương Thanh Linh | HS260105 Dương Kim Ngân (10A2) |
| `0930000013` | Hồ Công Khôi | HS260106 Hồ Văn Khoa (10A2) |
| `0930000014` | Bùi Đức Kiệt | HS260107 Bùi Ngọc Anh (10A2) |
| `0930000015` | Võ Thanh Uyên | HS260108 Võ Trung Kiên (10A2) |
| `0930000016` | Phan Công Đăng | HS250103 Phan Bảo Ngọc (11A1) |
| `0930000017` | Hoàng Đức Dũng | HS250104 Hoàng Phúc Lâm (11A1) |
| `0930000018` | Lê Thanh Chi | HS250105 Lê Minh Thư (11A1) |
| `0930000019` | Nguyễn Công Huy | HS250106 Nguyễn Bảo Long (11A1) |
| `0930000020` | Dương Đức Tuấn | HS250107 Dương Hà My (11A1) |
| `0930000021` | Hồ Thanh Linh | HS250108 Hồ Công Vinh (11A1) |
| `0930000022` | Bùi Công Khôi | HS250201 Bùi Diệu Linh (11A2); HS240301 Hồ Bảo Long (8A2) |
| `0930000023` | Võ Đức Kiệt | HS250202 Võ Minh Quân (11A2); HS240302 Phạm Thu Trang (8A2) |
| `0930000024` | Phan Thanh Uyên | HS250203 Phan Mai Phương (11A2) |
| `0930000025` | Hoàng Công Đăng | HS250204 Hoàng Đức Anh (11A2) |
| `0930000026` | Lê Đức Dũng | HS250205 Lê Lan Anh (11A2) |
| `0930000027` | Nguyễn Thanh Chi | HS250206 Nguyễn Quốc Bảo (11A2) |
| `0930000028` | Dương Công Huy | HS250207 Dương Ngọc Hân (11A2) |
| `0930000029` | Hồ Đức Tuấn | HS250208 Hồ Thành Đạt (11A2) |
| `0930000030` | Bùi Thanh Linh | HS240202 Bùi Kim Ngân (8A1) |
| `0930000031` | Võ Công Khôi | HS240203 Võ Văn Khoa (8A1) |
| `0930000032` | Phan Đức Kiệt | HS240204 Phan Ngọc Anh (8A1) |
| `0930000033` | Hoàng Thanh Uyên | HS240205 Hoàng Trung Kiên (8A1) |
| `0930000034` | Lê Công Đăng | HS240206 Lê Bảo Ngọc (8A1) |
| `0930000035` | Nguyễn Đức Dũng | HS240207 Nguyễn Phúc Lâm (8A1) |
| `0930000036` | Dương Thanh Chi | HS240208 Dương Minh Thư (8A1) |
| `0930000037` | Bùi Đức Tuấn | HS240303 Bùi Thành Đạt (8A2) |
| `0930000038` | Võ Minh Nam | HS240304 Võ Kim Ngân (8A2) |
| `0930000039` | Phan Minh Nhung | HS240305 Phan Văn Khoa (8A2) |
| `0930000040` | Hoàng Đức Kiệt | HS240306 Hoàng Ngọc Anh (8A2) |
| `0930000041` | Lê Minh Phước | HS240307 Lê Trung Kiên (8A2) |
| `0930000042` | Nguyễn Minh Hương | HS240308 Nguyễn Bảo Ngọc (8A2) |

