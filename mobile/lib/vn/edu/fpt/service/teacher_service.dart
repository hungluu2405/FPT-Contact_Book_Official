import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_sheet_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/request_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_section_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// API lớp và điểm danh của giáo viên (docs/API.md mục "Giáo viên"). Lịch
/// dạy, lịch thi dùng `StudentService` với phạm vi giáo viên. Lỗi ném
/// [ApiErrorModel].
class TeacherService {
  final ApiClient _apiClient;

  TeacherService(this._apiClient);

  /// Lớp mình dạy trong học kỳ [semesterId] (null: học kỳ hiện tại).
  Future<InSemesterModel<PageModel<TeacherSectionModel>>> fetchSections({
    int? semesterId,
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '/teacher/sections',
      query: {'semesterId': ?semesterId, 'page': page},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (items) => PageModel.fromJson(
        items as Map<String, dynamic>,
        TeacherSectionModel.fromJson,
      ),
    );
  }

  /// Chi tiết lớp: trang buổi học [sessionPage], trang học sinh [studentPage].
  Future<SectionAttendanceModel> fetchSectionAttendance(
    int sectionId, {
    int sessionPage = 0,
    int studentPage = 0,
  }) async {
    final data = await _apiClient.get(
      '/teacher/sections/$sectionId/attendance',
      query: {'sessionPage': sessionPage, 'studentPage': studentPage},
    );
    return SectionAttendanceModel.fromJson(data as Map<String, dynamic>);
  }

  /// Đơn mình phụ trách duyệt; [status] null là đơn chờ duyệt, `ALL` là mọi
  /// trạng thái.
  Future<PageModel<RequestModel>> fetchReviewRequests({
    String? status,
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '/teacher/requests',
      query: {'status': ?status, 'page': page},
    );
    return PageModel.fromJson(
      data as Map<String, dynamic>,
      RequestModel.fromJson,
    );
  }

  Future<RequestDetailModel> fetchReviewRequest(int id) async {
    final data = await _apiClient.get('/teacher/requests/$id');
    return RequestDetailModel.fromJson(data as Map<String, dynamic>);
  }

  /// Duyệt ([decision] `APPROVED`) hoặc từ chối (`REJECTED`, bắt buộc
  /// [comment]); [comment] rỗng thì không gửi.
  Future<ReviewResultModel> review(
    int id, {
    required String decision,
    String? comment,
  }) async {
    final text = comment?.trim() ?? '';
    final data = await _apiClient.post(
      '/teacher/requests/$id/review',
      data: {'decision': decision, 'comment': ?(text.isEmpty ? null : text)},
    );
    return ReviewResultModel.fromJson(data as Map<String, dynamic>);
  }

  static String _sheetPath(int sectionId, int sessionId) =>
      '/teacher/sections/$sectionId/sessions/$sessionId/attendance';

  Future<AttendanceSheetModel> fetchSheet(int sectionId, int sessionId) async {
    final data = await _apiClient.get(_sheetPath(sectionId, sessionId));
    return AttendanceSheetModel.fromJson(data as Map<String, dynamic>);
  }

  /// Lưu điểm danh: [statuses] đủ mọi học sinh (students.id → trạng thái),
  /// [notes] chỉ những ghi chú đổi (chuỗi rỗng là xóa), [reason] khi đổi
  /// trạng thái của buổi đã điểm danh.
  Future<AttendanceSaveResultModel> saveSheet(
    int sectionId,
    int sessionId, {
    required Map<int, String> statuses,
    Map<int, String> notes = const {},
    String? reason,
  }) async {
    final data = await _apiClient.post(
      _sheetPath(sectionId, sessionId),
      data: {
        'statuses': {
          for (final entry in statuses.entries) '${entry.key}': entry.value,
        },
        if (notes.isNotEmpty)
          'notes': {
            for (final entry in notes.entries) '${entry.key}': entry.value,
          },
        'reason': ?reason,
      },
    );
    return AttendanceSaveResultModel.fromJson(data as Map<String, dynamic>);
  }
}
