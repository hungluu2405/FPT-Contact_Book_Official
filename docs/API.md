# Hợp đồng REST API cho mobile (v1)

Tài liệu chi tiết, luôn khớp với code: `GET /v3/api-docs` (OpenAPI JSON) và `/swagger-ui.html` khi ứng dụng đang chạy. File này tóm tắt quy ước chung; khi hai nguồn khác nhau thì OpenAPI đúng.

- Gốc: `http://<máy chủ>:8080/api/v1`. Máy ảo Android gọi backend chạy trên máy dev qua `http://10.0.2.2:8080`.
- Dữ liệu: cùng database SQL Server `FptContactBook` với web, qua các service dùng chung. Mobile không có database riêng.
- JSON UTF-8. Ngày giờ trả theo ISO-8601, giờ Asia/Ho_Chi_Minh (ví dụ `2026-10-20T08:30:00`); mobile tự hiển thị `dd/MM/yyyy`, `HH:mm`.

## Xác thực
1. `POST /auth/login` với body `{"phone": "0900000004", "password": "..."}`. Số điện thoại nhận mọi dạng như web (`+84...`, có khoảng trắng, dấu chấm, gạch ngang).
2. Gửi access token ở mọi request: `Authorization: Bearer <accessToken>`.
3. Access token sống 15 phút (`expiresIn` tính bằng giây). Khi nhận 401 `UNAUTHORIZED`, gọi `POST /auth/refresh` với `{"refreshToken": "..."}` để lấy cặp token mới. Refresh cũng 401 thì về màn hình đăng nhập.
4. Refresh token sống 14 ngày và hết hiệu lực khi đổi hoặc bị reset mật khẩu.
5. `user.mustChangePassword = true`: chỉ gọi được `/auth/**`, `GET /me`, `POST /me/password`. API khác trả 403 `PASSWORD_CHANGE_REQUIRED`.
6. Đăng xuất: gọi `POST /auth/logout` (204) rồi xóa token đã lưu. Server không lưu token.

Phản hồi đăng nhập, làm mới và đổi mật khẩu:
```json
{
  "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", "expiresIn": 900,
  "user": {"id": 4, "phone": "0900000004", "fullName": "...", "roles": ["STUDENT"], "mustChangePassword": false}
}
```

## Endpoint
| Method | Đường dẫn | Cần token | Mô tả |
|---|---|---|---|
| POST | `/auth/login` | Không | Đăng nhập |
| POST | `/auth/refresh` | Không | Đổi refresh token lấy cặp token mới |
| POST | `/auth/logout` | Có | 204 |
| GET | `/me` | Có | `{id, phone, fullName, roles, mustChangePassword}` |
| POST | `/me/password` | Có | `{currentPassword, newPassword, confirmPassword}`, trả cặp token mới |

