# Rà soát toàn dự án – Giai đoạn 8a

Ngày rà soát: 20/10/2026. Nhánh `chore/phase-8a-review`, tách từ `main` tại `5ab0509` (đã gồm giai đoạn 7b).
Tài liệu này **chỉ báo cáo**, không sửa code. Mỗi phát hiện có mã (ví dụ `S1`) để chọn mục cần sửa.

## Cách rà soát và số liệu hiện tại

- **Build:** `mvn -o clean verify -Dmaven.compiler.showWarnings=true -Dmaven.compiler.showDeprecation=true`.
  Dự án không có `mvnw` (xem C8), nên dùng `mvn`; hai cờ chỉ để in cảnh báo của javac. Kết quả **PASSED**:
  1211 run / 1211 passed / 0 failed / 0 skipped, thời gian 1 phút 20 giây.
- **Chạy ứng dụng:** chạy với SQL Server localhost (`FptContactBook`), đăng nhập bằng 5 tài khoản mẫu. Mở các trang
  chính ở chiều rộng 360px trong trình duyệt tích hợp, đo tràn ngang (`scrollWidth > 360`) và chụp màn hình. Ảnh nằm
  trong phiên làm việc, không lưu vào repo (giai đoạn này chỉ được sửa tài liệu này).
- **Đọc code:** route lấy từ mọi controller (209 cặp phương thức + đường dẫn), `SecurityConfig`, `@PreAuthorize`, các
  lớp kiểm tra phạm vi ở service; mọi repository; mọi template và `app.css`; test theo từng route.
- **Skill đã dùng:** `ponytail-audit` (khung rà soát: lỗi, rủi ro, chịu tải, test, tốc độ, code thừa), `security-review`
  (phần 3), `lint-and-validate` (phần 7: dự án không có plugin lint/checkstyle, kiểm tra tĩnh chỉ là cảnh báo javac).
- **Độ tin cậy:** N+1 và chỉ mục được xác định bằng **đọc code**, chưa đo bằng log SQL (bật log SQL cần sửa cấu hình,
  ngoài phạm vi bước này). Danh sách test còn thiếu được tạo bằng script dò rồi **kiểm tay**; script đếm thiếu các
  test dùng `@ParameterizedTest`, nên mục 1.3 chỉ ghi những chỗ đã xác nhận bằng tay.

**Mức độ.** Cao: lỗ hổng hoặc lộ thông tin cần xử lý ngay. Trung bình: lỗi hoặc thiếu sót có ảnh hưởng thật đến
người dùng, hiệu năng hoặc khả năng phát hiện lỗi. Thấp: lệch nhỏ, dọn dẹp, tài liệu.
**Ước lượng công** tính bằng giờ làm của một người đã quen dự án, gồm cả viết test.

## Bảng tổng hợp (xếp theo mức độ)

| Mã | Mức | Phần | Tóm tắt | Công |
|---|---|---|---|---|
| S1 | Cao | Bảo mật | README ghi tài khoản `sa` và mật khẩu DB (trùng mật khẩu mẫu); lịch sử git còn mật khẩu này làm giá trị mặc định | 0,5 h + đổi mật khẩu |
| P1 | Trung bình | Hiệu năng | 2 `@ControllerAdvice` chạy 2–3 truy vấn đếm cho **mọi** request (ảnh, tải tệp, export, làm mới chat 10 giây/lần) | 2 h |
| P2 | Trung bình | Hiệu năng | Form gửi thông báo của ADMIN nạp mọi lớp học phần của mọi học kỳ vào một ô chọn | 2 h |
| P3 | Trung bình | Hiệu năng | `audit_logs` không có chỉ mục cho `action`, `entity_type`; bảng tăng nhanh nhất (LOGIN_FAILED) | 0,5 h (đề xuất) |
| U1 | Trung bình | Giao diện | 360px: bảng điểm danh của phụ huynh đẩy cột Chuyên cần / Tỷ lệ vắng ra ngoài màn hình | 3 h |
| U2 | Trung bình | Giao diện | 360px: bảng điểm danh một buổi của giảng viên chật, tên sinh viên và lựa chọn bị bẻ dòng | 3 h |
| U3 | Trung bình | Giao diện | Ô ngày `type="date"` hiện theo ngôn ngữ trình duyệt (`mm/dd/yyyy`), lệch quy định `dd/MM/yyyy` | 3 h |
| T1 | Trung bình | Kiểm thử | `MessageService.older` và `GET /messages/{id}/older` không có test nào (kể cả quyền thành viên) | 1 h |
| T2 | Trung bình | Kiểm thử | Quản lý hoạt động CLB (danh sách, sửa, hủy, ghi nhận tham dự) không có test MockMvc theo vai trò / phạm vi | 2 h |
| T3 | Trung bình | Kiểm thử | POST sửa/hủy buổi học, sửa/xóa lịch thi của TBM bộ môn khác chưa có test 403 (chỉ test GET form) | 1 h |
| T4 | Trung bình | Kiểm thử | POST điều chỉnh điểm sau công bố bởi GV chưa có test 403 (chỉ test GET) | 0,5 h |
| B1 | Thấp | Lỗi | `uq_class_sections_code` có trong script nhưng code và giả định 42 ghi "không có UNIQUE"; vi phạm không được đổi thành thông báo riêng | 1 h |
| A1 | Thấp | Phân quyền | SRS: GV, SV, PH "Xem" danh mục đào tạo; code không có màn hình xem danh mục cho các vai trò này | 4–6 h hoặc sửa SRS |
| A2 | Thấp | Phân quyền | SRS: TBM "Không" với đơn từ; TBM vào được `/teacher/requests` (danh sách rỗng nếu không là người duyệt) | 0,5 h |
| A3 | Thấp | Phân quyền | ADMIN duyệt đơn nghỉ khi SV không có chủ nhiệm (giả định 85); SRS chỉ ghi "AD xử lý đơn khác" | 0 h (xác nhận) |
| A4 | Thấp | Phân quyền | GV phụ trách CLB chỉ xem (giả định 101); SRS "phụ trách nếu được giao" | 0 h (xác nhận) |
| S2 | Thấp | Bảo mật | Thư mục `uploads/` (ảnh đại diện, minh chứng, tệp đính kèm) không có trong `.gitignore` | 0,1 h |
| S3 | Thấp | Bảo mật | Xuất Excel là GET có ghi audit; một liên kết từ trang khác có thể kích hoạt | 2 h |
| S4 | Thấp | Bảo mật | Log WARN của Hibernate ghi giá trị trùng (số điện thoại) khi vi phạm UNIQUE | 0,5 h |
| S5 | Thấp | Bảo mật | Chưa cấu hình HTTPS / cookie `Secure` (NFR-01) | Khi triển khai |
| S6 | Thấp | Bảo mật | Mọi lần đăng nhập sai thêm một dòng audit, không giới hạn (đã chấp nhận ở giả định 113) | 0 h (theo dõi) |
| S7 | Thấp | Chịu tải | Bộ đếm khóa tạm nằm trong bộ nhớ, không chia sẻ giữa nhiều instance (giả định 9) | Khi chạy nhiều instance |
| P4 | Thấp | Hiệu năng | Các ô chọn giảng viên, lớp, môn, phòng, GV phụ trách nạp toàn bộ bảng | 3 h |
| P5 | Thấp | Hiệu năng | Báo cáo nạp mọi lớp của học kỳ vào bộ nhớ rồi mới phân trang (giả định 119) | 0 h (theo dõi) |
| P6 | Thấp | Hiệu năng | Đề xuất thêm 2 chỉ mục: `requests(class_section_id, status)`, `grades(component_id)` | 0,5 h (đề xuất) |
| U4 | Thấp | Giao diện | 16 template dùng nút xanh Bootstrap `btn-primary`, lệch màu cam thương hiệu (`btn-orange`) | 1 h |
| U5 | Thấp | Giao diện | 360px: thanh trên bị bẻ thành 3 dòng (họ tên, "Đăng xuất") | 1 h |
| U6 | Thấp | Giao diện | Trang chủ 5 vai trò vẫn hiện thẻ "Sắp có – các chức năng của giai đoạn sau" | 1 h |
| U7 | Thấp | Giao diện | 360px: ô số liệu báo cáo bẻ mã lớp giữa chữ (`PRN211_FA` / `26_01`) | 0,3 h |
| U8 | Thấp | Giao diện | Trang đăng nhập ghi "dành cho sinh viên" dù mọi vai trò dùng chung | 0,1 h |
| U9 | Thấp | Giao diện | `app.css` lặp màu hover `#EA580C` 4 lần và màu Bootstrap viết cứng thay vì biến | 0,5 h |
| T5 | Thấp | Kiểm thử | Gỡ con khỏi phụ huynh (`unlinkChild`, `/children/{id}/remove`) không có test; đặt PH chính chưa có test MockMvc | 1 h |
| T6 | Thấp | Kiểm thử | Số đơn chờ duyệt trên menu (`pendingForTeacher`, `pendingForAdmin`) không có test kiểm con số | 1 h |
| T7 | Thấp | Kiểm thử | Danh mục: POST sửa của vai trò khác ADMIN và các route con khi chưa đăng nhập chưa có test riêng | 0,5 h |
| T8 | Thấp | Kiểm thử | `/club-logos/{file}` và `/notifications/{id}/files/{fileId}` chưa test truy cập khi chưa đăng nhập | 0,3 h |
| C1 | Thấp | Dọn dẹp | 5 phương thức repository chỉ được test gọi, code chính không dùng | 0,5 h |
| C2 | Thấp | Dọn dẹp | 3 hằng thông báo, 4 khóa `messages.properties`, 3 class CSS không ai dùng | 0,5 h |
| C3 | Thấp | Dọn dẹp | 1 import thừa (`NotificationAudienceTest`); 1 cảnh báo unchecked (`AttendanceServiceTest`) | 0,2 h |
| C4 | Thấp | Dọn dẹp | SRS 1.3 còn công nghệ cũ: H2 (dòng 30), Spring Boot 3.3.4 (dòng 23), springdoc (dòng 28) | 0,2 h |
| C8 | Thấp | Dọn dẹp | `CLAUDE.md` mục 9 dùng `./mvnw` nhưng repo không có Maven Wrapper | 0,3 h |
| W1–W6 | Thấp | Cảnh báo | Cảnh báo khi build và khởi động (xem phần 7) | 0,5–2 h |

