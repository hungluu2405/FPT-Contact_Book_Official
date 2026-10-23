import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/exam_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/app_clock.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/util/date_format.dart';

/// Lịch thi của SV (hoặc con của PH), tải theo trang.
class ExamController extends ChangeNotifier {
  static const String notFound = 'NOT_FOUND';

  final StudentService _studentService;
  final AppClock _clock;

  int? _studentId;
  SemesterModel? _semester;
  final List<ExamModel> _exams = [];
  int _page = -1;
  int _total = 0;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;

  /// Tăng mỗi lần mở lại để bỏ phản hồi về muộn.
  int _generation = 0;

  ExamController(this._studentService, {this._clock = vietnamNow});

  SemesterModel? get semester => _semester;

  List<ExamModel> get exams => List<ExamModel>.unmodifiable(_exams);

  /// Tổng số lịch thi của học kỳ (mọi trang).
  int get total => _total;

  bool get hasMore => _hasMore;

  bool get isLoading => _loading;

  /// Đã tải xong ít nhất một trang.
  bool get hasLoaded => _page >= 0;

  ApiErrorModel? get error => _error;

  bool get hasNoSemester => _error?.code == notFound;

  /// Lịch thi gần nhất từ hôm nay trở đi trong các trang đã tải.
  ExamModel? get nextExam {
    for (final exam in _exams) {
      if (!isPast(exam)) {
        return exam;
      }
    }
    return null;
  }

  /// Ngày thi đã qua (trước hôm nay theo giờ Việt Nam).
  bool isPast(ExamModel exam) {
    final date = parseApiDate(exam.date);
    final now = _clock();
    return date != null &&
        date.isBefore(DateTime(now.year, now.month, now.day));
  }

  /// Mở lịch thi của [studentId] (null: SV xem của mình) từ trang đầu.
  Future<void> open(int? studentId) {
    _studentId = studentId;
    return reload();
  }

  Future<void> reload() {
    _generation++;
    _semester = null;
    _exams.clear();
    _page = -1;
    _total = 0;
    _hasMore = true;
    _loading = false;
    _error = null;
    notifyListeners();
    return loadMore();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMore() async {
    if (_loading || !_hasMore) {
      return;
    }
    final generation = _generation;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchExams(
        _studentId,
        page: _page + 1,
      );
      if (generation != _generation) {
        return;
      }
      _semester = result.semester;
      _exams.addAll(result.data.items);
      _page = result.data.page;
      _total = result.data.totalElements;
      _hasMore = result.data.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _generation) {
        _error = error;
      }
    } finally {
      if (generation == _generation) {
        _loading = false;
        notifyListeners();
      }
    }
  }
}
