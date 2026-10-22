import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

/// Trang chủ của SV và PH: danh sách con, người đang được xem, số thông báo
/// chưa đọc. Các màn hình chức năng lấy [selectedStudentId] để biết xem dữ
/// liệu của ai.
class HomeController extends ChangeNotifier {
  final StudentService _studentService;
  final NotificationService _notificationService;

  UserModel? _user;
  List<ChildModel> _children = const [];
  ChildModel? _selectedChild;
  bool _loadingChildren = false;
  ApiErrorModel? _childrenError;
  int? _unreadCount;

  /// Tăng mỗi lần [start] để bỏ kết quả của phiên trước về muộn.
  int _generation = 0;

  HomeController(this._studentService, this._notificationService);

  UserModel? get user => _user;

  List<ChildModel> get children => List<ChildModel>.unmodifiable(_children);

  /// Con đang xem; null nghĩa là SV xem dữ liệu của chính mình
  /// (hoặc PH chưa có con nào).
  ChildModel? get selectedChild => _selectedChild;

  /// `students.id` truyền cho [StudentService.rootPath].
  int? get selectedStudentId => _selectedChild?.id;

  bool get isLoadingChildren => _loadingChildren;

  ApiErrorModel? get childrenError => _childrenError;

  /// Null khi chưa tải được; lỗi của số này không hiện cho người dùng.
  int? get unreadCount => _unreadCount;

  /// Có ai để xem dữ liệu học tập: bản thân là SV hoặc đã có con.
  bool get hasStudentData =>
      (_user?.isStudent ?? false) || _children.isNotEmpty;

  /// Có ô chọn người xem: PH có từ 2 lựa chọn trở lên (tính cả "Của tôi"
  /// khi vừa là SV vừa là PH).
  bool get canSwitchStudent =>
      _children.length + ((_user?.isStudent ?? false) ? 1 : 0) > 1;

  /// Gọi khi vào trang chủ sau đăng nhập; xóa dữ liệu của phiên trước.
  Future<void> start(UserModel user) {
    _generation++;
    _user = user;
    _children = const [];
    _selectedChild = null;
    _childrenError = null;
    _unreadCount = null;
    notifyListeners();
    return refresh();
  }

  /// Tải lại danh sách con và số chưa đọc (kéo xuống để tải lại).
  Future<void> refresh() async {
    final user = _user;
    if (user == null) {
      return;
    }
    await Future.wait([
      if (user.isParent) _loadChildren(_generation),
      if (user.canUseMobile) _loadUnreadCount(_generation),
    ]);
  }

  /// Chọn người xem; [child] null là SV xem của chính mình.
  void selectChild(ChildModel? child) {
    if (child == null && !(_user?.isStudent ?? false)) {
      return;
    }
    _selectedChild = child;
    notifyListeners();
  }

  Future<void> _loadChildren(int generation) async {
    _loadingChildren = true;
    _childrenError = null;
    notifyListeners();
    try {
      final children = await _studentService.fetchChildren();
      if (generation != _generation) {
        return;
      }
      _children = children;
      _selectedChild = _keepSelection(children);
    } on ApiErrorModel catch (error) {
      if (generation == _generation) {
        _childrenError = error;
      }
    } finally {
      if (generation == _generation) {
        _loadingChildren = false;
        notifyListeners();
      }
    }
  }

  /// Giữ con đang xem nếu vẫn còn liên kết; không thì SV về "Của tôi",
  /// PH về con đầu tiên.
  ChildModel? _keepSelection(List<ChildModel> children) {
    final current = _selectedChild;
    if (current != null) {
      for (final child in children) {
        if (child.id == current.id) {
          return child;
        }
      }
    }
    if ((_user?.isStudent ?? false) || children.isEmpty) {
      return null;
    }
    return children.first;
  }

  Future<void> _loadUnreadCount(int generation) async {
    try {
      final count = await _notificationService.fetchUnreadCount();
      if (generation == _generation) {
        _unreadCount = count;
        notifyListeners();
      }
    } on ApiErrorModel {
      // Chỉ là số trên biểu tượng chuông, lỗi thì để trống.
    }
  }
}
