import 'dart:io';

/// Contract test gọi backend thật qua API, ghi vào database dev. Chỉ được
/// chạy với backend trên máy này: mọi địa chỉ khác bị chặn trước khi gửi
/// request nào.
const String kDefaultContractBaseUrl = 'http://localhost:8080';

const Set<String> _allowedHosts = {'localhost', '127.0.0.1'};

/// Gốc API v1 cho contract test, lấy từ biến môi trường `API_BASE_URL`.
String contractApiRoot([Map<String, String>? environment]) {
  final env = environment ?? Platform.environment;
  final raw = (env['API_BASE_URL'] ?? '').trim();
  final baseUrl = raw.isEmpty ? kDefaultContractBaseUrl : raw;
  checkLocalBackend(baseUrl);
  final trimmed = baseUrl.endsWith('/')
      ? baseUrl.substring(0, baseUrl.length - 1)
      : baseUrl;
  return '$trimmed/api/v1';
}

/// Ném [StateError] khi [baseUrl] không phải http(s) tới localhost.
void checkLocalBackend(String baseUrl) {
  final uri = Uri.tryParse(baseUrl);
  final valid =
      uri != null &&
      (uri.scheme == 'http' || uri.scheme == 'https') &&
      _allowedHosts.contains(uri.host);
  if (!valid) {
    throw StateError(
      'Contract tests only run against a local backend, got "$baseUrl"',
    );
  }
}