Tổng: 1 Cao, 10 Trung bình, 29 Thấp (không tính W1–W6).

---

## 1. Phân quyền

### 1.1 Bảng đường dẫn × vai trò (theo code thực tế)

Lớp chặn thứ nhất là `SecurityConfig` (khớp theo thứ tự), lớp thứ hai là `@PreAuthorize` ở controller, lớp thứ ba là
kiểm tra phạm vi ở service. ✔ = được vào; ✔ⁿ = được vào trong phạm vi (n là phạm vi ở dưới); ✘ = 403;
mọi đường dẫn khi chưa đăng nhập đều chuyển tới `/login` (trừ `/login`, tài nguyên tĩnh, `/error`).

| Nhóm đường dẫn | Lớp chặn | AD | TBM | GV | SV | PH |
|---|---|---|---|---|---|---|
| `/admin` (trang chủ quản trị) | `/admin/**` hasRole ADMIN | ✔ | ✘ | ✘ | ✘ | ✘ |
| `GET /admin/users`, `GET /admin/users/{id}` | GET riêng: ADMIN, HEAD | ✔ | ✔¹ | ✘ | ✘ | ✘ |
| Thêm/sửa/khóa/mở khóa/đặt lại mật khẩu, vai trò, liên kết PH, nhập Excel người dùng | `/admin/**` + `@PreAuthorize ADMIN` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Danh mục `/admin/catalog/**` (khoa, bộ môn, ngành, lớp, học kỳ, phòng) | `/admin/**` + `@PreAuthorize ADMIN` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Môn học, đầu điểm `/admin/subjects/**` | ADMIN, HEAD + `SubjectAccessPolicy` | ✔ | ✔² | ✘ | ✘ | ✘ |
| Lớp học phần, buổi học, lịch thi, điểm danh, bảng điểm, công bố, điều chỉnh `/admin/sections/**` | ADMIN, HEAD + `ClassSectionService.findInScope` | ✔ | ✔² | ✘ | ✘ | ✘ |
| Ghi danh `/admin/sections/{id}/enrollments/**` | thêm `@PreAuthorize ADMIN` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Lưu điểm danh qua `/admin/sections/…/attendance` (điều chỉnh) | `AttendanceScope` write → chỉ ADMIN | ✔ | ✘ (xem được) | ✘ | ✘ | ✘ |
| Xử lý đơn `/admin/requests/**` | `/admin/**` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Quản lý CLB `/admin/clubs/**` | `/admin/**` + `@PreAuthorize ADMIN` | ✔ | ✘ | ✘ | ✘ | ✘ |
| Nhật ký `/admin/audit-logs/**` | `/admin/**` + `AuditLogService.requireAdmin` | ✔ | ✘ | ✘ | ✘ | ✘ |
| `/teacher/**` (trang chủ, lịch dạy, lớp giảng dạy, điểm danh, nhập điểm, duyệt đơn) | TEACHER, HEAD + hồ sơ GV (`AttendanceScope.teacher`) | ✘ | ✔³ | ✔³ | ✘ | ✘ |
| `/student/**` | STUDENT + dữ liệu của chính mình | ✘ | ✘ | ✘ | ✔ | ✘ |
| `/parent/**` | PARENT + `ScheduleViewService.requireChild` | ✘ | ✘ | ✘ | ✘ | ✔⁴ |
| `/messages/**` | SV, PH, GV, TBM + `MessagingPolicy` | ✘ | ✔⁵ | ✔⁵ | ✔⁵ | ✔⁵ |
| `/notifications` (hộp thư), chi tiết, tải tệp | đăng nhập + người nhận/người gửi | ✔ | ✔ | ✔ | ✔ | ✔ |
| `/notifications/new`, `/notifications/sent` | `@PreAuthorize SENDERS` + `NotificationAudience` | ✔ | ✔² | ✔³ | ✘ | ✘ |
| `/clubs`, `/clubs/{id}`, thành viên đang hoạt động | đăng nhập | ✔ | ✔ | ✔ | ✔ | ✔ |
| Gửi yêu cầu, rời CLB, đăng ký hoạt động | `ClubAccessPolicy.requireStudent` | ✘ | ✘ | ✘ | ✔ | ✘ |
| Duyệt thành viên, đổi vai trò CLB | chủ nhiệm ACTIVE của CLB hoặc ADMIN | ✔ | ✘ | ✘ | ✔⁶ | ✘ |
| Hoạt động: tạo, sửa, hủy, ghi nhận tham dự | chủ nhiệm/phó chủ nhiệm ACTIVE hoặc ADMIN | ✔ | ✘ | ✘ | ✔⁶ | ✘ |
| `/reports/attendance`, `/reports/grades` (+ export) | ADMIN, HEAD, TEACHER + `ReportScope` | ✔ | ✔² | ✔³ | ✘ | ✘ |
| `/reports/clubs` | ADMIN | ✔ | ✘ | ✘ | ✘ | ✘ |
| `/requests/{id}/files/{fileId}` (minh chứng) | đăng nhập + người liên quan đơn | ✔ | ✔⁷ | ✔⁷ | ✔⁷ | ✔⁷ |
| `/profile`, `/change-password`, `/avatars/*`, `/club-logos/*` | đăng nhập | ✔ | ✔ | ✔ | ✔ | ✔ |

