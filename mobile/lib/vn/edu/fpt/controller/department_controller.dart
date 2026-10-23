import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/department_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';

/// Tổ chuyên môn của tổ trưởng: các tổ mình phụ trách (khối trên trang chủ),
/// giáo viên của tổ đang chọn (tìm kiếm, tải theo trang) và chi tiết một giáo
/// viên. Dùng chung cho khối trang chủ, màn hình danh sách và chi tiết.
class DepartmentController extends ChangeNotifier {
  final DepartmentService _departmentService;

  List<DepartmentModel> _departments = const [];
  DepartmentModel? _selected;
  bool _loadingDepartments = false;
  ApiErrorModel? _departmentsError;

  String _query = '';
  final List<TeacherModel> _teachers = [];
  int _page = -1;
  bool _hasMore = true;
  bool _loading = false;
  ApiErrorModel? _error;

  TeacherDetailModel? _detail;
  bool _loadingDetail = false;
  ApiErrorModel? _detailError;

  /// Tăng mỗi lần tải lại, tìm kiếm, đổi tổ hoặc mở giáo viên khác để bỏ
  /// phản hồi về muộn.
  int _departmentsGeneration = 0;
  int _listGeneration = 0;
  int _detailGeneration = 0;

  DepartmentController(this._departmentService);

  List<DepartmentModel> get departments =>
      List<DepartmentModel>.unmodifiable(_departments);

  /// Tổ đang xem; null khi chưa tải được hoặc chưa được gán tổ nào.
  DepartmentModel? get selectedDepartment => _selected;

  bool get isLoadingDepartments => _loadingDepartments;

  ApiErrorModel? get departmentsError => _departmentsError;

  String get query => _query;

  List<TeacherModel> get teachers => List<TeacherModel>.unmodifiable(_teachers);

  bool get hasMore => _hasMore;

  bool get isLoading => _loading;

  ApiErrorModel? get error => _error;

  TeacherDetailModel? get detail => _detail;

  bool get isLoadingDetail => _loadingDetail;

  ApiErrorModel? get detailError => _detailError;

  /// Xóa dữ liệu của phiên trước (gọi khi vào app và khi đăng xuất).
  void reset() {
    _departmentsGeneration++;
    _listGeneration++;
    _detailGeneration++;
    _departments = const [];
    _selected = null;
    _loadingDepartments = false;
    _departmentsError = null;
    _query = '';
    _teachers.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    _detail = null;
    _loadingDetail = false;
    _detailError = null;
  }

  /// Tải các tổ mình phụ trách; giữ tổ đang chọn nếu vẫn còn, không thì chọn
  /// tổ đầu tiên.
  Future<void> loadDepartments() async {
    final generation = ++_departmentsGeneration;
    _loadingDepartments = true;
    _departmentsError = null;
    notifyListeners();
    try {
      final departments = await _departmentService.fetchDepartments();
      if (generation != _departmentsGeneration) {
        return;
      }
      _departments = departments;
      _selected = _keepSelection(departments);
    } on ApiErrorModel catch (error) {
      if (generation == _departmentsGeneration) {
        _departmentsError = error;
      }
    } finally {
      if (generation == _departmentsGeneration) {
        _loadingDepartments = false;
        notifyListeners();
      }
    }
  }

  void selectDepartment(DepartmentModel department) {
    if (department.id == _selected?.id) {
      return;
    }
    _selected = department;
    notifyListeners();
  }

  /// Mở danh sách giáo viên của tổ đang chọn, bỏ từ khóa cũ.
  Future<void> openTeachers() {
    _query = '';
    return _restart();
  }

  /// Tìm theo tên, mã, số điện thoại (bỏ khoảng trắng hai đầu).
  Future<void> search(String query) {
    _query = query.trim();
    return _restart();
  }

  /// Tải lại từ trang đầu với từ khóa hiện tại (kéo xuống để tải lại).
  Future<void> reload() => _restart();

  Future<void> _restart() {
    _listGeneration++;
    _teachers.clear();
    _page = -1;
    _hasMore = true;
    _loading = false;
    _error = null;
    return loadMore();
  }

  /// Tải trang kế tiếp; bỏ qua nếu đang tải hoặc đã hết.
  Future<void> loadMore() async {
    if (_loading || !_hasMore) {
      return;
    }
    final generation = _listGeneration;
    _loading = true;
    _error = null;
    notifyListeners();
    try {
      final page = await _departmentService.fetchTeachers(
        departmentId: _selected?.id,
        query: _query,
        page: _page + 1,
      );
      if (generation != _listGeneration) {
        return;
      }
      _teachers.addAll(page.items);
      _page = page.page;
      _hasMore = page.hasMore;
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

  /// Mở chi tiết một giáo viên trong tổ.
  Future<void> openTeacher(int userId) async {
    final generation = ++_detailGeneration;
    _detail = null;
    _detailError = null;
    _loadingDetail = true;
    notifyListeners();
    try {
      final detail = await _departmentService.fetchTeacher(userId);
      if (generation == _detailGeneration) {
        _detail = detail;
      }
    } on ApiErrorModel catch (error) {
      if (generation == _detailGeneration) {
        _detailError = error;
      }
    } finally {
      if (generation == _detailGeneration) {
        _loadingDetail = false;
        notifyListeners();
      }
    }
  }

  DepartmentModel? _keepSelection(List<DepartmentModel> departments) {
    final current = _selected;
    if (current != null) {
      for (final department in departments) {
        if (department.id == current.id) {
          return department;
        }
      }
    }
    return departments.isEmpty ? null : departments.first;
  }
}
