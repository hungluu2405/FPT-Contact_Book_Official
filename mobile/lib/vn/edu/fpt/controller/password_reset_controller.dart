import 'package:flutter/foundation.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';

/// Controller dùng chung cho hai màn hình quên mật khẩu: nhập số điện thoại
/// để nhận mã qua email, rồi nhập mã và mật khẩu mới.
class PasswordResetController extends ChangeNotifier {
  final AuthService _authService;

  String _phone = '';
  bool _loading = false;
  String? _errorMessage;
  Map<String, String> _fieldErrors = const {};

  PasswordResetController(this._authService);

  /// Số điện thoại đã dùng để yêu cầu mã, chuyển sang bước nhập mã.
  String get phone => _phone;

  bool get isLoading => _loading;

  String? get errorMessage => _errorMessage;

  /// Lỗi theo từng ô, khóa là tên trường của API (phone, code, newPassword...).
  Map<String, String> get fieldErrors => _fieldErrors;

  Future<bool> requestCode(String phone) {
    return _run(() async {
      await _authService.requestPasswordReset(phone.trim());
      _phone = phone.trim();
    });
  }

  Future<bool> resetPassword({
    required String code,
    required String newPassword,
    required String confirmPassword,
  }) {
    return _run(
      () => _authService.confirmPasswordReset(
        phone: _phone,
        code: code.trim(),
        newPassword: newPassword,
        confirmPassword: confirmPassword,
      ),
    );
  }

  /// Xóa lỗi của lần gửi trước, dùng khi người dùng sửa lại ô nhập.
  void clearErrors() {
    if (_errorMessage == null && _fieldErrors.isEmpty) {
      return;
    }
    _errorMessage = null;
    _fieldErrors = const {};
    notifyListeners();
  }

  Future<bool> _run(Future<void> Function() action) async {
    _loading = true;
    _errorMessage = null;
    _fieldErrors = const {};
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
}
