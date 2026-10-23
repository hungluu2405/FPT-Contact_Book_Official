import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:open_filex/open_filex.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/grade_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';
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
    final body = reply.body;
    if (body == null) {
      return ResponseBody.fromString('', reply.status);
    }
    if (body is Uint8List) {
      return ResponseBody.fromBytes(
        body,
        reply.status,
        headers: {
          Headers.contentTypeHeader: ['application/octet-stream'],
        },
      );
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
  String code = 'HS260001',
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

Map<String, dynamic> semesterJson({
  int id = 1,
  String code = 'HK1-2627',
  String name = 'Học kỳ I năm học 2026–2027',
}) => {
  'id': id,
  'code': code,
  'name': name,
  'startDate': '2026-09-07',
  'endDate': '2026-12-27',
  'current': true,
};

Map<String, dynamic> sessionJson({
  String date = '2026-10-21',
  String start = '07:30:00',
  String end = '09:20:00',
  String subjectCode = 'TOAN10',
  String subjectName = 'Toán 10',
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
  'sectionCode': '${subjectCode}_10A1',
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
  String subjectCode = 'TIN11',
  String subjectName = 'Tin học 11',
  String room = 'BE202',
}) => {
  'id': id,
  'sectionCode': '${subjectCode}_10A1',
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

/// Ô chọn học kỳ: mới nhất trước, học kỳ hiện tại có id 1.
List<Map<String, dynamic>> semestersJson() => [
  {
    'id': 38,
    'code': 'HK2-2627',
    'name': 'Học kỳ II năm học 2026–2027',
    'groupId': null,
  },
  {
    'id': 1,
    'code': 'HK1-2627',
    'name': 'Học kỳ I năm học 2026–2027',
    'groupId': null,
  },
  {'id': 37, 'code': 'HE-2026', 'name': 'Học kỳ hè 2026', 'groupId': null},
];

Map<String, dynamic> gradeRowJson({
  String subjectCode = 'TOAN10',
  String subjectName = 'Toán 10',
  List<double?> scores = const [8, 7.5, 9],
  double? average = 8.3,
}) => {
  'sectionCode': '$subjectCode-HK1-2627-01',
  'subjectCode': subjectCode,
  'subjectName': subjectName,
  'cells': [
    for (var i = 0; i < scores.length; i++)
      {
        'componentName': [
          'Kiểm tra thường xuyên',
          'Giữa kỳ',
          'Cuối kỳ',
          'Thực hành',
        ][i % 4],
        'weight': [20, 30, 50, 10][i % 4],
        'maxScore': 10,
        'score': scores[i],
      },
  ],
  'average': average,
};

/// `{semester, data: [...]}` của GET .../grades.
Map<String, dynamic> gradesJson(
  List<Map<String, dynamic>> rows, {
  int semesterId = 1,
}) => {'semester': semesterJson(id: semesterId), 'data': rows};

Map<String, dynamic> summaryJson({
  int present = 8,
  int excused = 1,
  int unexcused = 1,
  int total = 15,
  double? rate = 80,
  double absentRate = 13.3,
  bool warning = false,
}) => {
  'present': present,
  'absentExcused': excused,
  'absentUnexcused': unexcused,
  'totalSessions': total,
  'attendanceRate': rate,
  'absentRate': absentRate,
  'warning': warning,
};

Map<String, dynamic> attendanceRowJson({
  int sectionId = 1,
  String subjectCode = 'TOAN10',
  String subjectName = 'Toán 10',
  Map<String, dynamic>? summary,
}) => {
  'sectionId': sectionId,
  'sectionCode': '$subjectCode-HK1-2627-01',
  'subjectCode': subjectCode,
  'subjectName': subjectName,
  'summary': summary ?? summaryJson(),
};

Map<String, dynamic> attendanceJson(
  List<Map<String, dynamic>> rows, {
  int semesterId = 1,
}) => {
  'semester': semesterJson(id: semesterId),
  'thresholdPercent': 20,
  'sections': rows,
};

Map<String, dynamic> attendanceSessionJson({
  int no = 1,
  String date = '2026-10-05',
  String? status = 'PRESENT',
  String sessionStatus = 'DONE',
  String? note,
}) => {
  'sessionNo': no,
  'date': date,
  'startTime': '07:30:00',
  'endTime': '09:00:00',
  'roomCode': 'BE-301',
  'sessionStatus': sessionStatus,
  'status': status,
  'note': note,
};

Map<String, dynamic> attendanceHistoryJson(
  List<Map<String, dynamic>> sessions, {
  int page = 0,
  int totalPages = 1,
  Map<String, dynamic>? row,
}) => {
  'summary': row ?? attendanceRowJson(),
  'thresholdPercent': 20,
  'sessions': {
    'items': sessions,
    'page': page,
    'size': 20,
    'totalElements': sessions.length,
    'totalPages': totalPages,
  },
};

/// Một trang `{items, page, size, totalElements, totalPages}` không kèm học kỳ.
Map<String, dynamic> pageJson(
  List<Map<String, dynamic>> items, {
  int page = 0,
  int totalPages = 1,
  int? totalElements,
}) => {
  'items': items,
  'page': page,
  'size': 20,
  'totalElements': totalElements ?? items.length,
  'totalPages': totalPages,
};

Map<String, dynamic> clubJson({
  int id = 1,
  String code = 'F-CODE',
  String name = 'Câu lạc bộ Lập trình F-Code',
  String status = 'ACTIVE',
  int members = 2,
}) => {
  'id': id,
  'code': code,
  'name': name,
  'description': 'Nơi học sinh yêu thích lập trình giao lưu, luyện thuật toán.',
  'foundedDate': '2020-09-15',
  'advisorName': 'ThS. Hoàng Văn Code',
  'status': status,
  'memberCount': members,
  'presidentName': 'Nguyễn Văn Hùng',
};

Map<String, dynamic> clubDetailJson({
  Map<String, dynamic>? club,
  String? myStatus,
  String? myRole,
}) => {'club': club ?? clubJson(), 'myStatus': myStatus, 'myRole': myRole};

Map<String, dynamic> clubMemberJson({
  String name = 'Nguyễn Văn Hùng',
  String role = 'PRESIDENT',
}) => {'fullName': name, 'role': role, 'joinedAt': '2026-10-15T21:40:44'};

Map<String, dynamic> clubActivityJson({
  int id = 1,
  String title = 'Luyện thuật toán',
  String start = '2026-10-24T14:00:00',
  String end = '2026-10-24T16:00:00',
  int? max = 30,
  int taken = 12,
  String? myStatus,
}) => {
  'id': id,
  'title': title,
  'description': 'Giải đề tin học trẻ',
  'location': 'LAB-401',
  'startTime': start,
  'endTime': end,
  'maxParticipants': max,
  'taken': taken,
  'state': 'PLANNED',
  'myStatus': myStatus,
};

Map<String, dynamic> myClubJson({
  int clubId = 1,
  String code = 'F-CODE',
  String name = 'Câu lạc bộ Lập trình F-Code',
  String role = 'MEMBER',
  String status = 'ACTIVE',
}) => {
  'clubId': clubId,
  'code': code,
  'name': name,
  'clubStatus': 'ACTIVE',
  'role': role,
  'status': status,
};

Map<String, dynamic> notificationJson({
  int id = 1,
  String title = 'Lịch nghỉ lễ 20/11',
  String priority = 'NORMAL',
  String createdAt = '2026-10-20T08:30:00',
  bool read = false,
}) => {
  'id': id,
  'title': title,
  'senderName': 'Phòng Công tác học sinh',
  'priority': priority,
  'createdAt': createdAt,
  'read': read,
};

Map<String, dynamic> attachmentJson({
  int id = 11,
  String fileName = 'lich-nghi-le.pdf',
  int sizeBytes = 245760,
}) => {'id': id, 'fileName': fileName, 'sizeBytes': sizeBytes};

Map<String, dynamic> notificationDetailJson({
  int id = 1,
  String title = 'Lịch nghỉ lễ 20/11',
  String priority = 'NORMAL',
  List<Map<String, dynamic>> files = const [],
}) => {
  'id': id,
  'title': title,
  'content':
      'Nhà trường thông báo học sinh nghỉ học ngày 20/11 để tham dự lễ '
      'kỷ niệm Ngày Nhà giáo Việt Nam.',
  'priority': priority,
  'targetLabel': 'Toàn trường',
  'senderName': 'Phòng Công tác học sinh',
  'createdAt': '2026-10-20T08:30:00',
  'files': files,
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
  late final GradeController gradeController = GradeController(
    StudentService(apiClient),
  );
  late final AttendanceController attendanceController = AttendanceController(
    StudentService(apiClient),
  );
  late final ClubController clubController = ClubController(
    ClubService(apiClient),
  );

  /// Đường dẫn các tệp đã "mở" bằng ứng dụng ngoài, và kết quả trả về.
  final List<String> openedFiles = [];
  OpenResult openResult = OpenResult();

  /// Thư mục tạm thật thay cho thư mục cache của Android, chỉ được tạo khi
  /// tải tệp; test nào tải tệp thì tự xóa bằng [deleteAttachmentDir].
  late final Directory attachmentDir = Directory(
    '${Directory.systemTemp.path}/fpt_attachments_'
    '${DateTime.now().microsecondsSinceEpoch}_${identityHashCode(this)}',
  );
  late final AttachmentService attachmentService = AttachmentService(
    apiClient,
    directory: () async => attachmentDir,
    opener: (path) async {
      openedFiles.add(path);
      return openResult;
    },
  );
  late final NotificationController notificationController =
      NotificationController(NotificationService(apiClient), attachmentService);

  void deleteAttachmentDir() {
    if (attachmentDir.existsSync()) {
      attachmentDir.deleteSync(recursive: true);
    }
  }

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
