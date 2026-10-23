import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/grade_row_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

/// Điểm theo học kỳ của HS (hoặc con của PH). Chỉ đầu điểm đã công bố có
/// điểm; điểm trung bình do backend tính.
class GradeController extends ChangeNotifier {
  static const String notFound = 'NOT_FOUND';

  final StudentService _studentService;

  int? _studentId;
  List<SemesterOptionModel> _semesters = const [];
  int? _selectedSemesterId;
  SemesterModel? _semester;
  List<GradeRowModel> _rows = const [];
  bool _loading = false;
  ApiErrorModel? _error;

  /// Tăng mỗi lần tải điểm để bỏ phản hồi về muộn của học kỳ trước.
  int _generation = 0;

  GradeController(this._studentService);

  List<SemesterOptionModel> get semesters =>
      List<SemesterOptionModel>.unmodifiable(_semesters);

  /// Học kỳ đang xem; lúc đầu là học kỳ hiện tại do backend chọn.
  int? get selectedSemesterId => _selectedSemesterId;

  SemesterModel? get semester => _semester;

  List<GradeRowModel> get rows => List<GradeRowModel>.unmodifiable(_rows);

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  /// Chưa có học kỳ nào: hiện như trạng thái trống, không phải lỗi.
  bool get hasNoSemester => _error?.code == notFound;

  /// Mở điểm của [studentId] (null: HS xem của mình) ở học kỳ hiện tại.
  Future<void> open(int? studentId) async {
    _studentId = studentId;
    _semesters = const [];
    _selectedSemesterId = null;
    _semester = null;
    _rows = const [];
    await Future.wait([_loadSemesters(), _loadGrades(null)]);
  }

  Future<void> selectSemester(int semesterId) => _loadGrades(semesterId);

  Future<void> reload() => _loadGrades(_selectedSemesterId);

  Future<void> _loadSemesters() async {
    try {
      _semesters = await _studentService.fetchSemesters();
      notifyListeners();
    } on ApiErrorModel {
      // Không có ô chọn học kỳ thì vẫn xem được học kỳ hiện tại.
    }
  }

  Future<void> _loadGrades(int? semesterId) async {
    final generation = ++_generation;
    if (semesterId != null) {
      _selectedSemesterId = semesterId;
    }
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchGrades(
        _studentId,
        semesterId: semesterId,
      );
      if (generation != _generation) {
        return;
      }
      _semester = result.semester;
      _selectedSemesterId = result.semester.id;
      _rows = result.data;
    } on ApiErrorModel catch (error) {
      if (generation == _generation) {
        _rows = const [];
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
