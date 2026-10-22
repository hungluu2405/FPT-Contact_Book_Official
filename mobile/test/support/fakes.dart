import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/token_storage.dart';

/// Kho token trong bộ nhớ thay cho secure storage.
class MemoryTokenStorage implements TokenStorage {
  String? accessToken;
  String? refreshToken;

  MemoryTokenStorage({this.accessToken, this.refreshToken});

  @override
  Future<String?> readAccessToken() async => accessToken;

  @override
  Future<String?> readRefreshToken() async => refreshToken;

  @override
  Future<void> save({
    required String accessToken,
    required String refreshToken,
  }) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
  }
}

/// Phản hồi giả của server.
class FakeReply {
  final int status;
  final Object? body;

  const FakeReply(this.status, [this.body]);

  /// Lỗi dạng ApiError của backend.
  factory FakeReply.error(
    int status,
    String code,
    String message, {
    List<Map<String, String>> fieldErrors = const [],
  }) {
    return FakeReply(status, {
      'status': status,
      'code': code,
      'message': message,
      'fieldErrors': fieldErrors,
      'timestamp': '2026-10-22T08:30:00',
    });
  }
}

typedef FakeHandler = Future<FakeReply> Function(RequestOptions request);

/// Thay tầng HTTP của Dio: trả lời theo "METHOD /path", ghi lại mọi request.
/// Handler ném [SocketException] để giả lập mất mạng.
class FakeHttpAdapter implements HttpClientAdapter {
  final Map<String, FakeHandler> handlers = {};
  final List<RequestOptions> requests = [];

  void on(String method, String path, FakeHandler handler) {
    handlers['$method $path'] = handler;
  }

  void reply(String method, String path, FakeReply reply) {
    on(method, path, (_) async => reply);
  }

  void offline(String method, String path) {
    on(method, path, (_) async => throw const SocketException('offline'));
  }

  int count(String method, String path) => requests
      .where((request) => request.method == method && request.path == path)
      .length;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final handler = handlers['${options.method} ${options.path}'];
    if (handler == null) {
      throw StateError('No fake reply for ${options.method} ${options.path}');
    }
    final reply = await handler(options);
    if (reply.body == null) {
      return ResponseBody.fromString('', reply.status);
    }
    return ResponseBody.fromString(
      jsonEncode(reply.body),
      reply.status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> userJson({
  int id = 4,
  String fullName = 'Nguyễn Văn An',
  List<String> roles = const ['STUDENT'],
  bool mustChangePassword = false,
}) => {
  'id': id,
  'phone': '0900000004',
  'fullName': fullName,
  'roles': roles,
  'mustChangePassword': mustChangePassword,
};

Map<String, dynamic> tokenJson({
  String access = 'access-1',
  String refresh = 'refresh-1',
  Map<String, dynamic>? user,
}) => {
  'accessToken': access,
  'refreshToken': refresh,
  'tokenType': 'Bearer',
  'expiresIn': 900,
  'user': user ?? userJson(),
};

Map<String, dynamic> childJson({
  int id = 1,
  String code = 'SE190001',
  String name = 'Nguyễn Văn An',
}) => {'id': id, 'code': code, 'name': name};

Map<String, dynamic> profileJson({
  String fullName = 'Nguyễn Văn An',
  String? email = 'an.nv@fpt.edu.vn',
  List<Map<String, dynamic>> roles = const [
    {'roleCode': 'STUDENT', 'departmentCode': null, 'departmentName': null},
  ],
}) => {
  'phone': '0900000004',
  'fullName': fullName,
  'email': email,
  'gender': 'MALE',
  'dateOfBirth': '2005-03-15',
  'address': 'Hà Nội',
  'hasAvatar': false,
  'lastLoginAt': '2026-10-22T08:00:00',
  'roles': roles,
};

/// Bộ đối tượng thật nối với tầng HTTP giả.
class TestApp {
  final FakeHttpAdapter http = FakeHttpAdapter();
  final MemoryTokenStorage storage;
  late final ApiClient apiClient = ApiClient(
    baseUrl: 'http://test/api/v1',
    tokenStorage: storage,
    httpClientAdapter: http,
  );
  late final AuthService authService = AuthService(apiClient, storage);
  late final AuthController controller = AuthController(authService);
  late final PasswordResetController resetController = PasswordResetController(
    authService,
  );
  late final HomeController homeController = HomeController(
    StudentService(apiClient),
    NotificationService(apiClient),
  );
  late final ProfileController profileController = ProfileController(
    ProfileService(apiClient),
  );

  TestApp({String? accessToken, String? refreshToken})
    : storage = MemoryTokenStorage(
        accessToken: accessToken,
        refreshToken: refreshToken,
      );

  /// Trả lời các request của trang chủ: số chưa đọc và (PH) danh sách con.
  void stubHome({
    int unread = 0,
    List<Map<String, dynamic>> children = const [],
  }) {
    http.reply(
      'GET',
      '/notifications/unread-count',
      FakeReply(200, {'count': unread}),
    );
    http.reply('GET', '/parent/children', FakeReply(200, children));
  }
}
