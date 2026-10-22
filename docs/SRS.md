# Tài liệu đặc tả yêu cầu phần mềm (SRS)

**Hệ thống Sổ liên lạc điện tử – Đại học FPT (phân hệ Web)**

Phiên bản 0.2 (bản nháp) · Spring MVC + Thymeleaf + SQL Server · Mobile sẽ gọi Web API ở giai đoạn sau

## 1. Giới thiệu

### 1.1 Mục đích

Tài liệu mô tả các chức năng và ràng buộc của phân hệ **Web**, làm cơ sở để nhóm phân tích, thiết kế, lập trình và kiểm thử. Chức năng được tham khảo từ các hệ thống sổ liên lạc điện tử phổ biến tại Việt Nam như eNetViet, sau đó điều chỉnh cho môi trường đại học. Ứng dụng Mobile (Flutter) chỉ được nêu ở mục 8 như định hướng; trước mắt toàn bộ công sức dồn cho Web.

### 1.2 Phạm vi

**Trong phạm vi:** tài khoản và vai trò, danh mục đào tạo, thời khóa biểu và lịch thi, điểm danh, điểm số, thông báo, tin nhắn, đơn từ, câu lạc bộ, báo cáo thống kê.

**Ngoài phạm vi (đợt này):** ứng dụng Mobile, học phí và thanh toán, tích hợp hệ thống đào tạo thật của trường, giao diện đa ngôn ngữ.

### 1.3 Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Backend | Spring Boot 3.3.4 (Spring MVC), Java 21 |
| Giao diện | Thymeleaf, render phía máy chủ |
| Truy cập dữ liệu | Spring Data JPA (Hibernate) |
| Cơ sở dữ liệu | Microsoft SQL Server; script `fpt_contact_book.sql` (36 bảng) |
| Bảo mật | Spring Security (phiên đăng nhập cho Web), BCrypt; JWT cho API Mobile ở giai đoạn sau |
| Thư viện khác | Apache POI (Excel), springdoc OpenAPI |

Lưu ý: `pom.xml` hiện còn H2 và chưa có driver SQL Server, cần thêm `mssql-jdbc`.

### 1.4 Từ viết tắt

| Viết tắt | Ý nghĩa |
| --- | --- |
| AD | Quản trị viên (ADMIN) |
| TBM | Trưởng bộ môn (HEAD\_OF\_DEPARTMENT) |
| GV | Giảng viên (TEACHER) |
| SV | Sinh viên (STUDENT) |
| PH | Phụ huynh (PARENT) |
| CLB | Câu lạc bộ |
| RBAC | Phân quyền theo vai trò |
| FR / NFR | Yêu cầu chức năng / phi chức năng |

## 2. Mô tả tổng quan

### 2.1 Kiến trúc tổng thể

- Mô hình MVC: Model (entity JPA, service), View (Thymeleaf), Controller (Spring MVC). Thư mục mã nguồn đặt tên thống nhất giữa các thành viên.
- Một cơ sở dữ liệu SQL Server duy nhất, do backend truy cập.
- Backend dùng chung lớp service để sau này cung cấp thêm REST API cho Mobile.

### 2.2 Vai trò người dùng

| Vai trò | Mã | Mô tả |
| --- | --- | --- |
| Quản trị viên | ADMIN | Quản lý tài khoản, vai trò, danh mục, lịch, CLB; đặt lại mật khẩu; xem nhật ký |
| Trưởng bộ môn | HEAD\_OF\_DEPARTMENT | Quản lý giảng viên, môn học, lớp học phần của bộ môn mình; công bố điểm |
| Giảng viên | TEACHER | Điểm danh, nhập điểm, gửi thông báo cho lớp học phần mình dạy; duyệt đơn nghỉ |
| Sinh viên | STUDENT | Xem lịch học, điểm, điểm danh; gửi đơn; tham gia CLB; nhắn tin |
| Phụ huynh | PARENT | Theo dõi lịch học, điểm, điểm danh của con; gửi đơn thay con; nhắn tin với giảng viên |

### 2.3 Quan hệ người dùng và vai trò

