import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/teacher_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/department_service.dart';

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
}
