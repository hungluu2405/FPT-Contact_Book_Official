import 'dart:async';

import 'package:flutter_test/flutter_test.dart';

import '../support/fakes.dart';

void main() {
  group('departments', () {
    test('load picks the first department and keeps the choice', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/head/departments',
        FakeReply(200, [
          departmentJson(),
          departmentJson(
            id: 38,
            code: 'NV',
            name: 'Tổ Ngữ văn',
            teacherCount: 6,
          ),
        ]),
      );
      final controller = app.departmentController;

      await controller.loadDepartments();
      expect(controller.selectedDepartment?.code, 'TT');

      controller.selectDepartment(controller.departments.last);
      await controller.loadDepartments();

      expect(controller.departments, hasLength(2));
      expect(controller.selectedDepartment?.code, 'NV');
      expect(controller.isLoadingDepartments, isFalse);
    });

    test('an error is kept and no department is chosen', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/head/departments');

      await app.departmentController.loadDepartments();

      expect(app.departmentController.departmentsError?.isNetworkError, isTrue);
      expect(app.departmentController.selectedDepartment, isNull);
    });

    test('reset forgets the previous session', () async {
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
      await app.departmentController.loadDepartments();
      await app.departmentController.openTeachers();

      app.departmentController.reset();

      expect(app.departmentController.departments, isEmpty);
      expect(app.departmentController.selectedDepartment, isNull);
      expect(app.departmentController.teachers, isEmpty);
    });
  });

  group('teachers', () {
    test('the list is of the chosen department and can be searched', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/head/departments',
        FakeReply(200, [departmentJson()]),
      );
      app.http.reply(
        'GET',
        '/head/teachers',
        FakeReply(200, pageJson([teacherJson()], totalPages: 2)),
      );
      final controller = app.departmentController;
      await controller.loadDepartments();

      await controller.openTeachers();
      await controller.search('  gv002 ');

      final queries = app.http.requests
          .where((r) => r.path == '/head/teachers')
          .map((r) => r.queryParameters)
          .toList();
      expect(queries, [
        {'departmentId': 1, 'page': 0},
        {'departmentId': 1, 'q': 'gv002', 'page': 0},
      ]);
      expect(controller.query, 'gv002');
      expect(controller.teachers.single.teacherCode, 'GV002');
      expect(controller.hasMore, isTrue);
    });

    test('load more appends pages until the last', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.on('GET', '/head/teachers', (request) async {
        final page = request.queryParameters['page'] as int;
        return FakeReply(
          200,
          pageJson([teacherJson(userId: page + 1)], page: page, totalPages: 2),
        );
      });
      final controller = app.departmentController;
      await controller.openTeachers();

      await controller.loadMore();
      await controller.loadMore();

      expect(controller.teachers.map((t) => t.userId), [1, 2]);
      expect(controller.hasMore, isFalse);
      expect(app.http.count('GET', '/head/teachers'), 2);
    });

    test('a late reply of an older search is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/head/teachers', (request) {
        if (request.queryParameters['q'] == 'an') {
          return slow.future;
        }
        return Future.value(
          FakeReply(200, pageJson([teacherJson(userId: 16, code: 'GV003')])),
        );
      });
      final controller = app.departmentController;

      final old = controller.search('an');
      await controller.search('gv003');
      slow.complete(FakeReply(200, pageJson([teacherJson()])));
      await old;

      expect(controller.teachers.single.teacherCode, 'GV003');
      expect(controller.isLoading, isFalse);
    });

    test('an error can be retried', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.offline('GET', '/head/teachers');
      final controller = app.departmentController;

      await controller.openTeachers();
      expect(controller.error?.isNetworkError, isTrue);

      app.http.reply(
        'GET',
        '/head/teachers',
        FakeReply(200, pageJson([teacherJson()])),
      );
      await controller.loadMore();

      expect(controller.error, isNull);
      expect(controller.teachers, hasLength(1));
    });
  });

  group('teacher detail', () {
    test(
      'a teacher of the department opens with work and contact fields',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply(
          'GET',
          '/head/teachers/3',
          FakeReply(
            200,
            teacherDetailJson(roles: ['HEAD_OF_DEPARTMENT', 'TEACHER']),
          ),
        );

        await app.departmentController.openTeacher(3);

        final detail = app.departmentController.detail!;
        expect(detail.email, 'teacher@fpt.edu.vn');
        expect(detail.academicTitle, 'Thạc sĩ');
        expect(detail.isHead, isTrue);
        expect(app.departmentController.isLoadingDetail, isFalse);
      },
    );

    test(
      'a teacher outside the department shows the backend message',
      () async {
        final app = TestApp(accessToken: 'a', refreshToken: 'r');
        app.http.reply(
          'GET',
          '/head/teachers/99',
          FakeReply.error(
            403,
            'FORBIDDEN',
            'Bạn không có quyền xem nội dung này',
          ),
        );

        await app.departmentController.openTeacher(99);

        expect(app.departmentController.detail, isNull);
        expect(
          app.departmentController.detailError?.message,
          'Bạn không có quyền xem nội dung này',
        );
      },
    );

    test('a late detail of another teacher is dropped', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      final slow = Completer<FakeReply>();
      app.http.on('GET', '/head/teachers/3', (_) => slow.future);
      app.http.reply(
        'GET',
        '/head/teachers/16',
        FakeReply(200, teacherDetailJson(userId: 16, code: 'GV003')),
      );

      final old = app.departmentController.openTeacher(3);
      await app.departmentController.openTeacher(16);
      slow.complete(FakeReply(200, teacherDetailJson()));
      await old;

      expect(app.departmentController.detail?.teacherCode, 'GV003');
    });
  });
}
