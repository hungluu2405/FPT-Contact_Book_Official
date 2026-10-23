import 'package:flutter_test/flutter_test.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/model/user_model.dart';
import 'package:fpt_contact_book_mobile/vn/edu/fpt/service/student_service.dart';

import '../support/fakes.dart';

/// Phạm vi giáo viên (giai đoạn 10d-1): lịch dạy, lịch thi và thẻ "Lịch dạy
/// hôm nay" dùng lại controller của HS nhưng gọi `/teacher/...`.
void main() {
  test('the service builds teacher paths', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/teacher/schedule/week', FakeReply(200, weekJson()));
    app.http.reply(
      'GET',
      '/teacher/schedule/sessions',
      FakeReply(200, pagedJson([sessionJson()])),
    );
    app.http.reply(
      'GET',
      '/teacher/exams',
      FakeReply(200, pagedJson([examJson()])),
    );
    final service = StudentService(app.apiClient);

    await service.fetchWeek(null, teacher: true, date: '2026-10-26');
    await service.fetchSessions(null, teacher: true, page: 1);
    await service.fetchExams(null, teacher: true);

    expect(app.http.requests.map((r) => '${r.path} ${r.queryParameters}'), [
      '/teacher/schedule/week {date: 2026-10-26}',
      '/teacher/schedule/sessions {page: 1}',
      '/teacher/exams {page: 0}',
    ]);
    expect(StudentService.scheduleRoot(7), '/parent/children/7');
    expect(StudentService.scheduleRoot(7, teacher: true), '/teacher');
  });

  test(
    'the schedule controller opens the teaching week and sessions',
    () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.http.reply(
        'GET',
        '/teacher/schedule/week',
        FakeReply(
          200,
          weekJson(
            items: {
              '2026-10-21': [sessionJson()],
            },
          ),
        ),
      );
      app.http.reply(
        'GET',
        '/teacher/schedule/sessions',
        FakeReply(200, pagedJson([sessionJson()])),
      );
      final controller = app.scheduleController;

      await controller.open(null, teacher: true);
      await controller.reloadSessions();

      expect(controller.isTeacher, isTrue);
      expect(controller.selectedDay?.items.single.subjectCode, 'TOAN10');
      expect(app.http.count('GET', '/teacher/schedule/sessions'), 1);
      expect(app.http.count('GET', '/student/schedule/week'), 0);
    },
  );

  test('opening a student schedule after a teacher one goes back', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply('GET', '/teacher/schedule/week', FakeReply(200, weekJson()));
    app.http.reply('GET', '/student/schedule/week', FakeReply(200, weekJson()));
    final controller = app.scheduleController;

    await controller.open(null, teacher: true);
    await controller.open(null);

    expect(controller.isTeacher, isFalse);
    expect(app.http.requests.last.path, '/student/schedule/week');
  });

  test('the exam controller lists exams of the classes taught', () async {
    final app = TestApp(accessToken: 'a', refreshToken: 'r');
    app.http.reply(
      'GET',
      '/teacher/exams',
      FakeReply(200, pagedJson([examJson(), examJson(id: 2)])),
    );

    await app.examController.open(null, teacher: true);

    expect(app.examController.isTeacher, isTrue);
    expect(app.examController.exams, hasLength(2));
    expect(app.http.count('GET', '/teacher/exams'), 1);
  });

  group('home', () {
    UserModel user(List<String> roles) =>
        UserModel.fromJson(userJson(roles: roles));

    test('a teacher sees today of the teaching week', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.stubHome(unread: 2);
      app.http.reply(
        'GET',
        '/teacher/schedule/week',
        FakeReply(
          200,
          weekJson(
            items: {
              '2026-10-21': [sessionJson(), sessionJson(start: '13:00:00')],
            },
          ),
        ),
      );

      await app.homeController.start(user([RoleCodes.teacher]));

      expect(app.homeController.isTeacherView, isTrue);
      expect(app.homeController.todayItems, hasLength(2));
      expect(app.homeController.unreadCount, 2);
      expect(app.http.count('GET', '/student/schedule/week'), 0);
      expect(app.http.count('GET', '/parent/children'), 0);
    });

    test('a head of department is a teacher view too', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.stubHome();
      app.http.reply(
        'GET',
        '/teacher/schedule/week',
        FakeReply(200, weekJson()),
      );

      await app.homeController.start(
        user([RoleCodes.teacher, RoleCodes.headOfDepartment]),
      );

      expect(app.homeController.isTeacherView, isTrue);
      expect(app.http.count('GET', '/teacher/schedule/week'), 1);
    });

    test('a teacher who is also a parent keeps the parent view', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.stubHome(children: [childJson()]);

      await app.homeController.start(
        user([RoleCodes.teacher, RoleCodes.parent]),
      );

      expect(app.homeController.isTeacherView, isFalse);
      expect(app.http.count('GET', '/parent/children/1/schedule/week'), 1);
      expect(app.http.count('GET', '/teacher/schedule/week'), 0);
    });

    test('no semester yet is an empty day, not an error', () async {
      final app = TestApp(accessToken: 'a', refreshToken: 'r');
      app.stubHome();
      app.http.reply(
        'GET',
        '/teacher/schedule/week',
        FakeReply.error(404, 'NOT_FOUND', 'Chưa có học kỳ'),
      );

      await app.homeController.start(user([RoleCodes.teacher]));

      expect(app.homeController.todayItems, isEmpty);
      expect(app.homeController.todayError, isNull);
    });
  });
}
