import 'dart:convert';

import 'package:dio/dio.dart';

/// Lỗi trả về từ API (dạng ApiError trong docs/API.md) hoặc lỗi mạng.
/// Màn hình xử lý theo [code] và hiện [message] cho người dùng.
class ApiErrorModel implements Exception {
  static const String networkError = 'NETWORK_ERROR';
  static const String unauthorized = 'UNAUTHORIZED';
  static const String passwordChangeRequired = 'PASSWORD_CHANGE_REQUIRED';
  static const String payloadTooLarge = 'PAYLOAD_TOO_LARGE';
  static const String internalError = 'INTERNAL_ERROR';

  static const String networkMessage =
      'Không kết nối được máy chủ. Vui lòng kiểm tra mạng rồi thử lại.';
  static const String payloadTooLargeMessage =
      'Tệp quá lớn (tối đa 10MB mỗi tệp, 30MB mỗi lần gửi).';
  static const String internalMessage =
      'Hệ thống đang gặp lỗi, vui lòng thử lại sau.';
  static const String unauthorizedMessage =
      'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.';

  int _status;
  String _code;
  String _message;
  Map<String, String> _fieldErrors;

  ApiErrorModel({
    required this._status,
    required this._code,
    required this._message,
    Map<String, String> fieldErrors = const {},
  }) : _fieldErrors = Map<String, String>.of(fieldErrors);

  /// Đọc body ApiError. Một ô có nhiều lỗi thì giữ lỗi đầu tiên.
  factory ApiErrorModel.fromJson(Map<String, dynamic> json) {
    final fieldErrors = <String, String>{};
    for (final item in json['fieldErrors'] as List<dynamic>? ?? const []) {
      final error = item as Map<String, dynamic>;
      final field = error['field'] as String?;
      final message = error['message'] as String?;
      if (field != null && message != null) {
        fieldErrors.putIfAbsent(field, () => message);
      }
    }
    return ApiErrorModel(
      status: (json['status'] as num?)?.toInt() ?? 0,
      code: json['code'] as String? ?? internalError,
      message: json['message'] as String? ?? internalMessage,
      fieldErrors: fieldErrors,
    );
  }

  /// Đổi lỗi của Dio thành lỗi hiển thị được cho người dùng.
  factory ApiErrorModel.fromDioException(DioException exception) {
    final cause = exception.error;
    if (cause is ApiErrorModel) {
      return cause;
    }
    final response = exception.response;
    if (response == null) {
      return ApiErrorModel(
        status: 0,
        code: networkError,
        message: networkMessage,
      );
    }
    final status = response.statusCode ?? 0;
    final data = _decodeBytes(response.data);
    if (data is Map<String, dynamic> && data['code'] is String) {
      return ApiErrorModel.fromJson(data);
    }
    if (status == 413) {
      return ApiErrorModel(
        status: status,
        code: payloadTooLarge,
        message: payloadTooLargeMessage,
      );
    }
    if (status == 401) {
      return ApiErrorModel(
        status: status,
        code: unauthorized,
        message: unauthorizedMessage,
      );
    }
    return ApiErrorModel(
      status: status,
      code: internalError,
      message: internalMessage,
    );
  }

  /// Request tải tệp nhận body dạng byte, kể cả khi lỗi: đọc lại thành JSON
  /// để vẫn lấy được thông báo của backend.
  static Object? _decodeBytes(Object? data) {
    if (data is! List<int>) {
      return data;
    }
    try {
      return jsonDecode(utf8.decode(data));
    } on FormatException {
      return null;
    }
  }

  Map<String, dynamic> toJson() => {
    'status': _status,
    'code': _code,
    'message': _message,
    'fieldErrors': [
      for (final entry in _fieldErrors.entries)
        {'field': entry.key, 'message': entry.value},
    ],
  };

  /// Mã HTTP; 0 khi không nhận được phản hồi (lỗi mạng).
  int get status => _status;
  set status(int value) => _status = value;

  String get code => _code;
  set code(String value) => _code = value;

  String get message => _message;
  set message(String value) => _message = value;

  Map<String, String> get fieldErrors =>
      Map<String, String>.unmodifiable(_fieldErrors);
  set fieldErrors(Map<String, String> value) =>
      _fieldErrors = Map<String, String>.of(value);

  bool get isNetworkError => _code == networkError;

  @override
  String toString() => 'ApiErrorModel($_status, $_code)';
}