- Một người dùng có thể có nhiều vai trò và một vai trò có nhiều người dùng (nhiều–nhiều, bảng `user_roles`).
- Mỗi lần gán vai trò có thể kèm phạm vi bộ môn. Ví dụ: một giảng viên vừa là TEACHER vừa là HEAD\_OF\_DEPARTMENT của bộ môn Kỹ thuật phần mềm.
- Quyền của người dùng là hợp quyền của mọi vai trò được gán; không có chế độ chọn đang đăng nhập với vai trò nào.
- Phân quyền kiểm tra theo tên vai trò trong code, không có bảng quyền chi tiết.
- TBM quản lý giảng viên và môn học thuộc bộ môn mình được gán.
- SV và PH quan hệ nhiều–nhiều: một PH có nhiều con, một SV có nhiều PH. PH chỉ xem dữ liệu của con đã được liên kết.
- ADMIN không cần hồ sơ riêng; SV, GV, PH có hồ sơ riêng (`students`, `teachers`, `parents`).

### 2.4 Đăng nhập và mật khẩu

- Tên đăng nhập là **số điện thoại**: 10 chữ số, bắt đầu bằng 0, không trùng trong toàn hệ thống. Số dạng +84… được chuẩn hóa về 0… trước khi tra cứu.
- Email không dùng để đăng nhập, chỉ là thông tin liên hệ và không bắt buộc.
- Tài khoản do ADMIN tạo; người dùng không tự đăng ký.
- Mật khẩu lưu dạng băm BCrypt. Mật khẩu tạm bắt buộc đổi ở lần đăng nhập đầu.
- Không có chức năng quên mật khẩu tự động (không dùng email hay SMS). Người dùng liên hệ ADMIN để được **đặt lại mật khẩu**.
- Khóa tài khoản bằng cờ `is_active`, không xóa thật.

### 2.5 Ràng buộc và giả định

- Giai đoạn đầu dùng dữ liệu mẫu, chưa tích hợp hệ thống đào tạo thật.
- Giao diện tiếng Việt, lưu Unicode (NVARCHAR, collation Vietnamese\_CI\_AS).
- Chỉ có một học kỳ hiện tại tại một thời điểm (ràng buộc ở cơ sở dữ liệu).
- Học phí và thanh toán nằm ngoài phạm vi đợt này.
- Bộ môn được hiểu là đơn vị thuộc một khoa, chứa nhiều môn học và nhiều giảng viên.

## 3. Yêu cầu chức năng (Web)

Cột **Ưu tiên**: **C** = cao (bản đầu tiên), **TB** = trung bình, **T** = thấp (mở rộng). Cột **Vai trò** ghi vai trò được dùng chức năng.

### 3.1 Tài khoản và xác thực (FR-AUTH)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-AUTH-01 | Đăng nhập | Đăng nhập bằng số điện thoại và mật khẩu; báo lỗi rõ khi sai hoặc tài khoản bị khóa | Tất cả | C |
| FR-AUTH-02 | Đăng xuất | Kết thúc phiên, về trang đăng nhập | Tất cả | C |
| FR-AUTH-03 | Đổi mật khẩu | Tự đổi khi đã đăng nhập; bắt buộc đổi ở lần đầu hoặc sau khi AD đặt lại | Tất cả | C |
| FR-AUTH-04 | Phân quyền | Gộp quyền từ mọi vai trò; giới hạn dữ liệu theo bộ môn, lớp, con đã liên kết | Tất cả | C |
| FR-AUTH-05 | Hồ sơ cá nhân | Xem thông tin; sửa email, địa chỉ, ảnh đại diện (số điện thoại do AD quản lý) | Tất cả | TB |
| FR-AUTH-06 | Ghi nhớ đăng nhập | Giữ phiên trên thiết bị tin cậy | Tất cả | T |

### 3.2 Quản lý người dùng (FR-USER)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-USER-01 | Thêm người dùng | Nhập số điện thoại, họ tên, ít nhất một vai trò và hồ sơ theo vai trò (mã SV/GV, bộ môn, lớp); hệ thống sinh mật khẩu tạm | AD | C |
| FR-USER-02 | Danh sách người dùng | Tìm theo số điện thoại, tên, mã; lọc theo vai trò, lớp, bộ môn, trạng thái; phân trang. TBM chỉ thấy giảng viên bộ môn mình | AD, TBM | C |
| FR-USER-03 | Xem chi tiết | Xem thông tin đầy đủ, vai trò và phạm vi, phụ huynh hoặc con liên quan | AD, TBM | C |
| FR-USER-04 | Sửa người dùng | Cập nhật thông tin cá nhân và hồ sơ | AD | C |
| FR-USER-05 | Gán và gỡ vai trò | Gán nhiều vai trò cho một người, kèm bộ môn nếu cần | AD | C |
| FR-USER-06 | Khóa / mở khóa | Khóa tài khoản vi phạm hoặc thôi học, không xóa dữ liệu | AD | C |
| FR-USER-07 | Đặt lại mật khẩu | Tạo mật khẩu tạm (hiện một lần), bắt đổi ở lần đăng nhập kế tiếp, ghi nhật ký | AD | C |
| FR-USER-08 | Liên kết phụ huynh – sinh viên | Gán một hoặc nhiều PH cho SV, ghi quan hệ (cha, mẹ, người giám hộ) | AD | C |
| FR-USER-09 | Nhập từ Excel | Nhập hàng loạt SV, GV; báo lỗi theo từng dòng | AD | TB |