Phạm vi: ¹ chỉ giảng viên thuộc bộ môn của vai trò HEAD. ² chỉ dữ liệu có môn thuộc bộ môn của vai trò HEAD.
³ chỉ lớp có `teacher_id` là hồ sơ giảng viên của mình; người không có hồ sơ giảng viên nhận 403 hoặc danh sách rỗng.
⁴ chỉ con đã liên kết. ⁵ theo quy tắc nhắn tin (giả định 93). ⁶ đúng CLB mà mình là chủ nhiệm / phó chủ nhiệm.
⁷ người gửi, SV liên quan, PH đã liên kết, người duyệt hiện tại, ADMIN.

### 1.2 Đối chiếu ma trận SRS 3.13

| Nhóm SRS | Khớp | Lệch |
|---|---|---|
| Tài khoản, vai trò | AD toàn quyền; TBM xem GV bộ môn mình; mọi vai trò xem hồ sơ của mình | Không |
| Danh mục đào tạo | AD toàn quyền; TBM môn và lớp học phần bộ môn mình | **A1**: SRS ghi GV, SV, PH "Xem"; code không có màn hình xem danh mục cho các vai trò này (họ chỉ thấy tên môn, lớp qua lịch, điểm, điểm danh) |
| Thời khóa biểu, lịch thi | AD quản lý; TBM quản lý bộ môn mình; GV, SV, PH xem | Không |
| Điểm danh | AD xem và điều chỉnh; TBM xem bộ môn mình; GV điểm danh lớp mình; SV, PH xem | Không |
| Điểm số | AD công bố và điều chỉnh; TBM công bố (và điều chỉnh) bộ môn mình; GV nhập lớp mình; SV, PH xem | TBM được điều chỉnh sau công bố đúng FR-GRADE-06 (ma trận chỉ ghi "Công bố") |
| Thông báo | AD mọi phạm vi; TBM trong bộ môn; GV lớp học phần mình; SV, PH nhận | Không |
| Tin nhắn | AD không; các vai trò khác nhắn tin | Không |
| Đơn từ | AD xử lý đơn khác; GV duyệt đơn nghỉ lớp mình; SV gửi; PH gửi thay con | **A2**: TBM (SRS "Không") vào được `/teacher/requests` vì `/teacher/**` mở cho HEAD; người chỉ có vai trò HEAD, không có hồ sơ GV, thấy danh sách rỗng. **A3**: ADMIN duyệt cả đơn nghỉ khi SV không có chủ nhiệm (giả định 85) |
| Câu lạc bộ | AD quản lý; TBM, PH xem; SV tham gia, quản lý nếu là chủ nhiệm | **A4**: GV phụ trách chỉ xem, không quản lý (giả định 101) |
| Báo cáo, nhật ký | AD toàn bộ; TBM bộ môn mình; GV lớp mình; SV, PH không | Không |

**A1 – GV, SV, PH không có màn hình xem danh mục** (Thấp)
- Vị trí: `SecurityConfig.java:46` (`/admin/**` chỉ ADMIN); SRS 3.13 dòng "Danh mục đào tạo".
- Mô tả: Ma trận SRS cho GV, SV, PH quyền "Xem" danh mục, nhưng mọi trang danh mục nằm dưới `/admin/catalog/**`.
- Đề xuất: Chọn một trong hai: (a) xác nhận với người ra đề rằng "xem" nghĩa là thấy thông tin qua lịch/điểm (chỉ sửa SRS);
  (b) thêm trang chỉ đọc `/catalog/subjects`, `/catalog/classes` cho mọi người đã đăng nhập.
- Công: (a) 0,2 h; (b) 4–6 h.

**A2 – TBM vào được trang duyệt đơn** (Thấp)
- Vị trí: `SecurityConfig.java:47`, `TeacherRequestController`.
- Mô tả: `/teacher/**` mở cho HEAD_OF_DEPARTMENT. Người chỉ có vai trò HEAD vẫn thấy mục "Duyệt đơn" và trang rỗng.
  Không lộ dữ liệu (người duyệt tính theo hồ sơ GV), nhưng lệch ma trận SRS.
- Đề xuất: Ẩn mục "Duyệt đơn" khi người dùng không có hồ sơ giảng viên, hoặc ghi giả định rằng TBM luôn đồng thời là GV.
- Công: 0,5 h.

**A3, A4** (Thấp): đã ghi ở giả định 85 và 101; chỉ cần người ra đề xác nhận. Công 0 h.

### 1.3 Màn hình thiếu test MockMvc (đã kiểm tay)

Mọi nhóm màn hình đều có ít nhất một test đúng vai trò, một test sai vai trò và một test chưa đăng nhập. Các chỗ dưới
đây là **route có ghi dữ liệu hoặc có phạm vi** mà chưa test MockMvc đủ 4 loại (đúng vai trò 200 / sai vai trò 403 /
chưa đăng nhập → `/login` / sai phạm vi 403):

| Route | Thiếu | Mã |
|---|---|---|
| `GET /clubs/{id}/activities`, `GET/POST /clubs/{id}/activities/{aid}/edit`, `POST …/cancel`, `GET/POST …/attendance` | cả 4 loại (chỉ có test service) | T2 |
| `POST /clubs/{id}/members/{mid}/role` | cả 4 loại | T2 |
| `POST /admin/sections/{id}/sessions/{sid}/edit`, `…/cancel`, `GET/POST …/exams/{eid}/edit`, `POST …/exams/{eid}/delete` | sai phạm vi (TBM bộ môn khác), sai vai trò | T3 |
| `POST /admin/sections/{id}/grades/{gid}/adjust` | sai vai trò (GV) | T4 |
| `GET /messages/{id}/older` | cả 4 loại | T1 |
| `POST /admin/users/{id}/children/{sid}/remove`, `POST /admin/users/{id}/parents/{pid}/primary` | sai vai trò, chưa đăng nhập | T5 |
| `POST /admin/catalog/*/{id}/edit` | sai vai trò | T7 |
| `GET /club-logos/{file}`, `GET /notifications/{id}/files/{fid}` | chưa đăng nhập | T8 |

