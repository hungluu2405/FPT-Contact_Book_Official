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

Các API dữ liệu của SV, PH (giai đoạn 9c) và GV (9d) sẽ được bổ sung vào bảng này.

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

## Phân trang
Danh sách trả dạng `{"items": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}`. Tham số `page` (đếm từ 0) và `size`.
