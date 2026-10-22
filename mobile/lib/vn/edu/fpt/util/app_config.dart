import 'package:flutter/foundation.dart';

/// Cấu hình lúc build. Địa chỉ server truyền qua
/// `--dart-define=API_BASE_URL=https://...`, không viết cứng trong code.
class AppConfig {
  AppConfig._();

  static const String _definedBaseUrl = String.fromEnvironment('API_BASE_URL');

  /// Máy ảo Android gọi backend chạy trên máy dev qua địa chỉ này.
  static const String _debugBaseUrl = 'http://10.0.2.2:8080';

  /// Gốc server, không có dấu `/` ở cuối. Bản release bắt buộc truyền
  /// API_BASE_URL; bản debug mặc định gọi backend trên máy dev.
  static String get apiBaseUrl {
    if (_definedBaseUrl.isNotEmpty) {
      return _definedBaseUrl.endsWith('/')
          ? _definedBaseUrl.substring(0, _definedBaseUrl.length - 1)
          : _definedBaseUrl;
    }
    if (kDebugMode) {
      return _debugBaseUrl;
    }
    throw StateError('API_BASE_URL is required for non-debug builds');
  }

  /// Gốc của REST API v1.
  static String get apiRoot => '$apiBaseUrl/api/v1';
}