Chi tiết và đề xuất sửa: xem phần 5.

---

## 2. Hiệu năng

### 2.1 Danh sách chưa phân trang

| Màn hình | Truy vấn | Đánh giá |
|---|---|---|
| Form gửi thông báo của ADMIN (`/notifications/new`) | `ClassSectionRepository.findAllByOrderBySemesterStartDateDescCodeAsc` qua `NotificationAudience.sections` | **P2** – tăng mãi theo số học kỳ |
| Form lớp học phần, người dùng, vai trò, buổi học, lịch thi | `CatalogLookupService.teachers/classes/subjects/rooms/majors/departments` | **P4** – nạp toàn bộ bảng |
| Form CLB (GV phụ trách) | `ClubService.advisors` → `UserRoleRepository.findActiveUsersWithRole` | **P4** |
| Bảng điểm danh một buổi, bảng điểm một lớp, báo cáo một lớp | theo sĩ số lớp | Chấp nhận (giả định 64, 75, 119) |
| Báo cáo tổng hợp | `ReportScope` nạp mọi lớp của học kỳ rồi phân trang trong bộ nhớ | **P5** – chấp nhận ở quy mô một trường |

Các danh sách chính khác (người dùng, danh mục, lớp học phần, thông báo, đơn, tin nhắn, CLB, nhật ký) đều phân trang
bằng `Pageable`.

### 2.2 N+1

Đã đọc mọi truy vấn trả `List`/`Page` (91 phương thức) và đoạn `map` sang DTO tương ứng. **Không tìm thấy N+1 trên các
trang danh sách**: truy vấn đều có `@EntityGraph`/`JOIN FETCH`, hoặc gom số liệu phụ bằng một truy vấn `IN (ids của trang)`
(ví dụ `MessageService.conversations`, `NotificationService.sent`, `ClubService.list`, `UserService.list`). Các truy cập
quan hệ lazy chỉ lấy `getId()` (không nạp thêm). Mức tin cậy: đọc code, chưa đo bằng log SQL.

Điểm gần với N+1 nhất là **P1**: số truy vấn cố định thêm vào **mỗi request**, không phải mỗi dòng.

**P1 – Truy vấn đếm cho menu chạy ở mọi request** (Trung bình)
- Vị trí: `controller/NotificationModelAdvice.java:24-33`, `controller/RequestModelAdvice.java:22-33`.
- Mô tả: `@ModelAttribute` trong `@ControllerAdvice` chạy trước **mọi** handler, kể cả handler trả `ResponseEntity`
  hoặc mảnh HTML: ảnh `/avatars/*`, `/club-logos/*`, tải tệp, xuất Excel, POST rồi chuyển hướng, và
  `GET /messages/{id}/newer` mà trang chat gọi 10 giây/lần. Mỗi lần là 2 COUNT (thông báo, tin nhắn) và thêm 1 truy vấn
  đếm đơn chờ duyệt cho GV/TBM/ADMIN. Ví dụ một trang có 20 ảnh đại diện sinh thêm khoảng 40–60 truy vấn; 100 người mở chat
  sinh thêm khoảng 30 truy vấn mỗi giây chỉ để đếm.
- Đề xuất: Chỉ tính khi kết quả là một trang đầy đủ, bằng một `HandlerInterceptor.postHandle` kiểm tra
  `modelAndView != null`, tên view không bắt đầu bằng `redirect:` và không phải mảnh. Hoặc giới hạn advice bằng
  `@ControllerAdvice(assignableTypes = …)`. Thêm test đếm số truy vấn bằng Hibernate statistics.
- Công: 2 h.

**P2 – Ô chọn lớp học phần của ADMIN khi gửi thông báo** (Trung bình)
- Vị trí: `service/NotificationAudience.java:91-104`; `templates/notifications/form.html`.
- Mô tả: ADMIN thấy mọi lớp học phần của mọi học kỳ (giả định 78). Sau vài năm là hàng nghìn `<option>`, trang nặng và khó chọn.
- Đề xuất: Lọc theo học kỳ (mặc định học kỳ hiện tại, có ô chọn học kỳ), hoặc ô tìm theo mã lớp như ô tìm sinh viên khi ghi danh.
- Công: 2 h.

**P4 – Ô chọn nạp toàn bộ bảng** (Thấp)
- Vị trí: `service/CatalogLookupService.java:37-93`, `service/ClubService.java:96-99`.
- Mô tả: Ở quy mô một trường (vài trăm GV, vài trăm lớp) vẫn ổn. Bảng sẽ lớn dần: `teachers()` dùng cho form lớp học phần,
  `advisors()` cho form CLB.
- Đề xuất: Giữ nguyên tới khi quá khoảng 500 dòng; khi đó đổi sang ô tìm (nhập mã), như cách nhập GV chủ nhiệm ở giả định 37.
- Công: 3 h khi cần.

**P5** (Thấp): đã ghi ở giả định 119. Theo dõi thời gian trang báo cáo của ADMIN khi số lớp mỗi học kỳ vượt khoảng 1000.

### 2.3 Chỉ mục nên thêm (chỉ đề xuất, chưa tạo migration)

Đã đối chiếu 35 chỉ mục và 23 ràng buộc UNIQUE trong script với các điều kiện `WHERE`/`ORDER BY` của repository.

**P3 – `audit_logs`** (Trung bình)
- Vị trí: `repository/AuditLogRepository.java` (`search`); script chỉ có `ix_audit_created(created_at)`, `ix_audit_user(user_id)`.
- Mô tả: Trang nhật ký lọc theo `action` và `entity_type`, sắp theo `id` giảm dần. Từ giai đoạn 7b mỗi lần đăng nhập sai
  thêm một dòng, nên đây là bảng lớn nhất. Lọc theo hành động sẽ quét toàn bảng.
- Đề xuất: `CREATE INDEX ix_audit_action ON audit_logs(action, id DESC)` và
  `CREATE INDEX ix_audit_entity ON audit_logs(entity_type, entity_id)`. Thêm bằng migration mới, không sửa script gốc.
- Công: 0,5 h (sau khi duyệt thay đổi cấu trúc dữ liệu).

**P6 – Chỉ mục phụ** (Thấp)
- `requests(class_section_id, status)`: hàng đợi duyệt của GV (`UserRequestRepository.findTeacherQueue`) lọc theo lớp học
  phần của GV và trạng thái; hiện chỉ có `ix_req_status`, `ix_req_student`, `ix_req_requester`.
- `grades(component_id)`: `GradeRepository.existsByComponentId`, `findComponentIdsWithGrades` (xóa/sửa đầu điểm);
  `uq_grades` bắt đầu bằng `enrollment_id` nên không dùng được.