### Dùng chung (mọi vai trò)
| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/me/profile` | Hồ sơ: `{phone, fullName, email, gender, dateOfBirth, address, hasAvatar, lastLoginAt, roles[{roleCode, departmentCode, departmentName}]}` |
| GET | `/me/avatar` | Ảnh đại diện (JPEG/PNG, cache 1 giờ); chưa có ảnh: 404 `NOT_FOUND` |
| GET | `/semesters` | Học kỳ cho ô chọn: `[{id, code, name, groupId}]` |
| GET | `/notifications?page=` | Thông báo đã nhận (phân trang): `{id, title, senderName, priority, createdAt, read}` |
| GET | `/notifications/unread-count` | `{count}` |
| GET | `/notifications/{id}` | `{id, title, content, priority, targetLabel, senderName, createdAt, files[{id, fileName, sizeBytes}]}`; lần đầu mở thì đánh dấu đã đọc |
| POST | `/notifications/read-all` | Đánh dấu tất cả đã đọc, trả `{count}` còn lại |
| GET | `/notifications/{id}/files/{fileId}` | Tải tệp đính kèm |
| GET | `/requests/{id}/files/{fileId}` | Tải tệp đính kèm của đơn |

### Sinh viên (`/student`, vai trò STUDENT) và phụ huynh (`/parent/children/{studentId}`, vai trò PARENT)
`GET /parent/children` trả các con đã liên kết `[{id, code, name}]` (`id` là `studentId`). Mọi đường dẫn dưới đây có ở cả hai gốc; phụ huynh xem con chưa liên kết bị 403 `FORBIDDEN`. `semesterId` bỏ trống: học kỳ hiện tại.

| Method | Đường dẫn con | Trả về |
|---|---|---|
| GET | `/schedule/week?semesterId=&date=yyyy-MM-dd` | `{semester, data: {weekStart, weekEnd, previous, next, days[{date, today, items[ScheduleItem]}]}}` |
| GET | `/schedule/sessions?semesterId=&page=` | `{semester, data: Page<ScheduleItem>}` |
| GET | `/exams?semesterId=&page=` | `{semester, data: Page<{id, sectionCode, subjectCode, subjectName, examType, date, startTime, endTime, roomCode, note}>}` |
| GET | `/grades?semesterId=` | `{semester, data: [{sectionCode, subjectCode, subjectName, cells[{componentName, weight, maxScore, score}], average}]}`; `score` null nếu chưa có hoặc chưa công bố |
| GET | `/attendance?semesterId=` | `{semester, thresholdPercent, sections[{sectionId, sectionCode, subjectCode, subjectName, summary}]}` |
| GET | `/attendance/{sectionId}?page=` | `{summary, thresholdPercent, sessions: Page<{sessionNo, date, startTime, endTime, roomCode, sessionStatus, status, note}>}` |
| GET | `/requests?page=` | `Page<{id, type, title, studentCode, studentName, requesterName, sectionCode, fromDate, toDate, status, createdAt}>` (mọi loại đơn) |
| GET | `/requests/{id}` | Chi tiết đơn (RequestDetail): `{..., files[], reviews[], canCancel, canReview}` |
| GET | `/requests/leave-options` | `{studentId, studentCode, studentName, sections[{id, code, name}]}` |
| POST | `/requests/leave` | `multipart/form-data`: `fromDate`, `toDate` (yyyy-MM-dd hoặc dd/MM/yyyy), `reason`, tùy chọn `title`, `sectionId`, `files` (nhiều tệp, mỗi tệp tối đa 10MB). 201 + chi tiết đơn |
| POST | `/requests/{id}/cancel` | Hủy đơn đang chờ duyệt, trả chi tiết đơn; đơn không còn chờ duyệt: 400 `BUSINESS_ERROR` |

`ScheduleItem`: `{date, startTime, endTime, subjectCode, subjectName, sectionCode, roomCode, teacherName, status, note}`. `summary` (chuyên cần): `{present, absentExcused, absentUnexcused, totalSessions, attendanceRate, absentRate, warning}` (tỷ lệ là phần trăm, `warning` = vượt ngưỡng vắng).

### Giảng viên (`/teacher`, vai trò TEACHER hoặc HEAD_OF_DEPARTMENT)
Chỉ lớp mình dạy và đơn mình phụ trách duyệt; lớp hoặc đơn khác: 403 `FORBIDDEN`. Trưởng bộ môn dùng như giảng viên; quản lý cả bộ môn, nhập điểm, gửi thông báo làm trên web.

| Method | Đường dẫn | Trả về |
|---|---|---|
| GET | `/teacher/schedule/week?semesterId=&date=` | Lịch dạy một tuần, cùng dạng với sinh viên |
| GET | `/teacher/schedule/sessions?semesterId=&page=` | `{semester, data: Page<ScheduleItem>}` |
| GET | `/teacher/exams?semesterId=&page=` | Lịch thi các lớp mình dạy |
| GET | `/teacher/sections?semesterId=&page=` | `{semester, data: Page<{id, code, subjectCode, subjectName, semesterCode, status}>}` |
| GET | `/teacher/sections/{id}/attendance?sessionPage=&studentPage=` | `{section, sessions: Page<{id, sessionNo, date, startTime, endTime, roomCode, status, note, hasAttendance}>, students: Page<{studentId, studentCode, fullName, summary}>, totalSessions, thresholdPercent}` |
| GET | `/teacher/sections/{id}/sessions/{sessionId}/attendance` | Bảng điểm danh: `{sectionId, sectionCode, subjectCode, subjectName, sessionId, sessionNo, date, startTime, endTime, roomCode, marked, editableUntil, locked, reasonRequired, rows[{studentId, studentCode, fullName, status, note, approvedLeave}]}`. `editableUntil`: hạn điểm danh, sửa của giáo viên; `locked = true` là đã quá hạn, chỉ xem. Học sinh `approvedLeave = true` nên chọn sẵn `ABSENT_EXCUSED` |
| POST | `/teacher/sections/{id}/sessions/{sessionId}/attendance` | Body `{"statuses": {"<studentId>": "PRESENT", ...}, "notes": {"<studentId>": "Đi muộn 10 phút"}, "reason": "..."}`: `statuses` phải đủ mọi học sinh trong `rows`; `notes` tùy chọn (tối đa 255 ký tự, chuỗi rỗng là xóa, học sinh không có trong `notes` giữ nguyên ghi chú); `reason` (tối đa 255 ký tự) bắt buộc khi đổi trạng thái của buổi đã điểm danh, chỉ ghi vào nhật ký. Trả `{firstTime, changed, sheet}` |
| GET | `/teacher/requests?status=&page=` | Đơn cần duyệt: `status` bỏ trống là `PENDING`; `APPROVED`, `REJECTED`, `CANCELLED` hoặc `ALL` |
| GET | `/teacher/requests/{id}` | Chi tiết đơn (`canReview` cho biết còn duyệt được không) |
| POST | `/teacher/requests/{id}/review` | Body `{"decision": "APPROVED" hoặc "REJECTED", "comment": "..."}`: từ chối bắt buộc có `comment` (tối đa 500 ký tự). Trả `{decision, excusedSessions, request}`; `excusedSessions` là số buổi vắng không phép được chuyển thành vắng có phép |

**Hạn 24 giờ:** giáo viên điểm danh lần đầu được tới giờ kết thúc buổi + 24 giờ, sửa được tới lần điểm danh đầu + 24 giờ; quá hạn thì lưu bị 400 `BUSINESS_ERROR` ("Đã hết hạn điểm danh, chỉnh sửa..."), chỉ quản trị viên sửa được trên web.

Lỗi thường gặp khi điểm danh: buổi chưa diễn ra, buổi hoặc lớp đã hủy, thiếu sinh viên, thiếu lý do khi sửa, người khác vừa lưu cùng lúc (tải lại bảng rồi thử lại): đều là 400 `BUSINESS_ERROR` kèm câu tiếng Việt. Trạng thái không thuộc danh sách enum: 400 `VALIDATION_FAILED`. Buổi không thuộc lớp: 404 `NOT_FOUND`.

## Giá trị enum
Trả dạng mã; mobile hiển thị chữ tiếng Việt như web:

| Enum | Mã → chữ |
|---|---|
| `status` của buổi học, `sessionStatus` | `SCHEDULED` Đã lên lịch, `DONE` Đã học, `CANCELLED` Đã hủy, `MAKEUP` Học bù |
| `status` điểm danh | `PRESENT` Có mặt (đi muộn cũng là có mặt, ghi trong `note`), `ABSENT_EXCUSED` Vắng có phép, `ABSENT_UNEXCUSED` Vắng không phép; null là chưa điểm danh. Không còn `LATE` |
| `examType` | `MIDTERM` Giữa kỳ, `FINAL` Cuối kỳ, `RETAKE` Thi lại |
| `type` của đơn | `LEAVE` Xin nghỉ học, `TRANSCRIPT` Xin bảng điểm, `CERTIFICATE` Xác nhận sinh viên, `OTHER` Đơn khác |
| `status` của đơn | `PENDING` Chờ duyệt, `APPROVED` Đã duyệt, `REJECTED` Từ chối, `CANCELLED` Đã hủy |
| `decision` duyệt đơn | `APPROVED` Duyệt, `REJECTED` Từ chối |
| `priority` thông báo | `NORMAL` Thường, `IMPORTANT` Quan trọng, `URGENT` Khẩn |
| `gender` | `MALE`, `FEMALE`, `OTHER` |

Ngày `yyyy-MM-dd`, giờ `HH:mm:ss`, thời điểm `yyyy-MM-ddTHH:mm:ss`; số thập phân (điểm, trọng số) là số JSON.

## Lỗi
```json
{"status": 400, "code": "VALIDATION_FAILED", "message": "Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại",
 "fieldErrors": [{"field": "phone", "message": "Vui lòng nhập số điện thoại"}], "timestamp": "2026-10-20T08:30:00"}
