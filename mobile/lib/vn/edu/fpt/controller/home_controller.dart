import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/child_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/schedule_item_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

/// Trang chủ của SV và PH: danh sách con, người đang được xem, lịch học hôm
/// nay của người đó, số thông báo chưa đọc. Các màn hình chức năng lấy
/// [selectedStudentId] để biết xem dữ liệu của ai.
class HomeController extends ChangeNotifier {
  final StudentService _studentService;
  final NotificationService _notificationService;

  UserModel? _user;
  List<ChildModel> _children = const [];
  ChildModel? _selectedChild;
  bool _loadingChildren = false;
  ApiErrorModel? _childrenError;
  int? _unreadCount;
  List<ScheduleItemModel> _todayItems = const [];
  bool _loadingToday = false;
  ApiErrorModel? _todayError;

  /// Tăng mỗi lần [start] để bỏ kết quả của phiên trước về muộn.
  int _generation = 0;

  /// Tăng mỗi lần tải lịch hôm nay (đổi con, tải lại) vì lý do tương tự.
  int _todayGeneration = 0;

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

  /// Các buổi học hôm nay của người đang được xem, theo giờ.
  List<ScheduleItemModel> get todayItems =>
      List<ScheduleItemModel>.unmodifiable(_todayItems);

  bool get isLoadingToday => _loadingToday;

  ApiErrorModel? get todayError => _todayError;

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
    _todayGeneration++;
    _todayItems = const [];
    _loadingToday = false;
    _todayError = null;
    notifyListeners();
    return refresh();
  }

  /// Tải lại danh sách con, lịch hôm nay và số chưa đọc (kéo xuống để tải
  /// lại). PH cần danh sách con trước để biết lịch của ai.
  Future<void> refresh() async {
    final user = _user;
    if (user == null) {
      return;
    }
    final generation = _generation;
    await Future.wait([
      () async {
        if (user.isParent) {
          await _loadChildren(generation);
        }
        if (generation == _generation) {
          await loadToday();
        }
      }(),
      if (user.canUseMobile) _loadUnreadCount(generation),
    ]);
  }

  /// Tải lịch học hôm nay của người đang được xem (nút thử lại của thẻ).
  Future<void> loadToday() async {
    final generation = ++_todayGeneration;
    if (!hasStudentData) {
      _todayItems = const [];
      _loadingToday = false;
      notifyListeners();
      return;
    }
    _loadingToday = true;
    _todayError = null;
    notifyListeners();
    try {
      final result = await _studentService.fetchWeek(selectedStudentId);
      if (generation == _todayGeneration) {
        _todayItems = result.data.todayDay?.items ?? const [];
      }
    } on ApiErrorModel catch (error) {
      if (generation == _todayGeneration) {
        _todayItems = const [];
        // Chưa có học kỳ nào thì coi như hôm nay không có lịch.
        _todayError = error.code == 'NOT_FOUND' ? null : error;
      }
    } finally {
      if (generation == _todayGeneration) {
        _loadingToday = false;
        notifyListeners();
      }
    }
  }

  /// Chọn người xem; [child] null là SV xem của chính mình.
  void selectChild(ChildModel? child) {
    if (child == null && !(_user?.isStudent ?? false)) {
      return;
    }
    if (child?.id == _selectedChild?.id) {
      return;
    }
    _selectedChild = child;
    notifyListeners();
    unawaited(loadToday());
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
