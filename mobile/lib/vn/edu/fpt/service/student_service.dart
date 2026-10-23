import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/grade_row_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
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

  /// Gốc đường dẫn lịch học, lịch thi: GV xem lịch dạy của mình ([teacher]),
  /// HS xem của mình ([studentId] null) hoặc PH xem của con.
  static String scheduleRoot(int? studentId, {bool teacher = false}) =>
      teacher ? '/teacher' : rootPath(studentId);

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
    bool teacher = false,
  }) async {
    final data = await _apiClient.get(
      '${scheduleRoot(studentId, teacher: teacher)}/schedule/week',
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
    bool teacher = false,
  }) async {
    final data = await _apiClient.get(
      '${scheduleRoot(studentId, teacher: teacher)}/schedule/sessions',
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

  /// Mọi học kỳ cho ô chọn học kỳ, mới nhất trước.
  Future<List<SemesterOptionModel>> fetchSemesters() async {
    final data = await _apiClient.get('/semesters');
    return (data as List<dynamic>)
        .map(
          (item) => SemesterOptionModel.fromJson(item as Map<String, dynamic>),
        )
        .toList();
  }

  /// Điểm các môn của [semesterId] (null: học kỳ hiện tại); chỉ đầu điểm
  /// đã công bố có điểm.
  Future<InSemesterModel<List<GradeRowModel>>> fetchGrades(
    int? studentId, {
    int? semesterId,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/grades',
      query: {'semesterId': ?semesterId},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (rows) => (rows as List<dynamic>)
          .map((row) => GradeRowModel.fromJson(row as Map<String, dynamic>))
          .toList(),
    );
  }

  /// Chuyên cần các môn của [semesterId] (null: học kỳ hiện tại).
  Future<AttendanceOverviewModel> fetchAttendance(
    int? studentId, {
    int? semesterId,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/attendance',
      query: {'semesterId': ?semesterId},
    );
    return AttendanceOverviewModel.fromJson(data as Map<String, dynamic>);
  }

  /// Lịch sử điểm danh của lớp môn học [sectionId], [page] đếm từ 0.
  Future<AttendanceHistoryModel> fetchAttendanceHistory(
    int? studentId,
    int sectionId, {
    int page = 0,
  }) async {
    final data = await _apiClient.get(
      '${rootPath(studentId)}/attendance/$sectionId',
      query: {'page': page},
    );
    return AttendanceHistoryModel.fromJson(data as Map<String, dynamic>);
  }

  /// Lịch thi của học kỳ hiện tại theo ngày giờ, [page] đếm từ 0.
  Future<InSemesterModel<PageModel<ExamModel>>> fetchExams(
    int? studentId, {
    int page = 0,
    bool teacher = false,
  }) async {
    final data = await _apiClient.get(
      '${scheduleRoot(studentId, teacher: teacher)}/exams',
      query: {'page': page},
    );
    return InSemesterModel.fromJson(
      data as Map<String, dynamic>,
      (items) =>
          PageModel.fromJson(items as Map<String, dynamic>, ExamModel.fromJson),
    );
  }
}
