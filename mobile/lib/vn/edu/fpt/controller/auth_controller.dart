import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';

/// Trạng thái phiên đăng nhập, quyết định màn hình gốc của app.
enum AuthStatus {
  /// Đang khôi phục phiên lúc mở app (hoặc chờ thử lại khi lỗi mạng).
  unknown,
  unauthenticated,
  mustChangePassword,
  authenticated,
}

/// Controller dùng chung cho các màn hình đăng nhập, đổi mật khẩu,
/// khởi động và trang chủ.
class AuthController extends ChangeNotifier {
  final AuthService _authService;

  AuthStatus _status = AuthStatus.unknown;
  UserModel? _currentUser;
  bool _loading = false;
  String? _errorMessage;
  Map<String, String> _fieldErrors = const {};
  ApiErrorModel? _startupError;

  AuthController(this._authService) {
    _authService.bindSessionHandlers(
      onSessionExpired: _handleSessionExpired,
      onPasswordChangeRequired: _handlePasswordChangeRequired,
    );
  }

  AuthStatus get status => _status;

  UserModel? get currentUser => _currentUser;

  bool get isLoading => _loading;

  /// Lỗi chung của thao tác gần nhất (đăng nhập, đổi mật khẩu).
  String? get errorMessage => _errorMessage;

  /// Lỗi theo từng ô, khóa là tên trường của API.
  Map<String, String> get fieldErrors => _fieldErrors;

  /// Lỗi khi khôi phục phiên lúc mở app; màn hình khởi động hiện nút thử lại.
  ApiErrorModel? get startupError => _startupError;

  /// Mở app: chưa có token thì về đăng nhập, có token thì hỏi lại `/me`.
  /// Lỗi mạng giữ nguyên token để người dùng thử lại.
  Future<void> restoreSession() async {
    _status = AuthStatus.unknown;
    _startupError = null;
    _loading = true;
    notifyListeners();
    try {
      if (!await _authService.hasSavedSession()) {
        _setUser(null);
        return;
      }
      _setUser(await _authService.fetchCurrentUser());
    } on ApiErrorModel catch (error) {
      if (error.code == ApiErrorModel.unauthorized) {
        _setUser(null);
      } else {
        _startupError = error;
      }
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  Future<bool> login(String phone, String password) {
    return _run(() async {
      final token = await _authService.login(phone.trim(), password);
      _setUser(token.user);
    });
  }

  Future<bool> changePassword({
    required String currentPassword,
    required String newPassword,
    required String confirmPassword,
  }) {
    return _run(() async {
      final token = await _authService.changePassword(
        currentPassword: currentPassword,
        newPassword: newPassword,
        confirmPassword: confirmPassword,
      );
      _setUser(token.user);
    });
  }

  Future<void> logout() async {
    _loading = true;
    notifyListeners();
    try {
      await _authService.logout();
    } finally {
      _loading = false;
      _clearErrors();
      _setUser(null);
      notifyListeners();
    }
  }

  /// Xóa lỗi của lần gửi trước, dùng khi người dùng sửa lại ô nhập.
  void clearErrors() {
    if (_errorMessage == null && _fieldErrors.isEmpty) {
      return;
    }
    _clearErrors();
    notifyListeners();
  }

  Future<bool> _run(Future<void> Function() action) async {
    _loading = true;
    _clearErrors();
    notifyListeners();
    try {
      await action();
      return true;
    } on ApiErrorModel catch (error) {
      _errorMessage = error.message;
      _fieldErrors = error.fieldErrors;
      return false;
    } finally {
      _loading = false;
      notifyListeners();
    }
  }

  void _setUser(UserModel? user) {
    _currentUser = user;
    if (user == null) {
      _status = AuthStatus.unauthenticated;
    } else if (user.mustChangePassword) {
      _status = AuthStatus.mustChangePassword;
    } else {
      _status = AuthStatus.authenticated;
    }
  }

  void _clearErrors() {
    _errorMessage = null;
    _fieldErrors = const {};
  }

  void _handleSessionExpired() {
    _setUser(null);
    _errorMessage = ApiErrorModel.unauthorizedMessage;
    notifyListeners();
  }

  void _handlePasswordChangeRequired() {
    final user = _currentUser;
    if (user == null || _status == AuthStatus.mustChangePassword) {
      return;
    }
    user.mustChangePassword = true;
    _status = AuthStatus.mustChangePassword;
    notifyListeners();
  }
}
