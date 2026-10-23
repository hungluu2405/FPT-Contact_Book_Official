import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
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

/// "Bây giờ" của test: thứ Tư 21/10/2026 lúc 09:45 (giờ Việt Nam).
final DateTime kTestNow = DateTime(2026, 10, 21, 9, 45);

Map<String, dynamic> semesterJson() => {
  'id': 3,
  'code': 'FA26',
  'name': 'Học kỳ Thu 2026',
  'startDate': '2026-09-07',
  'endDate': '2026-12-27',
  'current': true,
};

Map<String, dynamic> sessionJson({
  String date = '2026-10-21',
  String start = '07:30:00',
  String end = '09:20:00',
  String subjectCode = 'MAD101',
  String subjectName = 'Toán rời rạc',
  String room = 'DE205',
  String teacher = 'Giang NT',
  String status = 'SCHEDULED',
  String? note,
}) => {
  'date': date,
  'startTime': start,
  'endTime': end,
  'subjectCode': subjectCode,
  'subjectName': subjectName,
  'sectionCode': '${subjectCode}_SE1901',
  'roomCode': room,
  'teacherName': teacher,
  'status': status,
  'note': note,
};

/// Tuần 19–25/10/2026, hôm nay là thứ Tư 21/10; [items] theo ngày `yyyy-MM-dd`.
Map<String, dynamic> weekJson({
  String weekStart = '2026-10-19',
  String? today = '2026-10-21',
  Map<String, List<Map<String, dynamic>>> items = const {},
}) {
  final monday = DateTime.parse(weekStart);
  String iso(DateTime date) => date.toIso8601String().substring(0, 10);
  return {
    'semester': semesterJson(),
    'data': {
      'weekStart': weekStart,
      'weekEnd': iso(monday.add(const Duration(days: 6))),
      'previous': iso(monday.subtract(const Duration(days: 7))),
      'next': iso(monday.add(const Duration(days: 7))),
      'days': [
        for (var i = 0; i < 7; i++)
          {
            'date': iso(monday.add(Duration(days: i))),
            'today': iso(monday.add(Duration(days: i))) == today,
            'items': items[iso(monday.add(Duration(days: i)))] ?? const [],
          },
      ],
    },
  };
}

Map<String, dynamic> examJson({
  int id = 1,
  String date = '2026-10-30',
  String type = 'MIDTERM',
  String subjectCode = 'PRM393',
  String subjectName = 'Lập trình di động',
  String room = 'BE202',
}) => {
  'id': id,
  'sectionCode': '${subjectCode}_SE1901',
  'subjectCode': subjectCode,
  'subjectName': subjectName,
  'examType': type,
  'date': date,
  'startTime': '07:30:00',
  'endTime': '09:00:00',
  'roomCode': room,
  'note': null,
};

/// Một trang dạng `{semester, data: {items, page, size, ...}}`.
Map<String, dynamic> pagedJson(
  List<Map<String, dynamic>> items, {
  int page = 0,
  int totalPages = 1,
  int? totalElements,
}) => {
  'semester': semesterJson(),
  'data': {
    'items': items,
    'page': page,
    'size': 20,
    'totalElements': totalElements ?? items.length,
    'totalPages': totalPages,
  },
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
  late final ScheduleController scheduleController = ScheduleController(
    StudentService(apiClient),
    clock: () => kTestNow,
  );
  late final ExamController examController = ExamController(
    StudentService(apiClient),
    clock: () => kTestNow,
  );

  TestApp({String? accessToken, String? refreshToken})
    : storage = MemoryTokenStorage(
        accessToken: accessToken,
        refreshToken: refreshToken,
      );

  /// Trả lời các request của trang chủ: số chưa đọc, (PH) danh sách con và
  /// lịch tuần (thẻ "Lịch học hôm nay") của SV và của từng con.
  void stubHome({
    int unread = 0,
    List<Map<String, dynamic>> children = const [],
    List<Map<String, dynamic>> today = const [],
  }) {
    http.reply(
      'GET',
      '/notifications/unread-count',
      FakeReply(200, {'count': unread}),
    );
    http.reply('GET', '/parent/children', FakeReply(200, children));
    final week = FakeReply(200, weekJson(items: {'2026-10-21': today}));
    http.reply('GET', '/student/schedule/week', week);
    for (final child in children) {
      http.reply('GET', '/parent/children/${child['id']}/schedule/week', week);
    }
  }
}
