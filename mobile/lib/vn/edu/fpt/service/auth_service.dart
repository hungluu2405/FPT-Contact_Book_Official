import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/token_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/token_storage.dart';

/// Các API xác thực (docs/API.md mục "Xác thực"). Lỗi ném [ApiErrorModel].
class AuthService {
  final ApiClient _apiClient;
  final TokenStorage _tokenStorage;

  AuthService(this._apiClient, this._tokenStorage);

  /// Gắn hai sự kiện phiên đăng nhập của [ApiClient] cho controller.
  void bindSessionHandlers({
    required void Function() onSessionExpired,
    required void Function() onPasswordChangeRequired,
  }) {
    _apiClient.onSessionExpired = onSessionExpired;
    _apiClient.onPasswordChangeRequired = onPasswordChangeRequired;
  }

  Future<bool> hasSavedSession() async {
    return await _tokenStorage.readRefreshToken() != null;
  }

  Future<TokenModel> login(String phone, String password) async {
    final data = await _apiClient.post(
      '/auth/login',
      data: {'phone': phone, 'password': password},
      authenticated: false,
    );
    return _saveTokens(data);
  }

  Future<UserModel> fetchCurrentUser() async {
    final data = await _apiClient.get('/me');
    return UserModel.fromJson(data as Map<String, dynamic>);
  }

  /// Đổi mật khẩu; server trả cặp token mới thay cho cặp cũ.
  Future<TokenModel> changePassword({
    required String currentPassword,
    required String newPassword,
    required String confirmPassword,
  }) async {
    final data = await _apiClient.post(
      '/me/password',
      data: {
        'currentPassword': currentPassword,
        'newPassword': newPassword,
        'confirmPassword': confirmPassword,
      },
    );
    return _saveTokens(data);
  }

  /// Quên mật khẩu, bước 1: server gửi mã 6 chữ số tới email của tài khoản.
  /// Server luôn trả 202 dù số điện thoại có tồn tại hay không.
  Future<void> requestPasswordReset(String phone) async {
    await _apiClient.post(
      '/auth/password-reset/request',
      data: {'phone': phone},
      authenticated: false,
    );
  }

  /// Quên mật khẩu, bước 2: đặt mật khẩu mới bằng mã nhận qua email.
  Future<void> confirmPasswordReset({
    required String phone,
    required String code,
    required String newPassword,
    required String confirmPassword,
  }) async {
    await _apiClient.post(
      '/auth/password-reset/confirm',
      data: {
        'phone': phone,
        'code': code,
        'newPassword': newPassword,
        'confirmPassword': confirmPassword,
      },
      authenticated: false,
    );
  }

  /// Báo server rồi xóa token; mất mạng hay token hết hạn vẫn xóa token.
  Future<void> logout() async {
    try {
      if (await hasSavedSession()) {
        await _apiClient.post('/auth/logout');
      }
    } on ApiErrorModel {
      // Server không lưu token: đăng xuất chỉ cần xóa token trên máy.
    } finally {
      await _tokenStorage.clear();
    }
  }

  Future<TokenModel> _saveTokens(dynamic data) async {
    final token = TokenModel.fromJson(data as Map<String, dynamic>);
    await _tokenStorage.save(
      accessToken: token.accessToken,
      refreshToken: token.refreshToken,
    );
    return token;
  }
}
