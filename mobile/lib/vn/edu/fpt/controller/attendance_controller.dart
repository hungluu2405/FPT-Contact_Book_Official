import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/attendance_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

/// Điểm danh của HS (hoặc con của PH): chuyên cần các môn theo học kỳ và
/// lịch sử từng buổi của một môn (tải theo trang). Dùng chung cho màn hình
/// danh sách và màn hình chi tiết.
class AttendanceController extends ChangeNotifier {
  static const String notFound = 'NOT_FOUND';

  final StudentService _studentService;

  int? _studentId;
  List<SemesterOptionModel> _semesters = const [];
  int? _selectedSemesterId;
  AttendanceOverviewModel? _overview;
  bool _loading = false;
  ApiErrorModel? _error;

  int? _sectionId;
  AttendanceSectionModel? _section;
  final List<AttendanceSessionModel> _sessions = [];
  int _sessionPage = -1;
  bool _sessionsHasMore = true;
  bool _loadingSessions = false;
  ApiErrorModel? _sessionsError;

  /// Tăng mỗi lần đổi học kỳ hoặc đổi môn để bỏ phản hồi về muộn.
  int _overviewGeneration = 0;
  int _sessionGeneration = 0;

  AttendanceController(this._studentService);

  List<SemesterOptionModel> get semesters =>
      List<SemesterOptionModel>.unmodifiable(_semesters);

  int? get selectedSemesterId => _selectedSemesterId;

  AttendanceOverviewModel? get overview => _overview;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  bool get hasNoSemester => _error?.code == notFound;

  /// Số môn vượt ngưỡng vắng trong học kỳ đang xem.
  int get warningCount =>
      _overview?.sections.where((row) => row.summary.warning).length ?? 0;

  /// Môn đang xem chi tiết (kèm chuyên cần), có sau khi tải trang đầu.
  AttendanceSectionModel? get section => _section;

  List<AttendanceSessionModel> get sessions =>
      List<AttendanceSessionModel>.unmodifiable(_sessions);

  bool get sessionsHasMore => _sessionsHasMore;

  bool get isLoadingSessions => _loadingSessions;

  ApiErrorModel? get sessionsError => _sessionsError;

  /// Mở điểm danh của [studentId] (null: HS xem của mình) ở học kỳ hiện tại.
  Future<void> open(int? studentId) async {
    _studentId = studentId;
    _semesters = const [];
    _selectedSemesterId = null;
    _overview = null;
    await Future.wait([_loadSemesters(), _loadOverview(null)]);
  }

  Future<void> selectSemester(int semesterId) => _loadOverview(semesterId);

  Future<void> reload() => _loadOverview(_selectedSemesterId);

  /// Mở lịch sử điểm danh của lớp môn học [sectionId] từ trang đầu.
  Future<void> openSection(int sectionId) {
    _sessionGeneration++;
    _sectionId = sectionId;
    _section = null;
    _sessions.clear();
    _sessionPage = -1;
    _sessionsHasMore = true;
    _loadingSessions = false;
    _sessionsError = null;
    return loadMoreSessions();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMoreSessions() async {
    final sectionId = _sectionId;
    if (sectionId == null || _loadingSessions || !_sessionsHasMore) {
      return;
    }
    final generation = _sessionGeneration;
    _loadingSessions = true;
    _sessionsError = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchAttendanceHistory(
        _studentId,
        sectionId,
        page: _sessionPage + 1,
      );
      if (generation != _sessionGeneration) {
        return;
      }
      _section = result.section;
      _sessions.addAll(result.sessions.items);
      _sessionPage = result.sessions.page;
      _sessionsHasMore = result.sessions.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _sessionGeneration) {
        _sessionsError = error;
      }
    } finally {
      if (generation == _sessionGeneration) {
        _loadingSessions = false;
        notifyListeners();
      }
    }
  }

  Future<void> _loadSemesters() async {
    try {
      _semesters = await _studentService.fetchSemesters();
      notifyListeners();
    } on ApiErrorModel {
      // Không có ô chọn học kỳ thì vẫn xem được học kỳ hiện tại.
    }
  }

  Future<void> _loadOverview(int? semesterId) async {
    final generation = ++_overviewGeneration;
    if (semesterId != null) {
      _selectedSemesterId = semesterId;
    }
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchAttendance(
        _studentId,
        semesterId: semesterId,
      );
      if (generation != _overviewGeneration) {
        return;
      }
      _overview = result;
      _selectedSemesterId = result.semester.id;
    } on ApiErrorModel catch (error) {
      if (generation == _overviewGeneration) {
        _overview = null;
        _error = error;
      }
    } finally {
      if (generation == _overviewGeneration) {
        _loading = false;
        notifyListeners();
      }
    }
  }
}