- Đã kiểm và **không cần**: `club_activity_participants` (khóa chính `(activity_id, user_id)` đã phục vụ việc đếm chỗ),
  `attendance` (`uq_attendance` bắt đầu bằng `session_id`), `enrollments` (`uq_enrollments` bắt đầu bằng `class_section_id`),
  `club_members` (`uq_club_members` bắt đầu bằng `club_id`), `messages` (`ix_msg_conv`).
- Công: 0,5 h (đề xuất, cần duyệt).

---

## 3. Bảo mật

**Đã kiểm và ổn (không có phát hiện):**
- **Escape đầu ra:** không có `th:utext` (2 kết quả tìm được chỉ là chú thích). Dữ liệu biểu đồ in bằng JavaScript
  inlining, đã escape; có test với `</SCRIPT>`.
- **CSRF:** mọi `<form method="post">` (đã đếm cả form nhiều dòng) đều có `th:action`, nên Thymeleaf tự chèn token.
  CSRF không bị tắt.
- **Entity ra view:** controller không import entity nào (0 dòng), chỉ import enum. `spring.jpa.open-in-view=false`.
- **Upload:**
  - Ảnh đại diện / logo: JPG/PNG tối đa 2 MB, kiểm cả chữ ký file lẫn Content-Type, tên file UUID; khi phục vụ, tên file
    phải khớp regex UUID (`ImageStore.java:25-26, 61-66`).
  - Tệp đính kèm / minh chứng: đuôi, Content-Type và chữ ký phải cùng khớp, tối đa 5 MB/tệp; tên lưu là UUID (regex ở
    `AttachmentStorage.java:39-40`); khi tải chỉ đọc từ khóa `attachments/`, không có path traversal.
  - Tải về dùng `ContentDisposition…filename(name, UTF_8)` nên tên file không chèn được header; quyền tải kiểm ở service.
- **Lỗi:** trang 403, 404, 413, 500 không in exception. `GlobalExceptionHandler` đổi lỗi vi phạm ràng buộc thành thông
  báo chung, không lộ SQL. Spring Boot mặc định không trả stack trace.
- **Mật khẩu:** BCrypt (strength 10); không ghi mật khẩu vào log hay audit (có test ở 7b). `application.properties` lấy
  thông tin DB từ biến môi trường, mặc định mật khẩu rỗng. `application-example.properties` dùng giá trị mẫu dạng giữ chỗ.

**S1 – Mật khẩu cơ sở dữ liệu trong README và lịch sử git** (Cao)
- Vị trí: `README.md:34` (bảng biến môi trường: `DB_USERNAME` = `sa`, `DB_PASSWORD` = một giá trị cụ thể). Lịch sử git:
  commit `38bbf7f` (16/10/2026) đặt giá trị đó làm mặc định của `spring.datasource.password`; `c7b1870` (17/10/2026) đã
  gỡ khỏi file, nhưng giá trị vẫn còn trong lịch sử. Tài liệu này không chép lại giá trị.
- Mô tả: Giá trị này **trùng mật khẩu chung của tài khoản mẫu** trong `CLAUDE.md` mục 10. Nếu đây cũng là mật khẩu thật
  của tài khoản `sa` trên SQL Server của máy dev, thì mật khẩu quản trị DB đang nằm công khai trong repo đã push lên `origin`.
  `sa` có toàn quyền trên mọi database của instance.
- Đề xuất:
  1. Đổi mật khẩu `sa` (nếu đúng là mật khẩu thật); tạo login riêng chỉ có quyền trên `FptContactBook` và
     `FptContactBook_test`, không dùng `sa`.
  2. Trong README thay giá trị bằng chỗ giữ chỗ (ví dụ `<mật khẩu của bạn>`).
  3. Có muốn xóa khỏi lịch sử git hay không là quyết định của bạn: việc này phải viết lại lịch sử và force push, nên cần duyệt riêng.
- Công: 0,5 h, cộng thời gian đổi mật khẩu trên SQL Server.

**S2 – `uploads/` chưa có trong `.gitignore`** (Thấp)
- Vị trí: `.gitignore`; `application.properties:52` (`app.upload-dir=./uploads`).
- Mô tả: Khi chạy app từ thư mục gốc, ảnh đại diện, minh chứng đơn nghỉ và tệp đính kèm (dữ liệu cá nhân) được lưu vào
  `./uploads` trong repo. Chỉ cần một lệnh `git add .` là chúng bị commit.
- Đề xuất: Thêm `uploads/` vào `.gitignore`.
- Công: 0,1 h.

**S3 – Xuất Excel là GET có ghi audit** (Thấp)
- Vị trí: `controller/ReportController.java` (`/reports/*/export`).
- Mô tả: Một liên kết trên trang khác có thể mở export dưới phiên của người dùng (cookie phiên vẫn được gửi khi điều
  hướng cấp cao nhất). Không lộ dữ liệu cho kẻ tấn công vì file tải về máy nạn nhân, nhưng nhật ký sẽ có dòng
  EXPORT_REPORT mà người dùng không chủ động làm. (Đã nêu ở review 7b.)
- Đề xuất: Chuyển export sang POST có CSRF (form có nút "Xuất Excel").
- Công: 2 h.

**S4 – Dữ liệu cá nhân trong log** (Thấp)
- Vị trí: log `org.hibernate.orm.jdbc.error` (WARN) khi vi phạm UNIQUE; `GlobalExceptionHandler.java:28` ghi kèm cả exception.
- Mô tả: Log có dạng "The duplicate key value is (0900000001)", tức ghi số điện thoại (thấy được trong log build).
- Đề xuất: Đặt `logging.level.org.hibernate.orm.jdbc.error=ERROR` (hoặc OFF) và trong handler chỉ ghi tên ràng buộc,
  không ghi cả exception.
- Công: 0,5 h.

**S5 – Chưa cấu hình HTTPS** (Thấp)
- Vị trí: `application.properties` (không có `server.ssl.*`, `server.servlet.session.cookie.secure`).
- Mô tả: NFR-01 yêu cầu HTTPS. Hiện chỉ chạy HTTP localhost.
- Đề xuất: Khi triển khai, đặt TLS ở reverse proxy và bật `server.servlet.session.cookie.secure=true`,
  `server.forward-headers-strategy=framework`. Khi đó phải xem lại giả định 14 (IP lấy từ `remoteAddr`).
- Công: tính vào giai đoạn triển khai.

**S6** (Thấp): mỗi lần đăng nhập sai thêm một dòng `audit_logs`, không giới hạn; đã chấp nhận ở giả định 113. Theo dõi kích thước bảng, liên quan P3.

**S7** (Thấp, chịu tải): `LoginAttemptService` lưu bộ đếm trong bộ nhớ (giả định 9). Nếu chạy nhiều instance, kẻ dò mật khẩu
được 5 lần cho mỗi instance. Cần chuyển sang lưu dùng chung (bảng DB hoặc cache phân tán) khi mở rộng.

---

## 4. Giao diện

### 4.1 Kiểm ở 360px (trình duyệt tích hợp, dữ liệu mẫu)

Đã mở 46 trang bằng 5 vai trò, đo `document.documentElement.scrollWidth`:

| Vai trò | Trang đã đo | Tràn ngang cả trang |
|---|---|---|
| ADMIN | trang chủ, người dùng (danh sách, chi tiết, thêm), lớp hành chính, học kỳ, môn học, lớp học phần (danh sách, chi tiết, thống kê điểm danh, bảng điểm, lịch tuần), xử lý đơn, quản lý CLB, chi tiết CLB, gửi thông báo, đã gửi, 3 trang báo cáo, nhật ký, hồ sơ | Không trang nào |
| GV | lịch dạy, thống kê điểm danh lớp, điểm danh một buổi, bảng điểm, duyệt đơn, tin nhắn, nhắn tin mới, thông báo | Không |
| SV | trang chủ, thời khóa biểu, lịch thi, điểm, điểm danh, gửi đơn nghỉ, CLB, CLB của tôi | Không |
| PH | thời khóa biểu của con, lịch thi, điểm, điểm danh, gửi đơn thay con, tin nhắn | Không |
| Đăng nhập | `/login` | Không |

Ảnh đã chụp trong phiên: đăng nhập; trang chủ ADMIN và SV; báo cáo điểm danh một lớp; nhật ký; bảng điểm ADMIN;
lịch dạy GV; điểm danh một buổi; điểm SV; gửi đơn nghỉ; thời khóa biểu và điểm danh của con. Bảng rộng đều cuộn ngang
**bên trong** `.table-responsive`, nên không có trang nào vỡ bố cục. Các vấn đề dùng được nhưng khó dùng:

**U1 – Phụ huynh không thấy ngay tỷ lệ chuyên cần** (Trung bình)
- Vị trí: `templates/common/attendance.html` (bảng theo môn), `fragments/attendance.html` (cells/headers).
- Mô tả: Ở 360px chỉ thấy các cột Môn học, Có mặt, Đi muộn, Vắng có phép; cột Chuyên cần, Tỷ lệ vắng và nhãn cảnh báo
  (thông tin quan trọng nhất với phụ huynh) nằm ngoài màn hình, phải vuốt ngang. NFR-06 nhấn mạnh phụ huynh lớn tuổi.
- Đề xuất: Ở màn hình nhỏ hiện mỗi môn thành một thẻ (tên môn, chuyên cần, tỷ lệ vắng, cảnh báo; các số đếm ở dòng phụ);
  hoặc đưa cột Chuyên cần / Tỷ lệ vắng lên ngay sau tên môn.
- Công: 3 h.

**U2 – Bảng điểm danh một buổi chật trên điện thoại** (Trung bình)
- Vị trí: `templates/common/attendance-sheet.html`.
- Mô tả: Ở 360px, 4 lựa chọn trạng thái xếp dọc trong một cột hẹp, tên sinh viên bị bẻ từng chữ, ô ghi chú bị đẩy ra
  ngoài. Giảng viên thường điểm danh tại lớp bằng điện thoại (UC-04).
- Đề xuất: Ở màn hình nhỏ hiện mỗi sinh viên thành một thẻ, các trạng thái là nhóm nút ngang (`btn-group`, chữ ngắn:
  "Có", "Muộn", "CP", "KP").
- Công: 3 h.

**U3 – Ô ngày hiện theo ngôn ngữ trình duyệt** (Trung bình)
- Vị trí: mọi `<input type="date">`: fragment `fragments/catalog.html:81` (dùng cho form đơn nghỉ
  `requests/leave-form.html:17-18`, học kỳ, CLB…), `admin/users/form.html:42`, `admin/audit-logs/list.html`.
- Mô tả: Trình duyệt tự định dạng ô ngày theo ngôn ngữ hệ điều hành. Trên máy thử, form đơn nghỉ hiện `10/20/2026` và nhật
  ký hiện `mm/dd/yyyy`, lệch quy định "Ngày dd/MM/yyyy" (`CLAUDE.md` mục 6). Người dùng dễ nhập nhầm ngày/tháng.
  Phần hiển thị ngày bằng `#temporals.format` vẫn đúng `dd/MM/yyyy`.
- Đề xuất: Đổi sang ô chữ có mẫu `dd/MM/yyyy` (placeholder, `pattern`) và `@DateTimeFormat(pattern = "dd/MM/yyyy")` ở
  phía server; hoặc giữ ô ngày và ghi rõ trong ASSUMPTIONS rằng định dạng do trình duyệt quyết định.
- Công: 3 h (đổi toàn bộ) hoặc 0,2 h (ghi giả định).

**U4 – Nút chính hai màu** (Thấp)
- Vị trí: 16 template dùng `btn-primary` của Bootstrap (xanh `#0d6efd`), ví dụ nút "Xem", "Tìm kiếm" trong
  `fragments/reports.html`, `admin/audit-logs/list.html`, `fragments/schedule.html`; 29 template khác dùng `btn-orange`.
- Mô tả: Nút chính lúc xanh lúc cam, lệch màu thương hiệu (cam `#F97316`).
- Đề xuất: Ghi đè biến `--bs-btn-*` của `.btn-primary` trong `app.css` cho trùng màu cam, hoặc đổi các nút sang `btn-orange`.
- Công: 1 h.

**U5 – Thanh trên 3 dòng ở 360px** (Thấp)
- Vị trí: `templates/fragments/topbar.html`.
- Mô tả: Họ tên ("Quản trị hệ thống") và nút "Đăng xuất" bị bẻ dòng, thanh trên cao khoảng 100px.
- Đề xuất: Ẩn họ tên ở màn hình nhỏ (`d-none d-sm-inline`), nút đăng xuất chỉ còn biểu tượng (giữ `aria-label`).
- Công: 1 h.

**U6 – Thẻ "Sắp có" còn trên trang chủ** (Thấp)
- Vị trí: `templates/common/home.html:28`; khóa `common.coming-soon`, `common.coming-soon.detail`.
- Mô tả: Mọi giai đoạn Web đã xong nhưng 5 trang chủ vẫn hiện "Các chức năng của giai đoạn sau sẽ xuất hiện tại đây".
- Đề xuất: Thay bằng lối tắt theo vai trò (lịch hôm nay, số thông báo chưa đọc, đơn chờ duyệt…), hoặc bỏ thẻ.
- Công: 1 h (bỏ thẻ 0,1 h).

**U7 – Ô số liệu báo cáo bẻ mã lớp** (Thấp)
- Vị trí: `templates/fragments/reports.html` (fragment `stat`, `fs-4`, `col-6`).
- Mô tả: Ở 360px mã `PRN211_FA26_01` bị cắt thành `PRN211_FA` / `26_01`.
- Đề xuất: Ô chứa chữ dài dùng `col-12` hoặc cỡ chữ nhỏ hơn và `text-break`.
- Công: 0,3 h.

**U8 – Câu giới thiệu ở trang đăng nhập** (Thấp)
- Vị trí: `messages.properties:33` (`auth.login.subtitle`), `templates/auth/login.html:16`.
- Mô tả: Ghi "Sổ liên lạc điện tử dành cho sinh viên" trong khi phụ huynh, giảng viên, quản trị đều đăng nhập ở đây.
- Đề xuất: "Sổ liên lạc điện tử Đại học FPT".
- Công: 0,1 h.

