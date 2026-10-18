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
34. Giai đoạn 4a: danh mục (khoa, bộ môn, ngành, lớp hành chính, môn học, học kỳ, phòng học) chỉ có thêm, sửa, xem; không có chức năng xóa vì SRS FR-ORG chỉ yêu cầu thêm, sửa và các bảng không có cờ trạng thái để ẩn. Riêng đầu điểm được xóa khi chưa có điểm.
35. Giai đoạn 4a: tổng trọng số đầu điểm khác 100% chỉ hiện cảnh báo ở trang môn học; giai đoạn Điểm số sẽ chặn nhập và công bố điểm khi tổng khác 100%.
36. Giai đoạn 4a: mã danh mục được bỏ khoảng trắng hai đầu và viết hoa trước khi lưu và kiểm tra trùng (giống mã SV/GV ở giả định 24). Tên đầu điểm chỉ bỏ khoảng trắng hai đầu, kiểm tra trùng trong môn không phân biệt hoa thường.
37. Giai đoạn 4a: giảng viên chủ nhiệm của lớp nhập bằng mã giảng viên (như cách nhập phụ huynh ở giả định 25), để trống là chưa có.
38. Giai đoạn 4a: niên khóa của lớp nhận số từ 2000 đến 2100 (script không có CHECK); số tín chỉ từ 1 đến 20; sức chứa phòng để trống hoặc từ 1 đến 32767 (SMALLINT).
39. Giai đoạn 4a: điểm tối đa của đầu điểm phải lớn hơn 0 và không quá 10, tối đa 2 chữ số thập phân; script không có CHECK cho cột này nên chỉ kiểm tra ở service. Trọng số lớn hơn 0, không quá 100, tối đa 2 chữ số thập phân (ck_gc_weight, DECIMAL(5,2)).
40. Giai đoạn 4a: trưởng bộ môn mở môn ngoài bộ môn mình, hoặc môn không tồn tại, bằng URL trực tiếp đều nhận 403 (không phân biệt để tránh dò id); quản trị viên nhận 404 với môn không tồn tại. Gửi bộ môn ngoài phạm vi trong form thêm/sửa môn cũng nhận 403.
41. Giai đoạn 4a: "Đặt làm học kỳ hiện tại" với học kỳ đã là hiện tại thì không làm gì và không ghi audit. Học kỳ mới tạo luôn chưa là học kỳ hiện tại; cờ chỉ đổi qua thao tác này.
42. Giai đoạn 4b: mã lớp học phần được bỏ khoảng trắng hai đầu và viết hoa (như giả định 36). Script không có UNIQUE cho class_sections.code nên chỉ chặn trùng ở service; tạo/sửa lớp chạy isolation SERIALIZABLE, nếu SQL Server chọn một bên làm deadlock victim thì bên đó nhận "vui lòng thử lại".
43. Giai đoạn 4b: mã đề xuất theo mẫu MÃMÔN-MÃKỲ-NN với NN là số nhỏ nhất từ 01 chưa dùng cho cùng tiền tố; để trống mã khi lưu thì tự đề xuất. Mã cũ không theo mẫu (ví dụ PRN211_FA26_01) giữ nguyên.
44. Giai đoạn 4b: lớp học phần đã có dòng ghi danh (bất kỳ trạng thái) thì không đổi được môn học và học kỳ; giảng viên (cùng bộ môn với môn), sĩ số, trạng thái vẫn sửa được. Đổi trạng thái sang CLOSED/CANCELLED không tự gỡ sinh viên.
45. Giai đoạn 4b: ghi danh lại sinh viên DROPPED đổi chính dòng cũ về ENROLLED và cập nhật enrolled_at về thời điểm ghi danh lại. Sinh viên có dòng COMPLETED không ghi danh lại được.
46. Giai đoạn 4b: gỡ sinh viên (ENROLLED → DROPPED) làm được ở mọi trạng thái lớp; chỉ ghi danh mới cần lớp OPEN.
47. Giai đoạn 4b: ô tìm sinh viên để ghi danh chỉ hiện tối đa 20 kết quả (sinh viên đang học, chưa có trong lớp hoặc đã bị gỡ); gõ thêm mã hoặc tên để thu hẹp.
48. Giai đoạn 4b: file mẫu nhập ghi danh chỉ có dòng tiêu đề "Mã SV" và sheet hướng dẫn, không có dòng ví dụ. Mã trong file được trim, viết hoa; vượt sĩ số là lỗi cả file. Phần đọc file (.xlsx, 2 MB, 500 dòng, tiêu đề) dùng chung với nhập người dùng qua ExcelImportReader.
49. Giai đoạn 4b: trưởng bộ môn xem, thêm, sửa lớp học phần của môn thuộc bộ môn mình và xem danh sách sinh viên; mọi thao tác ghi danh chỉ ADMIN (SRS FR-ORG-06). Lớp ngoài phạm vi hoặc id không tồn tại đều trả 403 với trưởng bộ môn (như giả định 40).
50. Giai đoạn 5a: hai khoảng giờ bị coi là trùng khi chồng nhau (bắt đầu A < kết thúc B và bắt đầu B < kết thúc A); buổi kết thúc đúng lúc buổi kia bắt đầu không tính là trùng. Buổi CANCELLED và mọi buổi, lịch thi của lớp học phần CANCELLED không tính.
51. Giai đoạn 5a: uq_sess_cs_slot không lọc trạng thái nên không tạo được buổi mới bắt đầu đúng ngày giờ của một buổi đã hủy trong cùng lớp; hệ thống báo trước thay vì để lỗi database.
52. Giai đoạn 5a: buổi học kiểm tra trùng phòng (buổi học và lịch thi), trùng giảng viên (chỉ buổi dạy, không tính lịch thi), trùng sinh viên đang ENROLLED (buổi học và lịch thi của lớp khác) và trùng lịch thi của chính lớp. Lịch thi kiểm tra trùng phòng, trùng lịch thi và buổi học của chính lớp, trùng lịch thi và buổi học của sinh viên chung (đã duyệt).
53. Giai đoạn 5a: buổi chỉ đổi phòng (giữ nguyên ngày giờ) chỉ kiểm tra trùng phòng; đổi ngày giờ thì kiểm tra đầy đủ.
54. Giai đoạn 5a: buổi thêm lẻ có trạng thái MAKEUP, buổi sinh từ lịch tuần là SCHEDULED; session_no là số lớn nhất của lớp cộng 1 (tính cả buổi đã hủy). Buổi CANCELLED không mở lại được và không sửa được; buổi đã có điểm danh chỉ đổi được phòng và ghi chú, không hủy được. Lớp học phần CANCELLED không thêm được buổi hay lịch thi.
55. Giai đoạn 5a: lịch tuần không đủ ngày tới ngày kết thúc học kỳ thì từ chối cả lô và báo số buổi tối đa xếp được (đã duyệt); tối đa 200 buổi mỗi lần; ngày bắt đầu phải nằm trong học kỳ.
56. Giai đoạn 5a: lịch thi không bắt buộc nằm trong học kỳ (thi lại có thể sau ngày kết thúc) và được xóa hẳn vì không bảng nào tham chiếu tới exams.
57. Giai đoạn 5a: ghi lịch (buổi học, lịch tuần, lịch thi) chạy SERIALIZABLE và khóa dòng lớp học phần; nếu SQL Server chọn một bên làm deadlock victim thì bên đó nhận "vui lòng thử lại".
58. Giai đoạn 5a: ScheduleChangeNotifier được gọi trong transaction đổi lịch (hủy buổi, đổi phòng, đổi giờ, thêm buổi bù); cài đặt hiện tại không làm gì, cài đặt thật ở giai đoạn Thông báo cần gửi sau khi transaction commit.
59. Giai đoạn 5a: tuần bắt đầu từ thứ Hai; mặc định là học kỳ hiện tại (không có thì học kỳ mới nhất) và tuần chứa hôm nay, hôm nay nằm ngoài học kỳ thì tuần đầu tiên của học kỳ. Phụ huynh chưa liên kết con nào thì thấy trang trống kèm hướng dẫn; /parent/schedule mở con đầu tiên đã liên kết.