Nhóm FR-USER-01 đến 04 tương ứng 4 màn hình CRUD: add, list, edit, view, dùng chung một `user_controller`.

### 3.3 Danh mục đào tạo (FR-ORG)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-ORG-01 | Khoa, bộ môn, ngành | Thêm, sửa danh mục; bộ môn thuộc khoa | AD | C |
| FR-ORG-02 | Lớp hành chính | Mã lớp, ngành, niên khóa, giảng viên chủ nhiệm (tùy chọn) | AD | C |
| FR-ORG-03 | Môn học | Mã môn, tên, số tín chỉ, bộ môn quản lý | AD, TBM (bộ môn mình) | C |
| FR-ORG-04 | Học kỳ | Khai báo thời gian; chỉ một học kỳ hiện tại | AD | C |
| FR-ORG-05 | Lớp học phần | Gắn môn, học kỳ, giảng viên, sĩ số tối đa | AD, TBM (bộ môn mình) | C |
| FR-ORG-06 | Ghi danh sinh viên | Đưa SV vào lớp học phần, không trùng, kiểm tra sĩ số | AD | C |
| FR-ORG-07 | Phòng học | Mã phòng, tòa nhà, sức chứa | AD | TB |

### 3.4 Thời khóa biểu và lịch thi (FR-SCHEDULE)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-SCHEDULE-01 | Quản lý buổi học | Tạo, sửa, hủy từng buổi (ngày, giờ, phòng); cảnh báo trùng phòng và trùng giờ | AD, TBM | C |
| FR-SCHEDULE-02 | Quản lý lịch thi | Giữa kỳ, cuối kỳ, thi lại; phòng, giờ thi | AD, TBM | C |
| FR-SCHEDULE-03 | Xem thời khóa biểu | Theo ngày, tuần, học kỳ; hiển thị môn, phòng, giờ, giảng viên | GV, SV, PH | C |
| FR-SCHEDULE-04 | Xem lịch thi | Ngày, giờ, phòng thi | GV, SV, PH | C |
| FR-SCHEDULE-05 | Báo thay đổi lịch | Tự gửi thông báo khi đổi phòng, hủy, dạy bù | Hệ thống | TB |

### 3.5 Điểm danh (FR-ATT)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-ATT-01 | Điểm danh theo buổi | Đánh dấu có mặt, vắng có phép, vắng không phép; mỗi học sinh có ghi chú (đi muộn ghi là có mặt kèm ghi chú). Điểm danh lần đầu trong 24 giờ sau khi buổi học kết thúc | GV (lớp mình) | C |
| FR-ATT-02 | Xem điểm danh | Lịch sử theo môn, tỷ lệ chuyên cần | SV, PH (con), GV, TBM | C |
| FR-ATT-03 | Điều chỉnh điểm danh | Sửa khi có minh chứng, lưu người sửa và thời điểm sửa. GV chỉ sửa trong 24 giờ kể từ lần điểm danh đầu; quá hạn chỉ AD sửa (bắt buộc lý do) | GV, AD | TB |
| FR-ATT-04 | Cảnh báo vắng quá ngưỡng | Báo SV và PH khi số buổi vắng vượt ngưỡng | Hệ thống | TB |

