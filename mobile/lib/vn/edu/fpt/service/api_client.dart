import 'dart:async';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/api_error_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/token_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/token_storage.dart';

/// Gọi REST API của backend: tự gắn access token, gặp 401 thì làm mới token
/// một lần rồi gửi lại request. Mọi lỗi được đổi thành [ApiErrorModel].
/// Không ghi log token, mật khẩu hay nội dung phản hồi.
class ApiClient {
  static const String _skipAuthKey = 'skipAuth';
  static const String _retriedKey = 'retried';

  final Dio _dio;
  final TokenStorage _tokenStorage;
  Completer<bool>? _refreshing;

  /// Gọi khi refresh token không còn dùng được; token đã bị xóa trước đó.
  void Function()? onSessionExpired;

  /// Gọi khi backend trả 403 PASSWORD_CHANGE_REQUIRED.
  void Function()? onPasswordChangeRequired;

  ApiClient({
    required String baseUrl,
    required this._tokenStorage,
    HttpClientAdapter? httpClientAdapter,
  }) : _dio = Dio(
         BaseOptions(
           baseUrl: baseUrl,
           connectTimeout: const Duration(seconds: 10),
           receiveTimeout: const Duration(seconds: 20),
           contentType: Headers.jsonContentType,
           responseType: ResponseType.json,
         ),
       ) {
    if (httpClientAdapter != null) {
      _dio.httpClientAdapter = httpClientAdapter;
    }
    _dio.interceptors.add(
      InterceptorsWrapper(onRequest: _onRequest, onError: _onError),
    );
  }

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) {
    return _send(() => _dio.get<dynamic>(path, queryParameters: query));
  }

  /// Tải nội dung nhị phân (tệp đính kèm) vào bộ nhớ.
  Future<Uint8List> getBytes(String path) async {
    final data = await _send(
      () => _dio.get<dynamic>(
        path,
        options: Options(responseType: ResponseType.bytes),
      ),
    );
    return Uint8List.fromList(data as List<int>);
  }

  /// [authenticated] = false cho các request không cần token (đăng nhập).
  Future<dynamic> post(String path, {Object? data, bool authenticated = true}) {
    return _send(
      () => _dio.post<dynamic>(
        path,
        data: data,
        options: Options(extra: {_skipAuthKey: !authenticated}),
      ),
    );
  }

  Future<dynamic> _send(Future<Response<dynamic>> Function() request) async {
    try {
      final response = await request();
      return response.data;
    } on DioException catch (exception) {
      throw ApiErrorModel.fromDioException(exception);
    }
  }

  Future<void> _onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (options.extra[_skipAuthKey] != true) {
      final accessToken = await _tokenStorage.readAccessToken();
      if (accessToken != null) {
        options.headers['Authorization'] = 'Bearer $accessToken';
      }
    }
    handler.next(options);
  }

  Future<void> _onError(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    final options = error.requestOptions;
    final status = error.response?.statusCode;
    if (status == 403 &&
        _errorCode(error.response) == ApiErrorModel.passwordChangeRequired) {
      onPasswordChangeRequired?.call();
      handler.next(error);
      return;
    }
    if (status != 401 ||
        options.extra[_skipAuthKey] == true ||
        options.extra[_retriedKey] == true) {
      handler.next(error);
      return;
    }

    try {
      // Request khác đã làm mới token trong lúc request này đang chạy:
      // chỉ cần gửi lại bằng token mới.
      final currentToken = await _tokenStorage.readAccessToken();
      final usedHeader = options.headers['Authorization'];
      final alreadyRefreshed =
          currentToken != null && usedHeader != 'Bearer $currentToken';
      if (!alreadyRefreshed && !await _refreshTokens()) {
        handler.next(error);
        return;
      }
      options.extra[_retriedKey] = true;
      // FormData chỉ gửi được một lần; gửi lại thì dùng bản sao.
      final body = options.data;
      if (body is FormData) {
        options.data = body.clone();
      }
      handler.resolve(await _dio.fetch<dynamic>(options));
    } on DioException catch (retryError) {
      handler.next(retryError);
    } on ApiErrorModel catch (refreshError) {
      handler.next(
        DioException(
          requestOptions: options,
          response: error.response,
          error: refreshError,
        ),
      );
    }
  }

  /// Các request cùng nhận 401 chỉ làm mới token một lần và dùng chung kết quả.
  Future<bool> _refreshTokens() {
    final pending = _refreshing;
    if (pending != null) {
      return pending.future;
    }
    final completer = Completer<bool>();
    _refreshing = completer;
    _doRefresh()
        .then(completer.complete, onError: completer.completeError)
        .whenComplete(() => _refreshing = null);
    return completer.future;
  }

  /// Trả về false khi server từ chối refresh token (đã xóa token và báo hết
  /// phiên). Lỗi mạng hoặc lỗi máy chủ thì ném [ApiErrorModel] và giữ token.
  Future<bool> _doRefresh() async {
    final refreshToken = await _tokenStorage.readRefreshToken();
    if (refreshToken == null) {
      await _expireSession();
      return false;
    }
    try {
      final response = await _dio.post<dynamic>(
        '/auth/refresh',
        data: {'refreshToken': refreshToken},
        options: Options(extra: {_skipAuthKey: true}),
      );
      final token = TokenModel.fromJson(response.data as Map<String, dynamic>);
      await _tokenStorage.save(
        accessToken: token.accessToken,
        refreshToken: token.refreshToken,
      );
      return true;
    } on DioException catch (exception) {
      final apiError = ApiErrorModel.fromDioException(exception);
      if (apiError.isNetworkError || apiError.status >= 500) {
        throw apiError;
      }
      await _expireSession();
      return false;
    }
  }

  Future<void> _expireSession() async {
    await _tokenStorage.clear();
    onSessionExpired?.call();
  }

  static String? _errorCode(Response<dynamic>? response) {
    final data = response?.data;
    return data is Map<String, dynamic> ? data['code'] as String? : null;
  }
}