**U9 – Màu viết cứng trong `app.css`** (Thấp)
- Vị trí: `static/css/app.css:41-42, 71-72` (`#EA580C` lặp 4 lần cho hover); `:331` (`#0dcaf0`), `:335` (`#dc3545`).
- Mô tả: Màu hover của nút cam không có biến; hai màu viền lấy giá trị cứng của Bootstrap. Đổi tông màu thương hiệu thì
  phải sửa nhiều chỗ.
- Đề xuất: Thêm `--color-orange-dark` vào `:root`; dùng `var(--bs-info)`, `var(--bs-danger)` cho hai màu viền.
- Công: 0,5 h.

### 4.2 Chữ tiếng Việt

Đã dò chữ thiếu dấu (các từ phổ biến viết không dấu như "Khong", "Lop", "Diem", "Sinh vien"…) trong mọi giá trị của
`messages.properties`, chữ tĩnh trong template và chuỗi trong các lớp `*Messages`: **0 kết quả**. Ngoại lệ đã biết:
cột `roles.name` trong dữ liệu mẫu không dấu, đã tránh bằng cách lấy tên vai trò từ `messages.properties` (giả định 17).
Không tìm thấy lỗi dấu.

---

## 5. Kiểm thử

237 phương thức public trong service và component; 67 phương thức không được test nào gọi trực tiếp. Phần lớn vẫn
được chạy qua test MockMvc của controller. Sau khi kiểm tay, các chỗ **thật sự thiếu** test cho tình huống chính hoặc
tình huống lỗi là:

**T1 – Tải tin cũ hơn** (Trung bình)
- Vị trí: `service/MessageService.java` (`older`), `controller/MessageController.java` (`GET /messages/{id}/older`).
- Mô tả: Không có test service hay MockMvc. Chưa kiểm: người ngoài hội thoại → 403, mốc `before` không thuộc hội thoại,
  trang cuối (không còn tin), tin đã xóa hiện "Tin nhắn đã bị xóa".
- Đề xuất: 1 test service (phân trang theo id, quyền thành viên) và 1 test MockMvc (200 cho thành viên, 403 cho người ngoài,
  `/login` khi chưa đăng nhập).
- Công: 1 h.

**T2 – Quản lý hoạt động CLB qua web** (Trung bình)
- Vị trí: `controller/ClubActivityController.java` (`all`, `edit`, `cancel`, `attendance`), `ClubController` (`/members/{mid}/role`);
  `service/ClubActivityService.java` (`all`, `newForm`, `attendanceSheet` không có test service).
- Mô tả: Luật đã có test service (sửa, hủy, ghi nhận tham dự), nhưng đường web chưa có test cho: thành viên thường mở
  trang quản lý → 403; chủ nhiệm CLB 2 sửa hoặc hủy hoạt động CLB 1 → 403; hoạt động của CLB khác trên đường dẫn → 404
  (giả định 112); chưa đăng nhập.
- Đề xuất: Thêm một nhóm `@Nested` vào `ClubIntegrationTest` với các trường hợp trên.
- Công: 2 h.

**T3 – Sửa lịch của TBM ngoài bộ môn** (Trung bình)
- Vị trí: `controller/ClassSessionController.java`, `controller/ExamController.java`; `ScheduleIntegrationTest.java:197-210`
  chỉ test GET form.
- Mô tả: Chưa có test POST `/sessions/{sid}/edit`, `/sessions/{sid}/cancel`, `/exams/{eid}/edit`, `/exams/{eid}/delete`
  của TBM bộ môn SE với lớp bộ môn IA (403, dữ liệu không đổi). Đây là đường ghi dữ liệu và xóa lịch thi.
- Đề xuất: Thêm vào test `headOtherDepartment` hiện có, kèm kiểm dữ liệu không đổi.
- Công: 1 h.

**T4 – GV gửi POST điều chỉnh điểm** (Trung bình)
- Vị trí: `controller/SectionGradeController.java` (`POST …/grades/{gid}/adjust`); `GradeIntegrationTest.java:207` chỉ test GET.
- Mô tả: Quyền điều chỉnh điểm sau công bố (FR-GRADE-06) chưa có test POST của vai trò sai.
- Đề xuất: POST với GV `0900000003` → 403, điểm không đổi, không có audit ADJUST_GRADE.
- Công: 0,5 h.

**T5 – Gỡ liên kết phụ huynh từ phía phụ huynh** (Thấp)
- Vị trí: `service/ParentLinkService.java` (`unlinkChild`), `controller/ParentLinkController.java` (`removeChild`, `setPrimary`).
- Mô tả: `unlinkChild` không có test; `setPrimary` có test service nhưng chưa có MockMvc.
- Đề xuất: 1 test service + 1 test MockMvc (ADMIN 200, TBM 403).
- Công: 1 h.

**T6 – Số đơn chờ duyệt trên menu** (Thấp)
- Vị trí: `service/RequestService.java` (`pendingForTeacher`, `pendingForAdmin`), `controller/RequestModelAdvice.java`.
- Mô tả: Chưa có test kiểm con số trên menu khớp với số đơn trong hàng đợi (người duyệt tính lúc truy vấn, giả định 85).
- Đề xuất: Test tích hợp: tạo 2 đơn nghỉ (1 lớp có GV, 1 không có chủ nhiệm) → GV thấy 1, ADMIN thấy 1.
- Công: 1 h.

**T7 – Danh mục** (Thấp)
- Vị trí: `CatalogIntegrationTest.java:92-110`.
- Mô tả: Đã test GET danh sách / chi tiết và POST tạo mới với vai trò sai; chưa test POST `/{id}/edit` với vai trò sai,
  và test chưa đăng nhập chỉ áp cho trang danh sách.
- Đề xuất: Thêm `post(path + "/1/edit")` vào vòng lặp `otherRolesAreForbidden`.
- Công: 0,5 h.

**T8 – Tệp khi chưa đăng nhập** (Thấp)
- Vị trí: `GET /club-logos/{file}`, `GET /notifications/{id}/files/{fid}`.
- Mô tả: Đã được `anyRequest().authenticated()` chặn, nhưng chưa có test giữ hành vi này (avatar thì đã có).
- Đề xuất: Thêm 2 URL vào `AccessControlIntegrationTest.anonymousIsRedirectedToLogin`.
- Công: 0,3 h.

---

## 6. Dọn dẹp

**B1 – Mã lớp học phần đã có UNIQUE trong script** (Thấp, lỗi nhỏ)
- Vị trí: script `database/fpt_contact_book.sql` (ràng buộc `uq_class_sections_code`); `service/ClassSectionService.java:41`
  (chú thích ghi "Mã lớp không có UNIQUE trong database"); `docs/ASSUMPTIONS.md` giả định 42;
  `service/ConstraintViolationMapper.java` không có `uq_class_sections_code`.
