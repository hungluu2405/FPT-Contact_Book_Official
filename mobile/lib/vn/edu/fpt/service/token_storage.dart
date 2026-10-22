import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Nơi lưu cặp token trên máy. Token chỉ nằm trong secure storage.
abstract class TokenStorage {
  Future<String?> readAccessToken();

  Future<String?> readRefreshToken();

  Future<void> save({
    required String accessToken,
    required String refreshToken,
  });

  Future<void> clear();
}

/// Lưu token bằng flutter_secure_storage, giữ bản sao trong bộ nhớ để
/// không phải đọc lại ở mỗi request.
class SecureTokenStorage implements TokenStorage {
  static const String _accessKey = 'access_token';
  static const String _refreshKey = 'refresh_token';

  final FlutterSecureStorage _storage;
  bool _loaded = false;
  String? _accessToken;
  String? _refreshToken;

  SecureTokenStorage([FlutterSecureStorage? storage])
    : _storage = storage ?? const FlutterSecureStorage();

  Future<void> _load() async {
    if (_loaded) {
      return;
    }
    _accessToken = await _storage.read(key: _accessKey);
    _refreshToken = await _storage.read(key: _refreshKey);
    _loaded = true;
  }

  @override
  Future<String?> readAccessToken() async {
    await _load();
    return _accessToken;
  }

  @override
  Future<String?> readRefreshToken() async {
    await _load();
    return _refreshToken;
  }

  @override
  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    _loaded = true;
    await _storage.write(key: _accessKey, value: accessToken);
    await _storage.write(key: _refreshKey, value: refreshToken);
  }

  @override
  Future<void> clear() async {
    _accessToken = null;
    _refreshToken = null;
    _loaded = true;
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}
