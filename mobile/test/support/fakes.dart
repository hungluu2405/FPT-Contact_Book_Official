import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:open_filex/open_filex.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/attendance_sheet_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/auth_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/club_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/department_notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/exam_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/grade_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/home_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/notification_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/password_reset_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/profile_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/request_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/schedule_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/controller/teacher_section_controller.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/picked_file_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/api_client.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/attachment_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/auth_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/club_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/profile_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/request_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/teacher_service.dart';
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
  int semesterId = 1,
}) => {
  'semester': semesterJson(id: semesterId),
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

Map<String, dynamic> requestJson({
  int id = 1,
  String type = 'LEAVE',
  String title = 'Đơn xin nghỉ học từ 22/10/2026 đến 23/10/2026',
  String status = 'PENDING',
  String? fromDate = '2026-10-22',
  String? toDate = '2026-10-23',
  String requesterName = 'Nguyễn Văn An',
}) => {
  'id': id,
  'type': type,
  'title': title,
  'studentCode': 'HS260001',
  'studentName': 'Nguyễn Văn An',
  'requesterName': requesterName,
  'sectionCode': null,
  'fromDate': fromDate,
  'toDate': toDate,
  'status': status,
  'createdAt': '2026-10-21T08:15:00',
};

Map<String, dynamic> reviewJson({
  String decision = 'APPROVED',
  String? comment = 'Đã nhận giấy khám bệnh',
}) => {
  'reviewerName': 'Giang NT',
  'decision': decision,
  'comment': comment,
  'reviewedAt': '2026-10-21T15:30:00',
};

Map<String, dynamic> requestDetailJson({
  int id = 1,
  String type = 'LEAVE',
  String status = 'PENDING',
  bool canCancel = true,
  bool sentByParent = false,
  List<Map<String, dynamic>> files = const [],
  List<Map<String, dynamic>> reviews = const [],
}) => {
  'id': id,
  'type': type,
  'title': 'Đơn xin nghỉ học từ 22/10/2026 đến 23/10/2026',
  'reason': 'Em bị sốt, có giấy khám bệnh kèm theo.',
  'studentCode': 'HS260001',
  'studentName': 'Nguyễn Văn An',
  'requesterName': sentByParent ? 'Nguyễn Văn Bình' : 'Nguyễn Văn An',
  'sentByParent': sentByParent,
  'sectionLabel': null,
  'fromDate': type == 'LEAVE' ? '2026-10-22' : null,
  'toDate': type == 'LEAVE' ? '2026-10-23' : null,
  'status': status,
  'createdAt': '2026-10-21T08:15:00',
  'updatedAt': '2026-10-21T08:15:00',
  'reviewerName': 'Giang NT',
  'files': files,
  'reviews': reviews,
  'canCancel': canCancel,
  'canReview': false,
};

Map<String, dynamic> leaveOptionsJson() => {
  'studentId': 1,
  'studentCode': 'HS260001',
  'studentName': 'Nguyễn Văn An',
  'sections': [
    {
      'id': 5,
      'code': 'TOAN10_10A1',
      'name': 'TOAN10 - Toán 10 (HK1-2627)',
      'groupId': null,
    },
    {
      'id': 6,
      'code': 'TIN11_10A1',
      'name': 'TIN11 - Tin học 11 (HK1-2627)',
      'groupId': null,
    },
  ],
};

Map<String, dynamic> departmentJson({
  int id = 1,
  String code = 'TT',
  String name = 'Tổ Toán – Tin học',
  int teacherCount = 4,
}) => {'id': id, 'code': code, 'name': name, 'teacherCount': teacherCount};

Map<String, dynamic> teacherJson({
  int userId = 3,
  String code = 'GV002',
  String fullName = 'Nguyễn Thị Giáo',
  String phone = '0900000003',
  bool active = true,
  bool head = false,
}) => {
  'userId': userId,
  'teacherCode': code,
  'fullName': fullName,
  'phone': phone,
  'active': active,
  'head': head,
};

Map<String, dynamic> teacherDetailJson({
  int userId = 3,
  String code = 'GV002',
  String fullName = 'Nguyễn Thị Giáo',
  String? academicTitle = 'Thạc sĩ',
  String? email = 'teacher@fpt.edu.vn',
  bool active = true,
  String? lastLoginAt = '2026-10-22T07:05:00',
  List<String> roles = const ['TEACHER'],
}) => {
  'userId': userId,
  'teacherCode': code,
  'fullName': fullName,
  'academicTitle': academicTitle,
  'departmentName': 'Tổ Toán – Tin học',
  'phone': '0900000003',
  'email': email,
  'active': active,
  'lastLoginAt': lastLoginAt,
  'roles': roles,
};

Map<String, dynamic> sentJson({
  int id = 70,
  String title = 'Họp tổ chuyên môn tháng 11',
  String priority = 'IMPORTANT',
  String targetType = 'DEPARTMENT',
  int recipients = 3,
  int read = 1,
}) => {
  'id': id,
  'title': title,
  'priority': priority,
  'targetType': targetType,
  'createdAt': '2026-10-23T09:00:00',
  'recipientCount': recipients,
  'readCount': read,
};

Map<String, dynamic> recipientJson({
  String name = 'Nguyễn Thị Giáo',
  bool read = true,
}) => {
  'fullName': name,
  'read': read,
  'readAt': read ? '2026-10-23T09:30:00' : null,
};

Map<String, dynamic> sentDetailJson({
  int id = 70,
  String targetType = 'DEPARTMENT',
  String targetLabel = 'TT - Tổ Toán – Tin học',
  int recipients = 3,
  int read = 1,
  List<Map<String, dynamic>>? rows,
  int page = 0,
  int totalPages = 1,
  List<Map<String, dynamic>> files = const [],
}) => {
  'id': id,
  'title': 'Họp tổ chuyên môn tháng 11',
  'content': 'Kính gửi các thầy cô: họp tổ lúc 14:00 thứ Năm tại phòng 204.',
  'priority': 'IMPORTANT',
  'targetType': targetType,
  'targetLabel': targetLabel,
  'createdAt': '2026-10-23T09:00:00',
  'files': files,
  'recipientCount': recipients,
  'readCount': read,
  'recipients': pageJson(
    rows ??
        [
          recipientJson(),
          recipientJson(name: 'Hoàng Văn Code', read: false),
          recipientJson(name: 'Đỗ Thị Thuật', read: false),
        ],
    page: page,
    totalPages: totalPages,
  ),
};

Map<String, dynamic> teacherSectionJson({
  int id = 1,
  String code = 'TOAN10-HK1-2627-01',
  String subjectCode = 'TOAN10',
  String subjectName = 'Toán 10',
  String status = 'OPEN',
}) => {
  'id': id,
  'code': code,
  'subjectCode': subjectCode,
  'subjectName': subjectName,
  'semesterCode': 'HK1-2627',
  'status': status,
};

Map<String, dynamic> sectionSessionJson({
  int id = 1,
  int no = 1,
  String date = '2026-10-21',
  String status = 'SCHEDULED',
  bool hasAttendance = false,
}) => {
  'id': id,
  'sessionNo': no,
  'date': date,
  'startTime': '07:30:00',
  'endTime': '09:00:00',
  'roomCode': 'DE205',
  'status': status,
  'note': null,
  'hasAttendance': hasAttendance,
};

Map<String, dynamic> sectionStudentJson({
  int studentId = 1,
  String code = 'HS260001',
  String name = 'Lê Minh Sinh',
  Map<String, dynamic>? summary,
}) => {
  'studentId': studentId,
  'studentCode': code,
  'fullName': name,
  'summary': summary ?? summaryJson(),
};

Map<String, dynamic> sectionAttendanceJson({
  List<Map<String, dynamic>>? sessions,
  List<Map<String, dynamic>>? students,
  int sessionPages = 1,
  int studentPages = 1,
  int sessionPage = 0,
  int studentPage = 0,
}) => {
  'section': teacherSectionJson(),
  'sessions': pageJson(
    sessions ??
        [
          sectionSessionJson(no: 1, date: '2026-10-19', hasAttendance: true),
          sectionSessionJson(id: 2, no: 2),
        ],
    page: sessionPage,
    totalPages: sessionPages,
  ),
  'students': pageJson(
    students ?? [sectionStudentJson()],
    page: studentPage,
    totalPages: studentPages,
  ),
  'totalSessions': 15,
  'thresholdPercent': 20,
};

Map<String, dynamic> sheetRowJson({
  int studentId = 1,
  String code = 'HS260001',
  String name = 'Lê Minh Sinh',
  String? status,
  String? note,
  bool approvedLeave = false,
}) => {
  'studentId': studentId,
  'studentCode': code,
  'fullName': name,
  'status': status,
  'note': note,
  'approvedLeave': approvedLeave,
};

/// Bảng điểm danh buổi 2 của lớp 1: ba học sinh, học sinh 3 có đơn nghỉ đã
/// duyệt; [marked] là buổi đã điểm danh (khi đó mọi hàng có trạng thái).
Map<String, dynamic> sheetJson({
  bool marked = false,
  bool locked = false,
  List<Map<String, dynamic>>? rows,
}) => {
  'sectionId': 1,
  'sectionCode': 'TOAN10-HK1-2627-01',
  'subjectCode': 'TOAN10',
  'subjectName': 'Toán 10',
  'sessionId': 2,
  'sessionNo': 2,
  'date': '2026-10-21',
  'startTime': '07:30:00',
  'endTime': '09:00:00',
  'roomCode': 'DE205',
  'marked': marked,
  'editableUntil': '2026-10-22T09:00:00',
  'locked': locked,
  'reasonRequired': marked,
  'rows':
      rows ??
      [
        sheetRowJson(status: marked ? 'PRESENT' : null),
        sheetRowJson(
          studentId: 2,
          code: 'HS260002',
          name: 'Trần Thị Bình',
          status: marked ? 'ABSENT_UNEXCUSED' : null,
          note: marked ? 'Không báo trước' : null,
        ),
        sheetRowJson(
          studentId: 3,
          code: 'HS260003',
          name: 'Phạm Văn Cường',
          status: marked ? 'ABSENT_EXCUSED' : null,
          approvedLeave: true,
        ),
      ],
};

/// Tệp minh chứng giả có [size] byte.
PickedFileModel pickedFile(String name, {int size = 3}) =>
    PickedFileModel.fromBytes(name, Uint8List(size));

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

  /// Kết quả của trình chọn tệp và máy ảnh giả; [pickError] khác null thì
  /// cả hai ném lỗi đó.
  List<PickedFileModel> pickedFiles = [];
  PickedFileModel? photo;
  Object? pickError;

  late final AttachmentService attachmentService = AttachmentService(
    apiClient,
    directory: () async => attachmentDir,
    opener: (path) async {
      openedFiles.add(path);
      return openResult;
    },
    filesPicker: () async {
      if (pickError != null) {
        throw pickError!;
      }
      return pickedFiles;
    },
    photoTaker: () async {
      if (pickError != null) {
        throw pickError!;
      }
      return photo;
    },
  );
  late final NotificationController notificationController =
      NotificationController(NotificationService(apiClient), attachmentService);
  late final DepartmentController departmentController = DepartmentController(
    DepartmentService(apiClient),
  );
  late final DepartmentNotificationController departmentNotificationController =
      DepartmentNotificationController(
        DepartmentService(apiClient),
        NotificationService(apiClient),
        attachmentService,
      );
  late final TeacherSectionController teacherSectionController =
      TeacherSectionController(
        TeacherService(apiClient),
        StudentService(apiClient),
      );
  late final AttendanceSheetController attendanceSheetController =
      AttendanceSheetController(TeacherService(apiClient));
  late final RequestController requestController = RequestController(
    RequestService(apiClient),
    attachmentService,
    clock: () => kTestNow,
  );

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
  /// lịch tuần (thẻ "Lịch học hôm nay") của SV, của từng con và lịch dạy
  /// của GV.
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
    http.reply('GET', '/teacher/schedule/week', week);
    for (final child in children) {
      http.reply('GET', '/parent/children/${child['id']}/schedule/week', week);
    }
  }
}
