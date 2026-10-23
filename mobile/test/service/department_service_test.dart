import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/sent_notification_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/notification_service.dart';

import '../support/fakes.dart';

void main() {
  test('head is recognised from the roles', () {
    final head = UserModel.fromJson(
      userJson(roles: ['TEACHER', 'HEAD_OF_DEPARTMENT']),
    );
    final teacher = UserModel.fromJson(userJson(roles: ['TEACHER']));

    expect(head.isHead, isTrue);
    expect(head.isTeacher, isTrue);
    expect(teacher.isHead, isFalse);
  });

  test('teacher models read the API fields and keep safe defaults', () {
    final item = TeacherModel.fromJson(teacherJson(head: true, active: false));
    final detail = TeacherDetailModel.fromJson(
      teacherDetailJson(academicTitle: null, email: null, lastLoginAt: null),
    );
    final bare = TeacherDetailModel.fromJson({'userId': 9});

    expect(item.head, isTrue);
    expect(item.active, isFalse);
    expect(detail.academicTitle, isNull);
    expect(detail.email, isNull);
    expect(detail.lastLoginAt, isNull);
    expect(detail.isHead, isFalse);
    expect(detail.toJson()['roles'], ['TEACHER']);
    expect(bare.roles, isEmpty);
    expect(bare.active, isTrue);
  });

  test('departments, teachers and a teacher use their paths', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/head/departments',
      FakeReply(200, [departmentJson()]),
    );
    app.http.reply(
      'GET',
      '/head/teachers',
      FakeReply(200, pageJson([teacherJson()])),
    );
    app.http.reply(
      'GET',
      '/head/teachers/3',
      FakeReply(200, teacherDetailJson()),
    );
    final service = DepartmentService(app.apiClient);

    final departments = await service.fetchDepartments();
    await service.fetchTeachers();
    await service.fetchTeachers(departmentId: 1, query: ' an ', page: 2);
    await service.fetchTeachers(query: '   ');
    final teacher = await service.fetchTeacher(3);

    expect(departments.single.teacherCount, 4);
    expect(
      app.http.requests
          .where((r) => r.path == '/head/teachers')
          .map((r) => r.queryParameters)
          .toList(),
      [
        {'page': 0},
        {'departmentId': 1, 'q': 'an', 'page': 2},
        {'page': 0},
      ],
    );
    expect(teacher.phone, '0900000003');
  });

  test('sent notification models read the API fields', () {
    final item = SentNotificationModel.fromJson(sentJson(targetType: 'USER'));
    final detail = SentNotificationDetailModel.fromJson(
      sentDetailJson(files: [attachmentJson()], totalPages: 2),
    );
    final bare = SentNotificationDetailModel.fromJson({'id': 1});

    expect(item.targetType, NotificationTargetType.user);
    expect(item.readCount, 1);
    expect(detail.recipients.items.first.readAt, '2026-10-23T09:30:00');
    expect(detail.recipients.items.last.read, isFalse);
    expect(detail.recipients.hasMore, isTrue);
    expect(detail.files.single.fileName, 'lich-nghi-le.pdf');
    expect(bare.recipients.items, isEmpty);
    expect(bare.recipientCount, 0);
  });

  test('sent list and detail use their paths and the read filter', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/notifications/sent',
      FakeReply(200, pageJson([sentJson()])),
    );
    app.http.reply(
      'GET',
      '/notifications/sent/70',
      FakeReply(200, sentDetailJson()),
    );
    final service = NotificationService(app.apiClient);

    await service.fetchSent(page: 1);
    await service.fetchSentDetail(70);
    await service.fetchSentDetail(70, read: false, page: 2);

    expect(app.http.requests.map((r) => r.queryParameters).toList(), [
      {'page': 1},
      {'page': 0},
      {'read': false, 'page': 2},
    ]);
  });

  test('a notification is sent as multipart to the department or a teacher', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'POST',
      '/head/notifications',
      FakeReply(201, sentDetailJson()),
    );
    final service = DepartmentService(app.apiClient);
    final form = DepartmentNotificationFormModel(
      title: '  Họp tổ  ',
      content: ' Thứ Năm ',
      priority: 'URGENT',
    );

    await service.sendNotification(
      form,
      departmentId: 1,
      files: [pickedFile('bien-ban.docx')],
    );
    await service.sendNotification(form, departmentId: 1, teacherUserId: 3);

    final first = app.http.requests.first;
    final firstData = first.data as FormData;
    expect(first.headers['content-type'], startsWith('multipart/form-data'));
    expect(Map.fromEntries(firstData.fields), {
      'title': 'Họp tổ',
      'content': 'Thứ Năm',
      'priority': 'URGENT',
      'departmentId': '1',
    });
    expect(
      firstData.files.single.value.contentType.toString(),
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    );
    final second = Map.fromEntries(
      (app.http.requests.last.data as FormData).fields,
    );
    expect(second['teacherUserId'], '3');
    expect(second.containsKey('departmentId'), isFalse);
  });
}
