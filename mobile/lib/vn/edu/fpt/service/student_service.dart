import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/exam_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/page_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_week_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';

/// API dữ liệu học tập của SV và PH (docs/API.md mục "Sinh viên ... và phụ
/// huynh"). Lỗi ném [ApiErrorModel].
class StudentService {
  final ApiClient _apiClient;

  StudentService(this._apiClient);

  /// Gốc đường dẫn: SV xem của mình ([studentId] null) dùng `/student`,
  /// PH xem con dùng `/parent/children/{studentId}`.
  static String rootPath(int? studentId) =>
      studentId == null ? '/student' : '/parent/children/$studentId';

  /// Các con đã liên kết của phụ huynh đang đăng nhập.
  Future<List<ChildModel>> fetchChildren() async {
    final data = await _apiClient.get('/parent/children');
    return (data as List<dynamic>)
        .map((child) => ChildModel.fromJson(child as Map<String, dynamic>))
        .toList();
  }

  /// Lịch tuần chứa [date] (`yyyy-MM-dd`) của học kỳ hiện tại; [date] null
  /// là tuần hiện tại (ngoài học kỳ thì tuần đầu học kỳ).
  Future<InSemesterModel<ScheduleWeekModel>> fetchWeek(
    int? studentId, {
    String? date,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/schedule/week',
      query: {'date': ?date},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (week) => ScheduleWeekModel.fromJson(week as Map<String, dynamic>),
    );
  }

  /// Mọi buổi học của học kỳ hiện tại theo ngày giờ, [page] đếm từ 0.
  Future<InSemesterModel<PageModel<ScheduleItemModel>>> fetchSessions(
    int? studentId, {
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/schedule/sessions',
      query: {'page': page},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (items) => PageModel.fromJson(
        items as Map<String, dynamic>,
        ScheduleItemModel.fromJson,
      ),
    );
  }

  /// Lịch thi của học kỳ hiện tại theo ngày giờ, [page] đếm từ 0.
  Future<InSemesterModel<PageModel<ExamModel>>> fetchExams(
    int? studentId, {
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/exams',
      query: {'page': page},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (items) =>
          PageModel.fromJson(items as Map<String, dynamic>, ExamModel.fromJson),
    );
  }
}