### 3.6 Điểm số (FR-GRADE)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-GRADE-01 | Khai báo đầu điểm | Đầu điểm và trọng số của môn (ví dụ Assignment 20%, Midterm 30%, Final 50%); tổng trọng số bằng 100% | AD, TBM (bộ môn mình) | C |
| FR-GRADE-02 | Nhập điểm | Nhập điểm thang 0–10 cho SV trong lớp học phần mình dạy | GV | C |
| FR-GRADE-03 | Nhập điểm từ Excel | Kiểm tra hợp lệ trước khi lưu, báo lỗi theo từng dòng | GV | TB |
| FR-GRADE-04 | Công bố điểm | Kiểm tra rồi công bố; điểm chưa công bố thì SV và PH không thấy; lưu người công bố và thời điểm | TBM, AD | C |
| FR-GRADE-05 | Xem điểm | Điểm từng đầu điểm, điểm trung bình môn theo trọng số | SV, PH (con), GV, TBM | C |
| FR-GRADE-06 | Điều chỉnh điểm sau công bố | Chỉ khi có lý do, ghi nhật ký | TBM, AD | TB |

### 3.7 Thông báo (FR-NOTI)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-NOTI-01 | Gửi thông báo | Phạm vi: toàn trường, theo vai trò, bộ môn, lớp, lớp học phần hoặc cá nhân; mức thường, quan trọng, khẩn. AD gửi mọi phạm vi, TBM gửi trong bộ môn, GV gửi cho lớp học phần của mình | AD, TBM, GV | C |
| FR-NOTI-02 | Xem thông báo | Danh sách, đánh dấu đã đọc, đếm số chưa đọc | Tất cả | C |
| FR-NOTI-03 | Đính kèm tệp | Tài liệu, hình ảnh | Người gửi | TB |
| FR-NOTI-04 | Thống kê đã đọc | Biết bao nhiêu người nhận đã đọc | Người gửi | TB |

### 3.8 Tin nhắn (FR-MSG)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-MSG-01 | Nhắn tin 1–1 | Giữa PH, SV với GV; lưu lịch sử | SV, PH, GV, TBM | C |
| FR-MSG-02 | Danh sách hội thoại | Tin chưa đọc, thời điểm đọc gần nhất | Tất cả | TB |
| FR-MSG-03 | Nhóm hội thoại | Kênh chung của lớp học phần | GV, SV | T |
| FR-MSG-04 | Xóa tin nhắn | Đánh dấu đã xóa, không xóa khỏi cơ sở dữ liệu | Người gửi | T |

### 3.9 Đơn từ (FR-REQ)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-REQ-01 | Gửi đơn xin nghỉ | Chọn lớp học phần (tùy chọn), khoảng ngày, lý do, đính kèm minh chứng; PH có thể gửi thay con | SV, PH | C |
| FR-REQ-02 | Duyệt hoặc từ chối đơn | Kèm ghi chú, lưu lịch sử duyệt, người gửi nhận thông báo; đơn nghỉ do GV lớp học phần duyệt | GV | C |
| FR-REQ-03 | Theo dõi trạng thái | Chờ duyệt, đã duyệt, từ chối, đã hủy | SV, PH | C |
| FR-REQ-04 | Hủy đơn | Chỉ khi còn chờ duyệt | SV, PH | TB |
| FR-REQ-05 | Đơn khác | Xin bảng điểm, xác nhận sinh viên, phúc khảo; AD xử lý | SV (gửi), AD (duyệt) | TB |

Khi đơn nghỉ được duyệt, các buổi vắng trong khoảng ngày xin nghỉ tự chuyển thành vắng có phép.

### 3.10 Câu lạc bộ (FR-CLUB)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-CLUB-01 | Quản lý CLB | Thêm, sửa, ngừng hoạt động; mã, tên, mô tả, logo, giảng viên phụ trách | AD | C |
| FR-CLUB-02 | Xem CLB | Danh sách, chi tiết, thành viên, hoạt động sắp tới | Tất cả | C |
| FR-CLUB-03 | Đăng ký tham gia CLB | Gửi yêu cầu, trạng thái chờ duyệt | SV | C |
| FR-CLUB-04 | Duyệt thành viên | Duyệt hoặc từ chối yêu cầu tham gia | Chủ nhiệm CLB, AD | C |
| FR-CLUB-05 | Quản lý hoạt động | Tạo, sửa, hủy hoạt động (thời gian, địa điểm, số lượng tối đa) | Chủ nhiệm, phó chủ nhiệm CLB, AD | C |
| FR-CLUB-06 | Đăng ký tham gia hoạt động | Đăng ký, hủy đăng ký, kiểm tra số lượng tối đa | Thành viên CLB | TB |
| FR-CLUB-07 | Ghi nhận tham dự | Đánh dấu thành viên đã tham dự hoạt động | Chủ nhiệm, phó chủ nhiệm CLB | TB |
| FR-CLUB-08 | Rời CLB | Thành viên tự rời | Thành viên CLB | TB |

