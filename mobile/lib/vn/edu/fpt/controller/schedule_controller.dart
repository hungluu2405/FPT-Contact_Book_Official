import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_week_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_clock.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Lịch học của SV (hoặc con của PH): lịch theo tuần với ngày đang chọn và
/// danh sách mọi buổi của học kỳ. Dùng chung cho màn hình lịch học và danh
/// sách buổi học.
class ScheduleController extends ChangeNotifier {
  static const String notFound = 'NOT_FOUND';

  final StudentService _studentService;
  final AppClock _clock;

  int? _studentId;
  SemesterModel? _semester;
  ScheduleWeekModel? _week;
  String? _selectedDate;
  bool _loading = false;
  ApiErrorModel? _error;

  final List<ScheduleItemModel> _sessions = [];
  int _sessionPage = -1;
  bool _sessionsHasMore = true;
  bool _loadingSessions = false;
  ApiErrorModel? _sessionsError;

  /// Tăng mỗi lần đổi tuần hoặc đổi người xem để bỏ phản hồi về muộn.
  int _weekGeneration = 0;
  int _sessionGeneration = 0;

  ScheduleController(this._studentService, {this._clock = vietnamNow});

  int? get studentId => _studentId;

  SemesterModel? get semester => _semester;

  ScheduleWeekModel? get week => _week;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  /// Chưa có học kỳ nào: hiện như trạng thái trống, không phải lỗi.
  bool get hasNoSemester => _error?.code == notFound;

  String? get selectedDate => _selectedDate;

  ScheduleDayModel? get selectedDay {
    for (final day in _week?.days ?? const <ScheduleDayModel>[]) {
      if (day.date == _selectedDate) {
        return day;
      }
    }
    return null;
  }

  /// Tuần đang xem có chứa hôm nay.
  bool get isCurrentWeek => _week?.todayDay != null;

  List<ScheduleItemModel> get sessions =>
      List<ScheduleItemModel>.unmodifiable(_sessions);

  bool get sessionsHasMore => _sessionsHasMore;

  bool get isLoadingSessions => _loadingSessions;

  ApiErrorModel? get sessionsError => _sessionsError;

  bool get hasNoSemesterForSessions => _sessionsError?.code == notFound;

  /// Mở lịch của [studentId] (null: SV xem của mình) ở tuần hiện tại.
  Future<void> open(int? studentId) {
    _studentId = studentId;
    _semester = null;
    _week = null;
    _selectedDate = null;
    _resetSessions();
    return _loadWeek(null);
  }

  /// Tải lại tuần đang xem (kéo xuống để tải lại, nút thử lại).
  Future<void> reload() => _loadWeek(_week?.weekStart);

  Future<void> previousWeek() => _loadWeek(_week?.previous);

  Future<void> nextWeek() => _loadWeek(_week?.next);

  /// Về tuần hiện tại và chọn hôm nay.
  Future<void> goToday() => _loadWeek(null);

  void selectDate(String date) {
    if (_selectedDate == date) {
      return;
    }
    _selectedDate = date;
    notifyListeners();
  }

  /// Buổi đang học: thuộc hôm nay (theo server), chưa hủy và giờ hiện tại
  /// nằm trong [giờ bắt đầu, giờ kết thúc).
  bool isOngoing(ScheduleDayModel day, ScheduleItemModel item) {
    if (!day.today || item.isCancelled) {
      return false;
    }
    final start = minutesOfDay(item.startTime);
    final end = minutesOfDay(item.endTime);
    if (start == null || end == null) {
      return false;
    }
    final now = _clock();
    final minutes = now.hour * 60 + now.minute;
    return minutes >= start && minutes < end;
  }

  Future<void> _loadWeek(String? date) async {
    final generation = ++_weekGeneration;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchWeek(_studentId, date: date);
      if (generation != _weekGeneration) {
        return;
      }
      final sameWeek = _week?.weekStart == result.data.weekStart;
      _semester = result.semester;
      _week = result.data;
      // Tải lại cùng tuần thì giữ ngày đang chọn; đổi tuần thì chọn lại.
      if (!sameWeek || selectedDay == null) {
        _selectedDate = _defaultDate(result.data);
      }
    } on ApiErrorModel catch (error) {
      if (generation == _weekGeneration) {
        _error = error;
      }
    } finally {
      if (generation == _weekGeneration) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  /// Hôm nay nếu thuộc tuần; không thì ngày đầu tiên có buổi học; không có
  /// buổi nào thì thứ Hai.
  String _defaultDate(ScheduleWeekModel week) {
    final today = week.todayDay;
    if (today != null) {
      return today.date;
    }
    for (final day in week.days) {
      if (day.items.isNotEmpty) {
        return day.date;
      }
    }
    return week.weekStart;
  }

  /// Tải trang đầu của danh sách buổi học (mở màn hình, kéo để tải lại).
  Future<void> reloadSessions() {
    _resetSessions();
    notifyListeners();
    return loadMoreSessions();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMoreSessions() async {
    if (_loadingSessions || !_sessionsHasMore) {
      return;
    }
    final generation = _sessionGeneration;
    _loadingSessions = true;
    _sessionsError = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchSessions(
        _studentId,
        page: _sessionPage + 1,
      );
      if (generation != _sessionGeneration) {
        return;
      }
      _semester ??= result.semester;
      _sessions.addAll(result.data.items);
      _sessionPage = result.data.page;
      _sessionsHasMore = result.data.hasMore;
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

  void _resetSessions() {
    _sessionGeneration++;
    _sessions.clear();
    _sessionPage = -1;
    _sessionsHasMore = true;
    _loadingSessions = false;
    _sessionsError = null;
  }
}