- Mô tả: Script **có** UNIQUE cho `class_sections.code`, nhưng code và giả định ghi ngược lại. Hai người tạo trùng mã cùng
  lúc thì database chặn, nhưng người dùng nhận thông báo chung "Dữ liệu không hợp lệ hoặc bị trùng" (bị chuyển về trang
  chủ) thay vì lỗi cạnh ô mã. `SERIALIZABLE` ở `create`/`update` không còn cần cho việc chống trùng mã.
- Đề xuất: Thêm `Map.entry("uq_class_sections_code", new String[]{"code", SectionMessages.CODE_TAKEN})`; sửa chú thích và
  giả định 42; cân nhắc bỏ `SERIALIZABLE` nếu không còn lý do khác (cần xem lại phần ghi danh trước).
- Công: 1 h.

**C1 – Phương thức repository chỉ có test gọi** (Thấp)
- Vị trí: `ClassSessionRepository.findByClassSectionIdOrderBySessionNoAsc`,
  `ClassSessionRepository.findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc` (giả định 4),
  `EnrollmentRepository.findByClassSectionId`, `EnrollmentRepository.findByStudentId`,
  `UserRoleRepository.existsByUserIdAndRoleId`.
- Mô tả: Thêm từ giai đoạn 1 "để dành"; code chính không dùng, chỉ `RepositoryQueryTest` gọi.
- Đề xuất: Xóa cùng test tương ứng, hoặc giữ nếu sắp dùng cho REST API.
- Công: 0,5 h.

**C2 – Hằng, khóa thông báo, class CSS không dùng** (Thấp)
- Hằng: `CatalogMessages.SUBJECT_DEPARTMENT_OUT_OF_SCOPE`, `ClubMessages.ONLY_STUDENTS_JOIN`, `GradeMessages.HAS_ERRORS`.
- Khóa `messages.properties`: `schedule.semester-range`, `activity.list`, `activity.time`, `activity.attendance.attended`.
- Class CSS: `.badge-status`, `.brand-header`, `.btn-primary-custom` (trùng `.btn-orange`).
- Đề xuất: Xóa (đã grep toàn bộ `src`, kể cả template, JS và test).
- Công: 0,5 h.

**C3 – Import thừa, cảnh báo unchecked** (Thấp)
- Vị trí: `src/test/java/…/service/NotificationAudienceTest.java` (import `model.ClassSection` không dùng);
  `src/test/java/…/service/AttendanceServiceTest.java` (javac báo "unchecked or unsafe operations").
- Đề xuất: Bỏ import; sửa kiểu generic (thường là `ArgumentCaptor` hoặc `List` thô).
- Công: 0,2 h. Code chính không có import thừa.

**C4 – Tham chiếu H2 và công nghệ cũ trong SRS** (Thấp, chỉ liệt kê)
- `docs/SRS.md:30`: "Lưu ý: `pom.xml` hiện còn H2 và chưa có driver SQL Server…". Thực tế `pom.xml` không có H2 và đã có `mssql-jdbc`.
- `docs/SRS.md:23`: "Spring Boot 3.3.4" (thực tế 4.1.1). `docs/SRS.md:28`: "springdoc OpenAPI" (chưa dùng, chờ giai đoạn REST API).
- `CLAUDE.md:17, 26` nhắc H2 chỉ để nói "không dùng", không cần sửa.
- Mã nguồn, `pom.xml`, README và cấu hình **không** có tham chiếu H2.
- Công: 0,2 h (sửa SRS nếu được phép).

**C8 – Không có Maven Wrapper** (Thấp)
- Vị trí: thư mục gốc (không có `mvnw`, `.mvn/`); `CLAUDE.md` mục 9 ghi `./mvnw clean verify`; `.gitignore` có dòng
  `!.mvn/wrapper/maven-wrapper.jar`.
- Mô tả: Người mới phải tự cài đúng bản Maven; máy dev đang dùng Maven 3.10.0.
- Đề xuất: Thêm wrapper (`mvn wrapper:wrapper`, đây là thay đổi build nên cần duyệt) hoặc sửa `CLAUDE.md` thành `mvn`.
- Công: 0,3 h.

---

## 7. Cảnh báo khi build và khi khởi động

**Khi build** (`mvn clean verify`): không có dòng `[WARNING]` hay `[ERROR]` của Maven; javac code chính không cảnh báo.

| Mã | Mức | Nguồn | Nội dung | Đề xuất | Công |
|---|---|---|---|---|---|
| W1 | Thấp | javac (test) | `AttendanceServiceTest.java: Some input files use unchecked or unsafe operations` | Xem C3 | 0,2 h |
| W2 | Thấp | JVM | `sun.misc.Unsafe::objectFieldOffset … called by lombok.permit.Permit` (4 dòng WARNING) | Máy dev chạy JDK 24.0.2 trong khi dự án nhắm Java 21. Chạy bằng JDK 21, hoặc chờ Lombok bản mới (nâng phiên bản cần duyệt) | 0 h |
| W3 | Thấp | JVM | `A Java agent has been loaded dynamically (byte-buddy-agent)` – Mockito tự gắn agent | Thêm `-javaagent` của Mockito vào `argLine` của surefire (sửa `pom.xml`, cần duyệt) | 0,5 h |
| W4 | Thấp | Spring Security (3 lần, mỗi context) | `Global AuthenticationManager configured with an AuthenticationProvider bean. UserDetailsService beans will not be used…` | Cấu hình đúng chủ ý (`PhoneAuthenticationProvider`). Tắt bằng `logging.level…InitializeUserDetailsBeanManagerConfigurer=ERROR`, hoặc không khai báo `UserDetailsService` là bean | 0,3 h |
| W5 | Thấp | Hibernate (6 dòng) | `HHH000247: ErrorCode: 2627/2601/547` kèm câu lỗi SQL Server (trùng `uq_users_phone`, `uq_faculties_code`, `ux_semesters_current`, CHECK `ck_semesters_dates`) | Do test cố ý gây vi phạm ràng buộc, đúng mong đợi. Xem S4 về số điện thoại trong log | 0 h |
| W6 | Thấp | Spring MVC (3 dòng) | `HttpRequestMethodNotSupportedException: Request method 'POST'/'PUT'/'DELETE' is not supported` | Do `AuditLogIntegrationTest.noWriteRoutes` cố ý gửi, đúng mong đợi | 0 h |

**Khi khởi động** (`mvn spring-boot:run`, DB localhost): 0 ERROR, 1 WARN (trùng W4). Trong cả lượt duyệt 46 trang với
5 vai trò, log không có thêm WARN hay ERROR nào.

---

## Phần không kiểm được

- Chưa đo số truy vấn SQL thật mỗi trang (cần bật log SQL hoặc Hibernate statistics, tức là sửa cấu hình). Đề xuất làm
  cùng P1.
- Chưa thử với dữ liệu lớn (hàng nghìn lớp, hàng trăm nghìn dòng `audit_logs`); các nhận định về chịu tải ở phần 2 dựa trên đọc code.
- Chưa kiểm trên Safari, Firefox (NFR-07) và trên điện thoại thật; ảnh 360px là giả lập kích thước trong Chromium.
- Chưa xác minh S1 trên máy SQL Server (không đăng nhập DB bằng công cụ dòng lệnh, theo `CLAUDE.md`).