Vai trò trong CLB (chủ nhiệm, phó chủ nhiệm, thành viên) tách biệt với vai trò hệ thống, lưu ở `club_members.member_role`.

### 3.11 Báo cáo và thống kê (FR-REPORT)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-REPORT-01 | Thống kê điểm danh | Tỷ lệ chuyên cần theo lớp học phần và theo sinh viên | GV, TBM | TB |
| FR-REPORT-02 | Thống kê điểm | Phổ điểm, tỷ lệ đạt theo lớp học phần, môn | GV, TBM, AD | TB |
| FR-REPORT-03 | Thống kê CLB | Số thành viên, số hoạt động | AD | T |
| FR-REPORT-04 | Xuất báo cáo | Xuất Excel hoặc PDF | GV, TBM, AD | TB |

### 3.12 Quản trị hệ thống (FR-SYS)

| Mã | Chức năng | Mô tả | Vai trò | Ưu tiên |
| --- | --- | --- | --- | --- |
| FR-SYS-01 | Nhật ký hoạt động | Ghi đăng nhập, đặt lại mật khẩu, sửa và công bố điểm, xóa dữ liệu: ai, khi nào, từ địa chỉ nào | AD | C |
| FR-SYS-02 | Sao lưu | Sao lưu cơ sở dữ liệu định kỳ | AD | T |

### 3.13 Ma trận phân quyền tóm tắt

| Nhóm chức năng | AD | TBM | GV | SV | PH |
| --- | --- | --- | --- | --- | --- |
| Tài khoản, vai trò | Toàn quyền | Xem giảng viên bộ môn mình | Hồ sơ của mình | Hồ sơ của mình | Hồ sơ của mình |
| Danh mục đào tạo | Toàn quyền | Môn học, lớp học phần bộ môn mình | Xem | Xem | Xem |
| Thời khóa biểu, lịch thi | Quản lý | Quản lý bộ môn mình | Xem lịch dạy | Xem lịch học | Xem lịch của con |
| Điểm danh | Xem, điều chỉnh | Xem bộ môn mình | Điểm danh lớp mình | Xem của mình | Xem của con |
| Điểm số | Công bố, điều chỉnh | Công bố bộ môn mình | Nhập điểm lớp mình | Xem của mình | Xem của con |
| Thông báo | Gửi mọi phạm vi | Gửi trong bộ môn | Gửi lớp học phần mình | Nhận | Nhận |
| Tin nhắn | Không | Nhắn tin | Nhắn tin | Nhắn tin | Nhắn tin |
| Đơn từ | Xử lý đơn khác | Không | Duyệt đơn nghỉ lớp mình | Gửi đơn | Gửi đơn thay con |
| Câu lạc bộ | Quản lý | Xem | Xem (hoặc phụ trách nếu được giao) | Tham gia; quản lý nếu là chủ nhiệm | Xem |
| Báo cáo, nhật ký | Toàn bộ | Báo cáo bộ môn mình | Báo cáo lớp mình | Không | Không |

## 4. Yêu cầu phi chức năng

| Mã | Nhóm | Yêu cầu |
| --- | --- | --- |
| NFR-01 | Bảo mật | Mật khẩu băm BCrypt, không lưu bản rõ; dùng HTTPS; bật CSRF cho các form Thymeleaf |
| NFR-02 | Bảo mật | Khóa tạm khi nhập sai nhiều lần; phiên hết hạn khi không hoạt động; thông báo lỗi đăng nhập chung chung, không tiết lộ số điện thoại có tồn tại hay không |
| NFR-03 | Phân quyền | Kiểm tra quyền ở backend cho mọi yêu cầu, không chỉ ẩn nút trên giao diện |
| NFR-04 | Riêng tư | PH chỉ xem con đã liên kết; SV chỉ xem dữ liệu của mình; GV chỉ xem lớp học phần mình dạy; TBM chỉ xem bộ môn mình |
| NFR-05 | Hiệu năng | Trang thông dụng phản hồi trong khoảng 2–3 giây; danh sách dài phải phân trang |
| NFR-06 | Khả dụng | Giao diện tiếng Việt, responsive trên điện thoại và máy tính, chữ rõ cho phụ huynh lớn tuổi |
| NFR-07 | Tương thích | Chạy trên các trình duyệt phổ biến bản mới (Chrome, Edge, Firefox, Safari) |
| NFR-08 | Bảo trì | Mã nguồn tổ chức theo MVC, tên thư mục thống nhất; entity khớp với script cơ sở dữ liệu |
| NFR-09 | Toàn vẹn dữ liệu | Ràng buộc ở cơ sở dữ liệu (khóa ngoại, duy nhất, CHECK); mọi thay đổi điểm, điểm danh, mật khẩu đều ghi nhật ký |
| NFR-10 | Mở rộng | Nghiệp vụ nằm ở lớp service để thêm REST API cho Mobile mà không viết lại |

