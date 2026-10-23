import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/semester_option_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_section_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';

/// "Lớp của tôi" của giáo viên: lớp đang dạy theo học kỳ (ô chọn học kỳ,
/// tải theo trang) và chi tiết một lớp (buổi học và chuyên cần học sinh, mỗi
/// phần tải theo trang riêng). Dùng chung cho danh sách và chi tiết lớp.
class TeacherSectionController extends ChangeNotifier {
  final TeacherService _teacherService;
  final StudentService _studentService;

  List<SemesterOptionModel> _semesters = const [];
  int? _semesterId;
  SemesterModel? _semester;

  final List<TeacherSectionModel> _sections = [];
  int _page = -1;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;

  int? _sectionId;
  TeacherSectionModel? _section;
  int _thresholdPercent = 0;
  int _totalSessions = 0;
  final List<SectionSessionModel> _sessions = [];
  int _sessionPage = 0;
  bool _sessionsHasMore = false;
  final List<SectionStudentModel> _students = [];
  int _studentPage = 0;
  bool _studentsHasMore = false;
  bool _loadingSection = false;
  bool _loadingMore = false;
  ApiErrorModel? _sectionError;

  /// Tăng mỗi lần đổi học kỳ, tải lại hoặc mở lớp khác để bỏ phản hồi về
  /// muộn.
  int _listGeneration = 0;
  int _sectionGeneration = 0;

  TeacherSectionController(this._teacherService, this._studentService);

  /// Học kỳ cho ô chọn; rỗng khi tải lỗi (ẩn ô chọn, vẫn xem học kỳ hiện tại).
  List<SemesterOptionModel> get semesters =>
      List<SemesterOptionModel>.unmodifiable(_semesters);

  /// Học kỳ đang xem (backend chọn khi mở lần đầu).
  SemesterModel? get semester => _semester;

  List<TeacherSectionModel> get sections =>
      List<TeacherSectionModel>.unmodifiable(_sections);

  bool get sectionsHasMore => _hasMore;

  bool get isLoadingSections => _loading;

  ApiErrorModel? get sectionsError => _error;

  TeacherSectionModel? get section => _section;

  int get thresholdPercent => _thresholdPercent;

  int get totalSessions => _totalSessions;

  List<SectionSessionModel> get sessions =>
      List<SectionSessionModel>.unmodifiable(_sessions);

  bool get sessionsHasMore => _sessionsHasMore;

  List<SectionStudentModel> get students =>
      List<SectionStudentModel>.unmodifiable(_students);

  bool get studentsHasMore => _studentsHasMore;

  bool get isLoadingSection => _loadingSection;

  bool get isLoadingMore => _loadingMore;

  ApiErrorModel? get sectionError => _sectionError;

  /// Mở danh sách lớp của học kỳ hiện tại và tải ô chọn học kỳ.
  Future<void> open() {
    _semesterId = null;
    return Future.wait([_loadSemesters(), _restart()]);
  }

  Future<void> _loadSemesters() async {
    try {
      _semesters = await _studentService.fetchSemesters();
    } on ApiErrorModel {
      // Ô chọn là phần phụ: lỗi thì ẩn, vẫn xem được học kỳ hiện tại.
      _semesters = const [];
    }
    notifyListeners();
  }

  Future<void> selectSemester(int semesterId) {
    _semesterId = semesterId;
    return _restart();
  }

  /// Tải lại danh sách lớp (kéo xuống để tải lại).
  Future<void> reloadSections() => _restart();

  Future<void> _restart() {
    _listGeneration++;
    _sections.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    return loadMoreSections();
  }

  /// Tải trang lớp kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMoreSections() async {
    if (_loading || !_hasMore) {
      return;
    }
    final generation = _listGeneration;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final result = await _teacherService.fetchSections(
        semesterId: _semesterId,
        page: _page + 1,
      );
      if (generation != _listGeneration) {
        return;
      }
      _semester = result.semester;
      _sections.addAll(result.data.items);
      _page = result.data.page;
      _hasMore = result.data.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _listGeneration) {
        _error = error;
      }
    } finally {
      if (generation == _listGeneration) {
        _loading = false;
        notifyListeners();
      }
    }
  }

  /// Mở chi tiết lớp: trang đầu buổi học và trang đầu học sinh.
  Future<void> openSection(int sectionId) async {
    final generation = ++_sectionGeneration;
    _sectionId = sectionId;
    _section = null;
    _sessions.clear();
    _students.clear();
    _sessionsHasMore = false;
    _studentsHasMore = false;
    _loadingMore = false;
    _loadingSection = true;
    _sectionError = null;
    notifyListeners();
    try {
      final data = await _teacherService.fetchSectionAttendance(sectionId);
      if (generation != _sectionGeneration) {
        return;
      }
      _section = data.section;
      _thresholdPercent = data.thresholdPercent;
      _totalSessions = data.totalSessions;
      _sessions.addAll(data.sessions.items);
      _sessionPage = data.sessions.page;
      _sessionsHasMore = data.sessions.hasMore;
      _students.addAll(data.students.items);
      _studentPage = data.students.page;
      _studentsHasMore = data.students.hasMore;
    } on ApiErrorModel catch (error) {
      if (generation == _sectionGeneration) {
        _sectionError = error;
      }
    } finally {
      if (generation == _sectionGeneration) {
        _loadingSection = false;
        notifyListeners();
      }
    }
  }

  /// Tải lại chi tiết lớp đang mở (sau khi điểm danh, kéo xuống để tải lại).
  Future<void> reloadSection() async {
    final id = _sectionId;
    if (id != null) {
      await openSection(id);
    }
  }

  Future<void> loadMoreSessions() => _loadMore(sessions: true);

  Future<void> loadMoreStudents() => _loadMore(sessions: false);

  Future<void> _loadMore({required bool sessions}) async {
    final id = _sectionId;
    final hasMore = sessions ? _sessionsHasMore : _studentsHasMore;
    if (id == null || _loadingMore || !hasMore) {
      return;
    }
    final generation = _sectionGeneration;
    _loadingMore = true;
    notifyListeners();
    try {
      final data = await _teacherService.fetchSectionAttendance(
        id,
        sessionPage: sessions ? _sessionPage + 1 : _sessionPage,
        studentPage: sessions ? _studentPage : _studentPage + 1,
      );
      if (generation != _sectionGeneration) {
        return;
      }
      if (sessions) {
        _sessions.addAll(data.sessions.items);
        _sessionPage = data.sessions.page;
        _sessionsHasMore = data.sessions.hasMore;
      } else {
        _students.addAll(data.students.items);
        _studentPage = data.students.page;
        _studentsHasMore = data.students.hasMore;
      }
    } on ApiErrorModel {
      // Phần đã có vẫn giữ; kéo xuống để tải lại.
    } finally {
      if (generation == _sectionGeneration) {
        _loadingMore = false;
        notifyListeners();
      }
    }
  }
}
