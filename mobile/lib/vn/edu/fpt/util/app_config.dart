import 'package:flutter/foundation.dart';

/// Cấu hình lúc build. Địa chỉ server truyền qua
/// `--dart-define=API_BASE_URL=https://...`, không viết cứng trong code.
class AppConfig {
  AppConfig._();

  static const String _definedBaseUrl = String.fromEnvironment('API_BASE_URL');

  /// Chỉ APK demo trong mạng LAN mới truyền giá trị này (cùng địa chỉ với
  /// thuộc tính Gradle `demoCleartextHost`): bản release được gọi HTTP
  /// thường tới đúng máy đó.
  static const String _demoCleartextHost = String.fromEnvironment(
    'DEMO_CLEARTEXT_HOST',
  );

  /// Máy ảo Android gọi backend chạy trên máy dev qua địa chỉ này.
  static const String debugBaseUrl = 'http://10.0.2.2:8080';

  /// Gốc server, không có dấu `/` ở cuối. Bản release bắt buộc truyền
  /// API_BASE_URL; bản debug mặc định gọi backend trên máy dev.
  static String get apiBaseUrl => resolveBaseUrl(
    defined: _definedBaseUrl,
    debug: kDebugMode,
    demoCleartextHost: _demoCleartextHost,
  );

  /// Gốc của REST API v1.
  static String get apiRoot => '$apiBaseUrl/api/v1';

  /// Chọn và kiểm tra địa chỉ server. Bản không phải debug chỉ nhận HTTPS,
  /// trừ HTTP tới đúng [demoCleartextHost] của APK demo.
  @visibleForTesting
  static String resolveBaseUrl({
    required String defined,
    required bool debug,
    required String demoCleartextHost,
  }) {
    if (defined.isEmpty) {
      if (debug) {
        return debugBaseUrl;
      }
      throw StateError('API_BASE_URL is required for non-debug builds');
    }
    final url = defined.endsWith('/')
        ? defined.substring(0, defined.length - 1)
        : defined;
    final uri = Uri.tryParse(url);
    if (uri == null ||
        (uri.scheme != 'http' && uri.scheme != 'https') ||
        uri.host.isEmpty) {
      throw StateError('API_BASE_URL must be an absolute http(s) URL');
    }
    if (!debug &&
        uri.scheme == 'http' &&
        (demoCleartextHost.isEmpty || uri.host != demoCleartextHost)) {
      throw StateError('Non-debug builds must use an https API_BASE_URL');
    }
    return url;
  }
}