## 5. Mô hình dữ liệu

Cơ sở dữ liệu được định nghĩa trong script `fpt_contact_book.sql` (SQL Server, 36 bảng). Quy ước: tên bảng và cột bằng tiếng Anh, snake\_case; khóa chính `BIGINT IDENTITY`; NVARCHAR cho tiếng Việt; `created_at` có giá trị mặc định, `updated_at` do ứng dụng cập nhật.

| Nhóm | Bảng |
| --- | --- |
| Tài khoản, vai trò | `users`, `roles`, `user_roles` |
| Hồ sơ theo vai trò | `students`, `teachers`, `parents`, `parent_student` |
| Danh mục đào tạo | `faculties`, `departments`, `majors`, `classes`, `subjects`, `semesters`, `class_sections`, `enrollments` |
| Lịch học, lịch thi | `rooms`, `class_sessions`, `exams` |
| Điểm danh, điểm số | `attendance`, `grade_components`, `grades` |
| Thông báo, tệp | `notifications`, `notification_recipients`, `notification_files`, `files` |
| Tin nhắn | `conversations`, `conversation_members`, `messages` |
| Đơn từ | `requests`, `request_files`, `request_reviews` |
| Câu lạc bộ | `clubs`, `club_members`, `club_activities`, `club_activity_participants` |
| Nhật ký | `audit_logs` |

**Quan hệ chính**

- `users` – `roles`: nhiều–nhiều qua `user_roles`, có `department_id` để giới hạn phạm vi bộ môn.
- `users` – `students` / `teachers` / `parents`: một–một. `parents` – `students`: nhiều–nhiều qua `parent_student`.
- Khoa → bộ môn → môn học; khoa → ngành → lớp hành chính.
- Lớp học phần nối môn học, học kỳ và giảng viên; sinh viên vào lớp học phần qua `enrollments`.
- Mỗi lớp học phần có nhiều buổi (`class_sessions`); điểm danh gắn với từng buổi và từng sinh viên.
- Điểm gắn với một lần ghi danh (`enrollments`) và một đầu điểm (`grade_components`).
- Thông báo có danh sách người nhận kèm trạng thái đã đọc; đơn từ có lịch sử duyệt.

## 6. Các kịch bản sử dụng tiêu biểu

**UC-01 Đăng nhập.** Người dùng nhập số điện thoại và mật khẩu. Hệ thống tra theo số điện thoại, so khớp mật khẩu băm và kiểm tra tài khoản chưa bị khóa. Nếu đúng thì tạo phiên, nạp các vai trò và mở trang chủ; nếu mật khẩu là mật khẩu tạm thì chuyển sang màn hình đổi mật khẩu; nếu sai thì báo lỗi chung và cho nhập lại.

**UC-02 Admin tạo người dùng nhiều vai trò.** AD nhập số điện thoại, họ tên, chọn các vai trò (ví dụ TEACHER và HEAD\_OF\_DEPARTMENT kèm bộ môn) và hồ sơ giảng viên. Hệ thống sinh mật khẩu tạm, hiện một lần cho AD để báo lại người dùng.

**UC-03 Admin đặt lại mật khẩu.** Người dùng liên hệ AD. AD chọn tài khoản, hệ thống tạo mật khẩu tạm, đặt cờ bắt buộc đổi mật khẩu và ghi nhật ký. Ở lần đăng nhập kế tiếp người dùng phải tự đặt mật khẩu mới.