```
Mobile xử lý theo `code`; `message` là câu tiếng Việt để hiện cho người dùng.

| code | HTTP | Khi nào |
|---|---|---|
| `BAD_CREDENTIALS` | 401 | Sai số điện thoại hoặc mật khẩu (một thông báo chung) |
| `ACCOUNT_LOCKED_TEMPORARILY` | 429 | Sai 5 lần, khóa tạm 15 phút |
| `ACCOUNT_DISABLED` | 403 | Tài khoản bị khóa (khi đăng nhập hoặc làm mới token) |
| `NO_ROLE` | 403 | Tài khoản chưa được gán vai trò |
| `UNAUTHORIZED` | 401 | Thiếu token, token sai, hết hạn hoặc tài khoản không còn dùng được |
| `FORBIDDEN` | 403 | Không có quyền hoặc ngoài phạm vi dữ liệu |
| `PASSWORD_CHANGE_REQUIRED` | 403 | Phải đổi mật khẩu trước |
| `VALIDATION_FAILED` | 400 | Thiếu hoặc sai dữ liệu; chi tiết từng ô trong `fieldErrors` |
| `BUSINESS_ERROR` | 400 | Vi phạm quy tắc nghiệp vụ (ví dụ mật khẩu hiện tại không đúng) |
| `NOT_FOUND` | 404 | Không tìm thấy dữ liệu |
| `CONFLICT` | 409 | Trùng hoặc vi phạm ràng buộc dữ liệu |
| `INTERNAL_ERROR` | 500 | Lỗi hệ thống |

Tệp vượt giới hạn tải lên (10MB mỗi tệp, 30MB mỗi request) bị máy chủ chặn trước khi tới API: HTTP 413, không có body ApiError.

## Phân trang
Danh sách trả dạng `{"items": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}`. Tham số `page` (đếm từ 0) và `size`.