**UC-04 Giảng viên điểm danh.** GV mở buổi học hôm nay của lớp học phần mình, đánh dấu trạng thái từng sinh viên và lưu. Hệ thống ghi người điểm danh, thời điểm điểm danh, và cảnh báo nếu có sinh viên vắng vượt ngưỡng.

**UC-05 Gửi và duyệt đơn nghỉ.** SV (hoặc PH) tạo đơn kèm minh chứng. GV lớp học phần nhận thông báo, duyệt hoặc từ chối kèm ghi chú. Người gửi nhận kết quả; nếu duyệt thì các buổi vắng trong khoảng ngày được chuyển thành vắng có phép.

**UC-06 Công bố điểm.** GV nhập điểm các đầu điểm. TBM kiểm tra rồi công bố. Từ lúc đó SV và PH mới xem được điểm; hệ thống ghi người công bố và thời điểm công bố.

**UC-07 Phụ huynh xem điểm của con.** PH đăng nhập, chọn một trong các con đã được liên kết, xem điểm và điểm danh. Hệ thống từ chối truy cập dữ liệu của sinh viên chưa liên kết.

**UC-08 Sinh viên tham gia câu lạc bộ.** SV gửi yêu cầu tham gia, chủ nhiệm CLB duyệt. Sau khi được duyệt, SV đăng ký tham gia các hoạt động của CLB.

## 7. Đề xuất lộ trình triển khai (Web trước)

| Giai đoạn | Nội dung | Ghi chú |
| --- | --- | --- |
| 1 | Nền tảng: dự án Spring Boot, kết nối SQL Server, đăng nhập bằng số điện thoại, phân quyền theo vai trò, trang chủ theo vai trò | Làm trước |
| 2 | Quản lý người dùng (4 màn hình CRUD), gán vai trò, đặt lại mật khẩu, liên kết PH – SV | Cần cho mọi module |
| 3 | Danh mục đào tạo, lớp học phần, ghi danh | Dữ liệu nền |
| 4 | Thời khóa biểu, lịch thi, điểm danh, điểm số | Chức năng cốt lõi |
| 5 | Thông báo, tin nhắn, đơn từ | Tăng tương tác |
| 6 | Câu lạc bộ, báo cáo, nhật ký | Hoàn thiện |
| 7 | REST API với JWT, nối ứng dụng Mobile | Sau khi Web ổn định |

Với đồ án môn học, nên chốt trước các mục ưu tiên **C** làm phạm vi bắt buộc, các mục còn lại làm nếu còn thời gian.

## 8. Định hướng cho Mobile (giai đoạn sau)

- Mobile gọi REST API của chính backend này; không có cơ sở dữ liệu cục bộ, chỉ lưu token đăng nhập và cài đặt giao diện.
- Màn hình đăng nhập Flutter đã dựng cần đổi nhãn Email thành Số điện thoại và dùng bàn phím số.
- Mobile không có chức năng tạo tài khoản, đăng ký hay quên mật khẩu.
- Các thao tác tạo, sửa, xóa dữ liệu gốc (người dùng, danh mục, lịch, điểm) chỉ có trên Web.
- Quyền được kiểm tra ở backend cho mọi API.
- Dữ liệu đổi trên Web hiện trên Mobile ở lần tải kế tiếp; khi mất mạng Mobile báo lỗi và cho thử lại, không có chế độ ngoại tuyến.

## 9. Điểm cần xác nhận

1. Trưởng bộ môn quản lý theo bộ môn (đơn vị chứa nhiều môn học) hay theo từng môn học? Tài liệu đang giả định theo bộ môn.
2. Ai công bố điểm: trưởng bộ môn, admin, hay cả hai? Tài liệu đang giả định cả hai.
3. Mỗi số điện thoại chỉ gắn với một tài khoản. Nếu hai phụ huynh hoặc phụ huynh và sinh viên dùng chung một số thì xử lý thế nào?
4. Đơn nghỉ do giảng viên lớp học phần duyệt, hay cần giáo vụ hoặc trưởng bộ môn duyệt thêm?
5. Có yêu cầu nộp kèm sơ đồ (use case, ERD, kiến trúc) cùng bản SRS này không?

---

*Ghi chú: danh sách chức năng được tổng hợp từ các tính năng thường thấy của hệ thống sổ liên lạc điện tử (như eNetViet) và điều chỉnh cho môi trường đại học. Bạn nên đối chiếu thêm với tài liệu hoặc bản demo chính thức của eNetViet khi cần trích dẫn.*
